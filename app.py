#!/usr/bin/env python3
import asyncio
from datetime import datetime, timedelta
import json
import os
import sqlite3
from pathlib import Path
from typing import Iterator

import cv2
import numpy as np
from aiortc import RTCPeerConnection, RTCSessionDescription, VideoStreamTrack
from av import VideoFrame
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import HTMLResponse, JSONResponse, PlainTextResponse, Response, StreamingResponse


BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
STATE_PATH = Path(os.environ.get("CHINCHE_API_STATE_JSON", DATA_DIR / "latest_state.json"))
SNAPSHOT_PATH = Path(os.environ.get("CHINCHE_API_SNAPSHOT_JPG", DATA_DIR / "latest_snapshot.jpg"))
EVENTS_PATH = Path(os.environ.get("CHINCHE_API_EVENTS_JSONL", DATA_DIR / "events.jsonl"))
SQLITE_PATH = Path(os.environ.get("CHINCHE_API_SQLITE_PATH", DATA_DIR / "events.db"))
FRAME_BIN_PATH = Path(os.environ.get("CHINCHE_API_FRAME_BIN", "/dev/shm/chinchemalva_latest_frame.bin"))
FRAME_META_PATH = Path(os.environ.get("CHINCHE_API_FRAME_META_JSON", "/dev/shm/chinchemalva_latest_frame_meta.json"))
CAMERA_ID = os.environ.get("CHINCHE_CAMERA_ID", "imx500-rpi-01")
MODEL_NAME = os.environ.get("CHINCHE_MODEL_NAME", "chinche_malva_v2")
pcs: set[RTCPeerConnection] = set()

app = FastAPI(
    title="V2 ChincheMalva Camera API",
    version="0.1.0",
    description="API de stream y metadatos para la camara IMX500 de chinche malva.",
)


def load_latest_state() -> dict:
    if not STATE_PATH.exists():
        return {
            "camera_id": CAMERA_ID,
            "timestamp": None,
            "model": MODEL_NAME,
            "total_detections": 0,
            "temperature_c": None,
            "humidity_pct": None,
            "image_width": None,
            "image_height": None,
            "boxes": [],
            "status": "waiting_for_frames",
        }
    try:
        return json.loads(STATE_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=500, detail=f"Estado JSON corrupto: {exc}") from exc


def load_snapshot_image() -> np.ndarray | None:
    if not SNAPSHOT_PATH.exists():
        return None
    data = np.frombuffer(SNAPSHOT_PATH.read_bytes(), dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_COLOR)
    return image


def load_shared_frame() -> np.ndarray | None:
    if not FRAME_BIN_PATH.exists() or not FRAME_META_PATH.exists():
        return None
    try:
        meta = json.loads(FRAME_META_PATH.read_text(encoding="utf-8"))
        width = int(meta["width"])
        height = int(meta["height"])
        channels = int(meta.get("channels", 3))
        dtype = np.dtype(meta.get("dtype", "uint8"))
        raw = FRAME_BIN_PATH.read_bytes()
        frame = np.frombuffer(raw, dtype=dtype)
        expected = width * height * channels
        if frame.size != expected:
            return None
        return frame.reshape((height, width, channels)).copy()
    except Exception:
        return None


def encode_live_jpeg(quality: int = 80) -> bytes | None:
    image = load_shared_frame()
    if image is None:
        image = load_snapshot_image()
    if image is None:
        return None
    ok, encoded = cv2.imencode(".jpg", image, [int(cv2.IMWRITE_JPEG_QUALITY), quality])
    if not ok:
        return None
    return encoded.tobytes()


class SnapshotVideoTrack(VideoStreamTrack):
    def __init__(self):
        super().__init__()
        self._fallback = np.zeros((480, 640, 3), dtype=np.uint8)

    async def recv(self):
        await asyncio.sleep(0.08)
        pts, time_base = await self.next_timestamp()
        image = load_shared_frame()
        if image is None:
            image = load_snapshot_image()
        if image is None:
            image = self._fallback
        frame = VideoFrame.from_ndarray(image, format="bgr24")
        frame.pts = pts
        frame.time_base = time_base
        return frame


def iter_mjpeg_frames() -> Iterator[bytes]:
    boundary = b"--frame\r\n"
    while True:
        jpg = encode_live_jpeg(quality=78)
        if jpg is not None:
            yield boundary
            yield b"Content-Type: image/jpeg\r\n"
            yield f"Content-Length: {len(jpg)}\r\n\r\n".encode("ascii")
            yield jpg
            yield b"\r\n"
        else:
            placeholder = b"Esperando snapshot de la camara IMX500"
            yield boundary
            yield b"Content-Type: text/plain\r\n"
            yield f"Content-Length: {len(placeholder)}\r\n\r\n".encode("ascii")
            yield placeholder
            yield b"\r\n"
        import time
        time.sleep(0.25)


def row_to_event(row: sqlite3.Row) -> dict:
    return {
        "camera_id": row["camera_id"],
        "timestamp": row["timestamp"],
        "model": row["model"],
        "total_detections": row["total_detections"],
        "temperature_c": row["temperature_c"],
        "humidity_pct": row["humidity_pct"],
        "image_width": row["image_width"],
        "image_height": row["image_height"],
        "boxes": json.loads(row["boxes_json"]),
    }


def read_events(limit: int, date_from: str | None = None, date_to: str | None = None) -> list[dict]:
    if SQLITE_PATH.exists():
        with sqlite3.connect(SQLITE_PATH) as conn:
            conn.row_factory = sqlite3.Row
            sql = """
                SELECT camera_id, timestamp, model, total_detections, temperature_c,
                       humidity_pct, image_width, image_height, boxes_json
                FROM detection_events
            """
            clauses = []
            params = []
            if date_from:
                clauses.append("timestamp >= ?")
                params.append(date_from)
            if date_to:
                clauses.append("timestamp <= ?")
                params.append(date_to)
            if clauses:
                sql += " WHERE " + " AND ".join(clauses)
            sql += " ORDER BY id DESC LIMIT ?"
            params.append(limit)
            rows = conn.execute(sql, params).fetchall()
        return [row_to_event(row) for row in rows]
    if not EVENTS_PATH.exists():
        return []
    lines = EVENTS_PATH.read_text(encoding="utf-8").splitlines()
    result = []
    for line in lines[-limit:]:
        if not line.strip():
            continue
        try:
            result.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    if date_from:
        result = [ev for ev in result if ev.get("timestamp") and ev["timestamp"] >= date_from]
    if date_to:
        result = [ev for ev in result if ev.get("timestamp") and ev["timestamp"] <= date_to]
    return result


def build_summary(events: list[dict]) -> dict:
    if not events:
        return {
            "count": 0,
            "max_detections": 0,
            "avg_detections": 0.0,
            "avg_temperature_c": None,
            "avg_humidity_pct": None,
        }

    detections = [float(ev.get("total_detections", 0) or 0) for ev in events]
    temps = [float(ev["temperature_c"]) for ev in events if ev.get("temperature_c") is not None]
    hums = [float(ev["humidity_pct"]) for ev in events if ev.get("humidity_pct") is not None]
    return {
        "count": len(events),
        "max_detections": int(max(detections)) if detections else 0,
        "avg_detections": round(sum(detections) / len(detections), 2) if detections else 0.0,
        "avg_temperature_c": round(sum(temps) / len(temps), 2) if temps else None,
        "avg_humidity_pct": round(sum(hums) / len(hums), 2) if hums else None,
    }


@app.get("/health")
def health():
    state = load_latest_state()
    return {
        "status": "ok",
        "camera_id": CAMERA_ID,
        "model": MODEL_NAME,
        "has_state": STATE_PATH.exists(),
        "has_snapshot": SNAPSHOT_PATH.exists(),
        "has_shared_frame": FRAME_BIN_PATH.exists() and FRAME_META_PATH.exists(),
        "latest_timestamp": state.get("timestamp"),
    }


@app.get("/latest")
def latest():
    return JSONResponse(load_latest_state())


@app.get("/snapshot.jpg")
def snapshot():
    if not SNAPSHOT_PATH.exists():
        raise HTTPException(status_code=404, detail="Todavia no hay snapshot disponible.")
    return Response(
        content=SNAPSHOT_PATH.read_bytes(),
        media_type="image/jpeg",
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"},
    )


@app.get("/live.jpg")
def live_jpg():
    jpg = encode_live_jpeg(quality=78)
    if jpg is None:
        raise HTTPException(status_code=404, detail="Todavia no hay frame vivo disponible.")
    return Response(
        content=jpg,
        media_type="image/jpeg",
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"},
    )


@app.get("/stream.mjpg")
def stream():
    return StreamingResponse(
        iter_mjpeg_frames(),
        media_type="multipart/x-mixed-replace; boundary=frame",
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"},
    )


@app.get("/events")
def events(
    limit: int = Query(default=50, ge=1, le=1000),
    date_from: str | None = Query(default=None),
    date_to: str | None = Query(default=None),
):
    items = read_events(limit, date_from=date_from, date_to=date_to)
    return JSONResponse(
        {
            "camera_id": CAMERA_ID,
            "count": len(items),
            "summary": build_summary(items),
            "events": items,
        }
    )


@app.get("/stats")
def stats(
    limit: int = Query(default=288, ge=1, le=5000),
    date_from: str | None = Query(default=None),
    date_to: str | None = Query(default=None),
):
    items = read_events(limit, date_from=date_from, date_to=date_to)
    return JSONResponse(
        {
            "camera_id": CAMERA_ID,
            "summary": build_summary(items),
            "points": list(reversed(items)),
        }
    )


@app.post("/webrtc/offer")
async def webrtc_offer(offer: dict):
    if "sdp" not in offer or "type" not in offer:
        raise HTTPException(status_code=400, detail="Oferta WebRTC invalida.")

    pc = RTCPeerConnection()
    pcs.add(pc)

    @pc.on("connectionstatechange")
    async def on_connectionstatechange():
        if pc.connectionState in {"failed", "closed", "disconnected"}:
            await pc.close()
            pcs.discard(pc)

    pc.addTrack(SnapshotVideoTrack())
    await pc.setRemoteDescription(RTCSessionDescription(sdp=offer["sdp"], type=offer["type"]))
    answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    return {
        "sdp": pc.localDescription.sdp,
        "type": pc.localDescription.type,
    }


@app.get("/")
def root():
    now = datetime.now()
    default_from = (now - timedelta(hours=6)).strftime("%Y-%m-%dT%H:%M")
    default_to = now.strftime("%Y-%m-%dT%H:%M")
    html = """
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>V2 ChincheMalva</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Hanken+Grotesk:wght@400;500;600;700&display=swap" rel="stylesheet">
  <style>
    :root {
      color-scheme: light;
      --surface: #fcf9f5;
      --surface-dim: #dcdad6;
      --surface-bright: #fcf9f5;
      --surface-lowest: #ffffff;
      --surface-low: #f6f3ef;
      --surface-container: #f0ede9;
      --surface-high: #ebe8e4;
      --surface-highest: #e5e2de;
      --surface-variant: #e5e2de;
      --background: #fcf9f5;
      --text: #1c1c1a;
      --muted: #434843;
      --outline: #737973;
      --outline-soft: #c3c8c1;
      --primary: #061b0e;
      --primary-soft: #1b3022;
      --primary-soft-text: #819986;
      --secondary: #556344;
      --secondary-soft: #d8e8c1;
      --secondary-soft-dim: #bccca7;
      --tertiary: #231500;
      --tertiary-soft: #39290e;
      --tertiary-text: #a88f6c;
      --accent-metal: #dec29c;
      --accent-metal-deep: #a88f6c;
      --good-bg: #edf5dd;
      --good-text: #2f4b22;
      --warn-bg: #f3e7cf;
      --warn-text: #6c5432;
      --alert-bg: #ffdad6;
      --alert-text: #93000a;
      --shadow-soft: 0 16px 38px rgba(6, 27, 14, 0.05);
      --shadow-hover: 0 10px 24px rgba(6, 27, 14, 0.08);
      --radius-sm: 4px;
      --radius-md: 6px;
      --radius-lg: 8px;
      --radius-xl: 12px;
      --space-xs: 4px;
      --space-sm: 12px;
      --space-md: 24px;
      --space-lg: 40px;
      --space-xl: 64px;
    }
    * { box-sizing:border-box; }
    body {
      margin:0;
      font-family:"Hanken Grotesk", "Segoe UI", sans-serif;
      background:
        radial-gradient(circle at top left, rgba(184, 205, 184, 0.32), transparent 24%),
        radial-gradient(circle at 100% 0, rgba(222, 194, 156, 0.24), transparent 20%),
        linear-gradient(180deg, var(--background), var(--surface-low));
      color:var(--text);
    }
    .wrap { max-width:1440px; margin:0 auto; padding:32px 32px 48px 32px; }
    .hero {
      display:flex;
      justify-content:space-between;
      gap:24px;
      align-items:end;
      margin-bottom:24px;
      padding:24px;
      border:1px solid var(--outline-soft);
      border-radius:var(--radius-xl);
      background:
        linear-gradient(135deg, rgba(255,255,255,0.76), rgba(246,243,239,0.94)),
        linear-gradient(135deg, rgba(27,48,34,0.02), rgba(57,41,14,0.04));
      box-shadow:var(--shadow-soft);
      position:relative;
      overflow:hidden;
    }
    .hero::after {
      content:"";
      position:absolute;
      inset:auto -14% -42% auto;
      width:320px;
      height:320px;
      border-radius:50%;
      background:radial-gradient(circle, rgba(216,232,193,0.7), rgba(216,232,193,0));
      pointer-events:none;
    }
    .hero-copy { max-width:760px; }
    .hero-kicker {
      display:inline-flex;
      align-items:center;
      gap:8px;
      padding:7px 12px;
      border-radius:999px;
      background:rgba(216,232,193,0.55);
      color:var(--secondary);
      border:1px solid rgba(85,99,68,0.16);
      font-size:12px;
      font-weight:600;
      letter-spacing:.12em;
      text-transform:uppercase;
    }
    h1 { margin:14px 0 0 0; font-size:48px; font-weight:700; line-height:56px; letter-spacing:-0.02em; color:var(--primary); }
    .sub { color:var(--muted); font-size:16px; margin-top:8px; max-width:720px; line-height:24px; }
    .hero-meta { display:flex; gap:10px; flex-wrap:wrap; margin-top:16px; }
    .hero-pill {
      display:inline-flex;
      align-items:center;
      gap:8px;
      padding:10px 14px;
      border-radius:999px;
      background:var(--surface-lowest);
      border:1px solid var(--outline-soft);
      color:var(--primary-soft);
      font-size:13px;
      font-weight:500;
    }
    .grid { display:grid; grid-template-columns:minmax(0,1.7fr) minmax(320px,1fr); gap:20px; }
    .panel {
      background:linear-gradient(180deg, var(--surface-lowest), var(--surface-low));
      border:1px solid var(--outline-soft);
      border-radius:var(--radius-lg);
      overflow:hidden;
      box-shadow:var(--shadow-soft);
    }
    .panel h2 {
      margin:0;
      padding:18px 20px 12px 20px;
      font-size:14px;
      font-weight:600;
      color:var(--secondary);
      letter-spacing:.05em;
      text-transform:uppercase;
      border-bottom:1px solid rgba(166,141,106,0.18);
    }
    .stream-wrap { padding:20px; }
    .stream-stage { position:relative; width:100%; }
    .stream {
      width:100%;
      display:block;
      background:linear-gradient(180deg, #f8f5f0, #ece8df);
      border-radius:var(--radius-lg);
      min-height:380px;
      object-fit:contain;
      border:1px solid rgba(166,141,106,0.28);
    }
    .stats { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; padding:20px; }
    .card {
      background:linear-gradient(180deg, var(--surface-lowest), rgba(252,249,245,0.94));
      border:1px solid rgba(166,141,106,0.22);
      border-radius:var(--radius-lg);
      padding:15px;
      min-height:102px;
    }
    .card.is-wide { grid-column:1 / -1; min-height:110px; }
    .label { color:var(--muted); font-size:14px; font-weight:600; text-transform:uppercase; letter-spacing:.05em; }
    .value { margin-top:8px; font-size:32px; font-weight:600; line-height:40px; letter-spacing:-0.01em; color:var(--primary); }
    .small { font-size:14px; color:var(--muted); line-height:1.45; }
    .status-chip { display:inline-flex; align-items:center; gap:10px; padding:8px 12px; border-radius:999px; font-size:16px; font-weight:700; }
    .status-chip.is-on { background:var(--good-bg); color:var(--good-text); border:1px solid rgba(85,99,68,0.18); }
    .status-chip.is-off { background:var(--alert-bg); color:var(--alert-text); border:1px solid rgba(186,26,26,0.16); }
    .status-dot { width:10px; height:10px; border-radius:50%; display:inline-block; }
    .status-chip.is-on .status-dot { background:var(--secondary); box-shadow:0 0 0 4px rgba(85,99,68,0.12); }
    .status-chip.is-off .status-dot { background:var(--alert-text); box-shadow:0 0 0 4px rgba(186,26,26,0.08); }
    .metric-chip { display:inline-flex; align-items:center; gap:10px; padding:8px 12px; border-radius:999px; font-size:20px; font-weight:700; margin-top:6px; }
    .metric-chip.is-good { background:var(--good-bg); color:var(--good-text); border:1px solid rgba(85,99,68,0.18); }
    .metric-chip.is-warn { background:var(--warn-bg); color:var(--warn-text); border:1px solid rgba(166,141,106,0.22); }
    .metric-chip.is-alert { background:var(--alert-bg); color:var(--alert-text); border:1px solid rgba(186,26,26,0.16); }
    .events { padding:0 20px 20px 20px; }
    table { width:100%; border-collapse:collapse; font-size:13px; }
    th,td { text-align:left; padding:12px 8px; border-bottom:1px solid rgba(166,141,106,0.16); }
    tbody tr:nth-child(odd) { background:rgba(255,255,255,0.75); }
    tbody tr:nth-child(even) { background:rgba(246,243,239,0.7); }
    th { color:var(--muted); font-size:11px; text-transform:uppercase; letter-spacing:.08em; }
    .links { display:flex; gap:10px; flex-wrap:wrap; margin-top:10px; }
    .links a {
      color:var(--on-primary, #ffffff);
      text-decoration:none;
      font-size:13px;
      font-weight:600;
      padding:10px 14px;
      border-radius:999px;
      background:linear-gradient(135deg, var(--primary), var(--primary-soft));
      border:1px solid rgba(6,27,14,0.18);
      transition:transform .14s ease, box-shadow .14s ease, background .14s ease;
    }
    .links a:hover { background:linear-gradient(135deg, #0d2414, #253c2d); transform:translateY(-1px); box-shadow:var(--shadow-hover); }
    .toolbar { display:flex; gap:12px; flex-wrap:wrap; padding:20px 20px 14px 20px; align-items:end; }
    .field { display:flex; flex-direction:column; gap:6px; }
    .field label { font-size:12px; color:var(--muted); text-transform:uppercase; letter-spacing:.08em; }
    .field input, .field select, .field button {
      min-height:44px;
      background:var(--surface-low);
      border:1px solid rgba(166,141,106,0.28);
      color:var(--text);
      border-radius:var(--radius-md);
      padding:10px 12px;
      font-size:14px;
      font-family:inherit;
    }
    .field input:focus, .field select:focus {
      outline:none;
      border-color:var(--primary-soft);
      box-shadow:inset 0 0 0 1px rgba(27,48,34,0.18);
    }
    .field button {
      cursor:pointer;
      background:linear-gradient(135deg, var(--primary), var(--primary-soft));
      color:#ffffff;
      font-weight:700;
      border:none;
      transition:transform .14s ease, filter .14s ease, opacity .14s ease, box-shadow .14s ease;
    }
    .field button:hover { transform:translateY(-1px); filter:brightness(1.03); box-shadow:var(--shadow-hover); }
    .field button:disabled { opacity:.45; cursor:not-allowed; transform:none; filter:none; }
    .field button.is-secondary { background:var(--surface-lowest); color:var(--secondary); border:1px solid rgba(85,99,68,0.24); }
    .field small { color:var(--muted); font-size:12px; line-height:1.4; }
    .field.is-grow { min-width:240px; flex:1 1 260px; }
    .toolbar-note { padding:0 20px 0 20px; color:var(--muted); font-size:13px; line-height:1.45; }
    .toolbar-status { display:flex; justify-content:space-between; align-items:center; gap:12px; flex-wrap:wrap; padding:12px 20px 0 20px; }
    .analytics-mode { display:inline-flex; align-items:center; gap:10px; padding:10px 14px; border-radius:999px; font-size:13px; font-weight:700; }
    .analytics-mode.is-live { background:var(--good-bg); color:var(--good-text); border:1px solid rgba(85,99,68,0.16); }
    .analytics-mode.is-manual { background:var(--warn-bg); color:var(--warn-text); border:1px solid rgba(166,141,106,0.18); }
    .analytics-dot { width:10px; height:10px; border-radius:50%; background:currentColor; box-shadow:0 0 0 4px rgba(85,99,68,0.10); }
    .quick-range { display:flex; flex-wrap:wrap; gap:10px; padding:14px 20px 6px 20px; }
    .quick-range button {
      cursor:pointer;
      background:var(--surface-lowest);
      border:1px solid rgba(85,99,68,0.18);
      color:var(--secondary);
      border-radius:999px;
      padding:10px 14px;
      font-size:13px;
      font-weight:600;
      transition:transform .14s ease, background .14s ease, border-color .14s ease;
    }
    .quick-range button:hover { transform:translateY(-1px); }
    .quick-range button.is-active { background:linear-gradient(135deg, var(--secondary), #6a7858); border-color:rgba(85,99,68,0.3); color:#ffffff; }
    .range-summary { display:flex; flex-wrap:wrap; gap:10px; padding:10px 20px 18px 20px; }
    .summary-chip { display:inline-flex; align-items:center; gap:8px; padding:10px 12px; border-radius:14px; background:var(--surface-lowest); border:1px solid rgba(166,141,106,0.18); color:var(--primary); font-size:13px; }
    .summary-chip .k { color:var(--muted); text-transform:uppercase; letter-spacing:.08em; font-size:11px; }
    .summary-chip .v { font-weight:700; }
    .range-highlight { margin:0 20px 6px 20px; padding:14px 16px; border-radius:var(--radius-lg); background:linear-gradient(135deg, rgba(216,232,193,0.44), rgba(252,222,182,0.28)); border:1px solid rgba(166,141,106,0.22); }
    .range-highlight .k { color:var(--muted); text-transform:uppercase; letter-spacing:.1em; font-size:11px; }
    .range-highlight .v { margin-top:6px; font-size:18px; font-weight:700; line-height:1.35; color:var(--primary); }
    .charts { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:18px; padding:0 20px 20px 20px; align-items:start; }
    .chart-card { position:relative; background:linear-gradient(180deg, var(--surface-lowest), rgba(252,249,245,0.92)); border:1px solid rgba(166,141,106,0.18); border-radius:var(--radius-lg); padding:16px; min-height:340px; transition:transform .18s ease, border-color .18s ease, box-shadow .18s ease; }
    .chart-card:hover { transform:translateY(-2px); border-color:rgba(166,141,106,0.34); box-shadow:var(--shadow-hover); }
    .chart-card canvas { width:100%; height:220px; display:block; }
    .chart-help { color:var(--muted); font-size:13px; line-height:1.5; margin:8px 0 12px 0; max-width:62ch; }
    .legend { display:flex; gap:14px; flex-wrap:wrap; margin:0 0 12px 0; }
    .legend-item { display:inline-flex; align-items:center; gap:8px; font-size:13px; color:var(--primary); }
    .legend-swatch { width:14px; height:14px; border-radius:999px; display:inline-block; }
    .chart-tooltip { position:absolute; display:none; pointer-events:none; min-width:160px; padding:10px 12px; border-radius:12px; background:rgba(252,249,245,0.98); border:1px solid rgba(166,141,106,0.24); color:var(--text); font-size:12px; line-height:1.4; box-shadow:var(--shadow-soft); z-index:3; }
    .chart-tooltip .tt-title { color:var(--secondary); margin-bottom:4px; }
    .reason { color:var(--muted); font-size:13px; margin-top:10px; line-height:1.45; }
    .history-note { padding:0 20px 18px 20px; color:var(--muted); font-size:13px; line-height:1.45; }
    @media (max-width: 1100px) {
      .grid { grid-template-columns:1fr; }
    }
    @media (max-width: 900px) {
      .wrap { padding:18px 16px 28px 16px; }
      .hero { padding:18px; }
      h1 { font-size:28px; line-height:36px; }
      .charts { grid-template-columns:1fr; }
      .stats { grid-template-columns:1fr; }
      .stream { min-height:260px; }
      .chart-card { min-height:320px; }
      .links { gap:8px; }
      .links a { flex:1 1 auto; justify-content:center; text-align:center; }
    }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="hero">
      <div class="hero-copy">
        <div class="hero-kicker">Monitor IMX500 en vivo</div>
        <h1>V2 ChincheMalva</h1>
        <div class="sub">Camara IMX500 en directo, metadatos, evolución ambiental y seguimiento visual del último tramo de actividad.</div>
        <div class="hero-meta">
          <span class="hero-pill">Detección en tiempo real</span>
          <span class="hero-pill">Overlay de cajas sincronizado</span>
          <span class="hero-pill">Analítica viva y rango manual</span>
        </div>
      </div>
      <div class="links">
        <a href="/latest" target="_blank">Estado actual</a>
        <a href="/events" target="_blank">Historico</a>
        <a href="/live.jpg" target="_blank">Imagen viva</a>
      </div>
    </div>
    <div class="grid">
      <div class="panel">
        <h2>Vista en directo</h2>
        <div class="stream-wrap">
          <div class="stream-stage">
            <video id="live-video" class="stream" autoplay playsinline muted style="display:none;"></video>
            <canvas id="live-canvas" class="stream" width="1280" height="720"></canvas>
          </div>
          <div class="links" style="margin-top:14px;">
            <a id="mode-live-btn" href="#" onclick="return false;">Directo rapido</a>
            <a id="mode-webrtc-btn" href="#" onclick="return false;">Probar WebRTC</a>
          </div>
          <div class="reason">
            La vista principal usa frames vivos servidos desde memoria compartida. WebRTC queda como modo experimental.
          </div>
          <div id="stream-mode" class="reason">Modo: iniciando...</div>
        </div>
      </div>
      <div class="panel">
        <h2>Estado</h2>
        <div class="stats">
          <div class="card is-wide"><div class="label">Estado camara</div><div id="camera-status" class="value small">--</div></div>
          <div class="card"><div class="label">Detecciones</div><div id="detections" class="value">--</div></div>
          <div class="card"><div class="label">Temperatura</div><div id="temp" class="value">--</div></div>
          <div class="card"><div class="label">Humedad</div><div id="hum" class="value">--</div></div>
          <div class="card"><div class="label">Ultima lectura</div><div id="ts" class="value small">--</div></div>
        </div>
      </div>
    </div>
    <div class="panel" style="margin-top:18px;">
      <h2>Analitica</h2>
      <div class="toolbar">
        <div class="field">
          <label for="date-from">Desde</label>
          <input id="date-from" type="datetime-local" value="__DEFAULT_FROM__">
        </div>
        <div class="field">
          <label for="date-to">Hasta</label>
          <input id="date-to" type="datetime-local" value="__DEFAULT_TO__">
        </div>
        <div class="field is-grow">
          <label for="limit">Resolucion</label>
          <select id="limit">
            <option value="24">Baja · 24 puntos</option>
            <option value="72">Media · 72 puntos</option>
            <option value="144" selected>Alta · 144 puntos</option>
            <option value="288">Muy alta · 288 puntos</option>
          </select>
          <small>Más puntos = más detalle, pero una gráfica más cargada.</small>
        </div>
        <div class="field">
          <label>&nbsp;</label>
          <button id="apply-btn" type="button">Aplicar rango</button>
        </div>
        <div class="field">
          <label>&nbsp;</label>
          <button id="current-chart-btn" class="is-secondary" type="button">Grafica actual</button>
        </div>
      </div>
      <div class="toolbar-note">Puedes marcar un periodo exacto con <strong>Desde</strong> y <strong>Hasta</strong>, o usar uno de los accesos rápidos para cambiar el rango de un toque.</div>
      <div class="toolbar-status">
        <div id="analytics-mode" class="analytics-mode is-live"><span class="analytics-dot"></span><span>Gráfica en directo · se actualiza sola</span></div>
      </div>
      <div class="quick-range">
        <button type="button" data-range="current">Grafica actual</button>
        <button type="button" data-range="1h">Ultima 1h</button>
        <button type="button" data-range="6h" class="is-active">Ultimas 6h</button>
        <button type="button" data-range="24h">Ultimas 24h</button>
        <button type="button" data-range="today">Hoy</button>
      </div>
      <div class="range-highlight">
        <div class="k">Periodo seleccionado</div>
        <div id="summary-range-text" class="v">--</div>
      </div>
      <div class="range-summary">
        <div class="summary-chip"><span class="k">Desde</span><span id="summary-from" class="v">--</span></div>
        <div class="summary-chip"><span class="k">Hasta</span><span id="summary-to" class="v">--</span></div>
        <div class="summary-chip"><span class="k">Duracion</span><span id="summary-duration" class="v">--</span></div>
        <div class="summary-chip"><span class="k">Resolucion</span><span id="summary-points" class="v">--</span></div>
      </div>
      <div class="charts">
        <div class="chart-card">
          <div class="label">Detecciones en el tiempo</div>
          <div class="chart-help">Esta gráfica enseña cómo va cambiando el número de chinches malvas detectadas a lo largo del tiempo seleccionado. Sirve para ver picos, momentos de calma y evolución general.</div>
          <div class="legend">
            <span class="legend-item"><span class="legend-swatch" style="background:#556344;"></span> Número de detecciones</span>
          </div>
          <canvas id="detections-chart" width="560" height="220"></canvas>
          <div id="detections-tooltip" class="chart-tooltip"></div>
        </div>
        <div class="chart-card">
          <div class="label">Clima en el tiempo</div>
          <div class="chart-help">Esta gráfica muestra cómo cambian la temperatura y la humedad en el intervalo elegido. Sirve para relacionar el ambiente con la actividad detectada.</div>
          <div class="legend">
            <span class="legend-item"><span class="legend-swatch" style="background:#a88f6c;"></span> Temperatura</span>
            <span class="legend-item"><span class="legend-swatch" style="background:#1b3022;"></span> Humedad</span>
          </div>
          <canvas id="climate-chart" width="560" height="220"></canvas>
          <div id="climate-tooltip" class="chart-tooltip"></div>
        </div>
      </div>
    </div>
    <div class="panel" style="margin-top:18px;">
      <h2>Historico reciente</h2>
      <div class="history-note">La tabla sigue mostrando muestras recientes para revisión rápida, mientras que las gráficas pueden quedar en directo o fijadas al rango que elijas.</div>
      <div class="events">
        <table>
          <thead><tr><th>Hora</th><th>Detecciones</th><th>Temp</th><th>Humedad</th></tr></thead>
          <tbody id="events-body"></tbody>
        </table>
      </div>
    </div>
  </div>
  <script>
    let liveLoopActive = false;
    let webrtcStarted = false;
    let paintLoopActive = false;
    let latestState = null;
    let smoothedBoxes = new Map();
    let chartState = {};
    let chartHoverState = {};
    let analyticsLiveMode = true;
    let analyticsRefreshInFlight = false;
    let analyticsControlsDirty = false;
    const BOX_SMOOTHING_STILL = 0.18;
    const BOX_SMOOTHING_FAST = 0.98;
    const BOX_MOVE_THRESHOLD = 4;
    const BOX_DROP_MS = 500;
    const DETECTION_WARN_MIN = 3;
    const DETECTION_ALERT_MIN = 6;
    const TEMP_GOOD_MIN = 18;
    const TEMP_GOOD_MAX = 28;
    const TEMP_WARN_MIN = 15;
    const TEMP_WARN_MAX = 30;
    const HUM_GOOD_MIN = 45;
    const HUM_GOOD_MAX = 70;
    const HUM_WARN_MIN = 35;
    const HUM_WARN_MAX = 80;
    const LIVE_ANALYTICS_RANGE = 'current';
    const OVERLAY_STROKE = '#a88f6c';
    const OVERLAY_FILL = 'rgba(168, 143, 108, 0.14)';
    const OVERLAY_LABEL_BG = 'rgba(6, 27, 14, 0.88)';
    const CHART_BG = '#f6f3ef';
    const CHART_TEXT = 'rgba(67, 72, 67, 0.86)';
    const CHART_GRID = 'rgba(166, 141, 106, 0.24)';
    const CHART_HOVER = 'rgba(85, 99, 68, 0.35)';
    const CHART_POINT_FILL = '#fcf9f5';

    function setMode(text) {
      document.getElementById('stream-mode').textContent = 'Modo: ' + text;
    }

    function getCameraStatus(latest) {
      if (!latest || !latest.timestamp) return 'Apagada';
      const ts = new Date(latest.timestamp);
      if (Number.isNaN(ts.getTime())) return 'Apagada';
      const ageMs = Date.now() - ts.getTime();
      return ageMs <= 5000 ? 'Encendida' : 'Apagada';
    }

    function renderCameraStatus(status) {
      const el = document.getElementById('camera-status');
      const isOn = status === 'Encendida';
      el.innerHTML = `<span class="status-chip ${isOn ? 'is-on' : 'is-off'}"><span class="status-dot"></span>${status}</span>`;
    }

    function metricClass(level) {
      if (level === 'alert') return 'is-alert';
      if (level === 'warn') return 'is-warn';
      return 'is-good';
    }

    function renderMetricValue(id, text, level) {
      const el = document.getElementById(id);
      el.innerHTML = `<span class="metric-chip ${metricClass(level)}">${text}</span>`;
    }

    function formatSummaryDate(raw) {
      if (!raw) return '--';
      const date = new Date(raw);
      if (Number.isNaN(date.getTime())) return raw;
      return date.toLocaleString('es-ES', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    }

    function formatDuration(dateFrom, dateTo) {
      const from = new Date(dateFrom);
      const to = new Date(dateTo);
      if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) return '--';
      const totalMinutes = Math.max(0, Math.round((to.getTime() - from.getTime()) / 60000));
      const days = Math.floor(totalMinutes / 1440);
      const hours = Math.floor((totalMinutes % 1440) / 60);
      const minutes = totalMinutes % 60;
      const parts = [];
      if (days) parts.push(days + ' d');
      if (hours) parts.push(hours + ' h');
      if (minutes || parts.length === 0) parts.push(minutes + ' min');
      return parts.join(' ');
    }

    function formatRangeSentence(dateFrom, dateTo) {
      const from = formatSummaryDate(dateFrom);
      const to = formatSummaryDate(dateTo);
      if (from === '--' || to === '--') return 'Mostrando el rango completo disponible';
      return `Mostrando datos desde ${from} hasta ${to}`;
    }

    function setAnalyticsMode(isLive, label) {
      analyticsLiveMode = isLive;
      const el = document.getElementById('analytics-mode');
      el.className = 'analytics-mode ' + (isLive ? 'is-live' : 'is-manual');
      el.innerHTML = `<span class="analytics-dot"></span><span>${label}</span>`;
    }

    function setAnalyticsDirty(isDirty) {
      analyticsControlsDirty = isDirty;
      document.getElementById('apply-btn').disabled = !isDirty;
    }

    function markAnalyticsDirty() {
      clearQuickRangeSelection();
      setAnalyticsDirty(true);
      if (analyticsLiveMode) {
        setAnalyticsMode(false, 'Cambios pendientes · pulsa Aplicar rango o vuelve a Grafica actual');
      }
    }

    function setQuickRange(mode) {
      const to = new Date();
      const from = new Date(to);
      if (mode === 'current' || mode === '1h') from.setHours(to.getHours() - 1);
      if (mode === '6h') from.setHours(to.getHours() - 6);
      if (mode === '24h') from.setHours(to.getHours() - 24);
      if (mode === 'today') {
        from.setHours(0, 0, 0, 0);
      }
      const fromInput = document.getElementById('date-from');
      const toInput = document.getElementById('date-to');
      fromInput.value = from.toISOString().slice(0, 16);
      toInput.value = to.toISOString().slice(0, 16);
      document.querySelectorAll('.quick-range button').forEach((btn) => {
        btn.classList.toggle('is-active', btn.dataset.range === mode);
      });
      const liveLabel = mode === LIVE_ANALYTICS_RANGE
        ? 'Gráfica actual · ventana móvil de la última hora'
        : 'Gráfica en directo · se actualiza sola';
      setAnalyticsMode(true, liveLabel);
      setAnalyticsDirty(false);
      refreshAnalytics(true);
    }

    function clearQuickRangeSelection() {
      document.querySelectorAll('.quick-range button').forEach((btn) => btn.classList.remove('is-active'));
    }

    function updateAnalyticsSummary(limit, dateFrom, dateTo) {
      const limitLabel = document.getElementById('limit').selectedOptions[0]?.textContent || String(limit);
      document.getElementById('summary-from').textContent = formatSummaryDate(dateFrom);
      document.getElementById('summary-to').textContent = formatSummaryDate(dateTo);
      document.getElementById('summary-duration').textContent = formatDuration(dateFrom, dateTo);
      document.getElementById('summary-points').textContent = limitLabel;
      document.getElementById('summary-range-text').textContent = formatRangeSentence(dateFrom, dateTo);
    }

    function getDetectionLevel(total) {
      const value = Number(total ?? 0);
      if (value >= DETECTION_ALERT_MIN) return 'alert';
      if (value >= DETECTION_WARN_MIN) return 'warn';
      return 'good';
    }

    function getTemperatureLevel(temp) {
      const value = Number(temp);
      if (Number.isNaN(value)) return 'warn';
      if (value < TEMP_WARN_MIN || value > TEMP_WARN_MAX) return 'alert';
      if (value < TEMP_GOOD_MIN || value > TEMP_GOOD_MAX) return 'warn';
      return 'good';
    }

    function getHumidityLevel(hum) {
      const value = Number(hum);
      if (Number.isNaN(value)) return 'warn';
      if (value < HUM_WARN_MIN || value > HUM_WARN_MAX) return 'alert';
      if (value < HUM_GOOD_MIN || value > HUM_GOOD_MAX) return 'warn';
      return 'good';
    }

    function lerp(a, b, t) {
      return a + (b - a) * t;
    }

    function boxCenter(box) {
      return {
        x: (box.x1 + box.x2) / 2,
        y: (box.y1 + box.y2) / 2,
      };
    }

    function updateSmoothedBoxes(state) {
      const now = Date.now();
      const next = new Map();
      const currentBoxes = Array.isArray(state?.boxes) ? state.boxes : [];

      for (const raw of currentBoxes) {
        const id = String(raw.id ?? '');
        if (!id) continue;
        const target = {
          id,
          x1: Number(raw.x1),
          y1: Number(raw.y1),
          x2: Number(raw.x2),
          y2: Number(raw.y2),
          seenAt: now,
        };
        const prev = smoothedBoxes.get(id);
        if (!prev) {
          next.set(id, target);
          continue;
        }
        const prevCenter = boxCenter(prev);
        const targetCenter = boxCenter(target);
        const movement = Math.hypot(targetCenter.x - prevCenter.x, targetCenter.y - prevCenter.y);
        const smoothing = movement >= BOX_MOVE_THRESHOLD ? BOX_SMOOTHING_FAST : BOX_SMOOTHING_STILL;
        next.set(id, {
          id,
          x1: lerp(prev.x1, target.x1, smoothing),
          y1: lerp(prev.y1, target.y1, smoothing),
          x2: lerp(prev.x2, target.x2, smoothing),
          y2: lerp(prev.y2, target.y2, smoothing),
          seenAt: now,
        });
      }

      for (const [id, prev] of smoothedBoxes.entries()) {
        if (next.has(id)) continue;
        if (now - prev.seenAt < BOX_DROP_MS) {
          next.set(id, prev);
        }
      }

      smoothedBoxes = next;
    }

    function startCanvasPaintFromImageBlob(blob) {
      const canvas = document.getElementById('live-canvas');
      const ctx = canvas.getContext('2d');
      const url = URL.createObjectURL(blob);
      const img = new Image();
      img.onload = () => {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        URL.revokeObjectURL(url);
        drawOverlay(ctx, canvas);
      };
      img.src = url;
    }

    function startCanvasPaintFromVideo() {
      if (paintLoopActive) return;
      paintLoopActive = true;
      const video = document.getElementById('live-video');
      const canvas = document.getElementById('live-canvas');
      const ctx = canvas.getContext('2d');
      function paint() {
        if (video.readyState >= 2) {
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          drawOverlay(ctx, canvas);
        }
        if (webrtcStarted) {
          requestAnimationFrame(paint);
        } else {
          paintLoopActive = false;
        }
      }
      requestAnimationFrame(paint);
    }

    async function setLiveFrame() {
      if (webrtcStarted) return;
      if (liveLoopActive) return;
      liveLoopActive = true;
      try {
        const response = await fetch('/live.jpg?t=' + Date.now(), { cache: 'no-store' });
        if (!response.ok) throw new Error('frame vivo no disponible');
        const blob = await response.blob();
        startCanvasPaintFromImageBlob(blob);
        liveLoopActive = false;
        setTimeout(setLiveFrame, 80);
      } catch (err) {
        liveLoopActive = false;
      setTimeout(setLiveFrame, 180);
      }
    }

    function drawOverlay(ctx, canvas) {
      if (!latestState || smoothedBoxes.size === 0) return;
      const srcW = Number(latestState.image_width || 640);
      const srcH = Number(latestState.image_height || 480);
      const sx = canvas.width / srcW;
      const sy = canvas.height / srcH;

      ctx.lineWidth = 4;
      ctx.strokeStyle = OVERLAY_STROKE;
      ctx.font = '600 20px "Hanken Grotesk", sans-serif';
      ctx.textBaseline = 'top';

      for (const box of smoothedBoxes.values()) {
        const x = Number(box.x1) * sx;
        const y = Number(box.y1) * sy;
        const w = (Number(box.x2) - Number(box.x1)) * sx;
        const h = (Number(box.y2) - Number(box.y1)) * sy;
        if (!(w > 0 && h > 0)) continue;
        ctx.fillStyle = OVERLAY_FILL;
        ctx.fillRect(x, y, w, h);
        ctx.strokeRect(x, y, w, h);
        const label = String(box.id ?? '');
        if (label) {
          const tx = Math.max(0, x);
          const ty = Math.max(0, y - 26);
          const tw = Math.max(26, ctx.measureText(label).width + 14);
          ctx.fillStyle = OVERLAY_LABEL_BG;
          ctx.fillRect(tx, ty, tw, 24);
          ctx.fillStyle = '#fcf9f5';
          ctx.fillText(label, tx + 7, ty + 3);
        }
      }
    }

    async function startWebRTC() {
      const video = document.getElementById('live-video');
      const pc = new RTCPeerConnection({
        iceServers: [{ urls: ['stun:stun.l.google.com:19302'] }]
      });
      let gotTrack = false;

      pc.ontrack = async (event) => {
        gotTrack = true;
        video.srcObject = event.streams[0];
        try { await video.play(); } catch (e) {}
        video.style.display = 'none';
        document.getElementById('live-canvas').style.display = 'block';
        webrtcStarted = true;
        startCanvasPaintFromVideo();
        setMode('WebRTC experimental');
      };

      pc.onconnectionstatechange = () => {
        if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected' || pc.connectionState === 'closed') {
          startLiveMode();
        }
      };

      try {
        pc.addTransceiver('video', { direction: 'recvonly' });
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        const response = await fetch('/webrtc/offer', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            sdp: pc.localDescription.sdp,
            type: pc.localDescription.type
          })
        });
        if (!response.ok) throw new Error('offer failed');
        const answer = await response.json();
        await pc.setRemoteDescription(answer);
        setTimeout(() => {
          if (!gotTrack) startLiveMode();
        }, 5000);
      } catch (err) {
        startLiveMode();
      }
    }

    function startLiveMode() {
      webrtcStarted = false;
      document.getElementById('live-video').style.display = 'none';
      document.getElementById('live-canvas').style.display = 'block';
      setMode('Directo rapido');
      setLiveFrame();
    }

    function formatAxisValue(value) {
      if (!Number.isFinite(value)) return '--';
      return Number.isInteger(value) ? String(value) : value.toFixed(1);
    }

    function drawChart(canvasId, points, series, options = {}) {
      const canvas = document.getElementById(canvasId);
      const ctx = canvas.getContext('2d');
      const width = canvas.width;
      const height = canvas.height;
      const leftPad = 46;
      const rightPad = options.rightAxis ? 46 : 10;
      const topPad = 20;
      const bottomPad = 20;
      ctx.clearRect(0, 0, width, height);
      ctx.fillStyle = CHART_BG;
      ctx.fillRect(0, 0, width, height);
      ctx.font = '12px "Hanken Grotesk", sans-serif';
      ctx.fillStyle = CHART_TEXT;
      ctx.strokeStyle = CHART_GRID;
      const leftValues = points.map(options.leftSelector || series[0].selector).filter(v => v != null);
      const leftMin = leftValues.length ? Math.min(...leftValues) : 0;
      const leftMax = leftValues.length ? Math.max(...leftValues) : 1;
      const leftRange = (leftMax - leftMin) || 1;
      let rightMin = 0;
      let rightMax = 1;
      let rightRange = 1;
      if (options.rightAxis && options.rightSelector) {
        const rightValues = points.map(options.rightSelector).filter(v => v != null);
        rightMin = rightValues.length ? Math.min(...rightValues) : 0;
        rightMax = rightValues.length ? Math.max(...rightValues) : 1;
        rightRange = (rightMax - rightMin) || 1;
      }
      for (let i = 0; i < 5; i++) {
        const y = topPad + (height - topPad - bottomPad) * i / 4;
        ctx.beginPath();
        ctx.moveTo(leftPad, y);
        ctx.lineTo(width - rightPad, y);
        ctx.stroke();
        const leftLabel = leftMax - (leftRange * i / 4);
        ctx.fillText(formatAxisValue(leftLabel), 4, y + 4);
        if (options.rightAxis) {
          const rightLabel = rightMax - (rightRange * i / 4);
          const label = formatAxisValue(rightLabel);
          const textWidth = ctx.measureText(label).width;
          ctx.fillText(label, width - textWidth - 4, y + 4);
        }
      }
      series.forEach((item, seriesIndex) => {
        const selector = item.selector;
        const values = points.map(selector).filter(v => v != null);
        if (!values.length) return;
        const isRightAxis = Boolean(options.rightAxis && item.axis === 'right');
        const min = isRightAxis ? rightMin : leftMin;
        const max = isRightAxis ? rightMax : leftMax;
        const range = isRightAxis ? rightRange : leftRange;
        ctx.strokeStyle = item.color;
        ctx.lineWidth = 2;
        ctx.beginPath();
        points.forEach((point, index) => {
          const value = selector(point);
          if (value == null) return;
          const x = leftPad + ((width - leftPad - rightPad) * index / Math.max(points.length - 1, 1));
          const y = height - bottomPad - ((value - min) / range) * (height - topPad - bottomPad);
          if (index === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        });
        ctx.stroke();
      });

      const hover = chartHoverState[canvasId];
      if (hover && Number.isFinite(hover.x)) {
        ctx.strokeStyle = CHART_HOVER;
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(hover.x, topPad);
        ctx.lineTo(hover.x, height - bottomPad);
        ctx.stroke();

        series.forEach((item) => {
          const point = points[hover.index];
          if (!point) return;
          const value = item.selector(point);
          if (value == null) return;
          const isRightAxis = Boolean(options.rightAxis && item.axis === 'right');
          const min = isRightAxis ? rightMin : leftMin;
          const range = isRightAxis ? rightRange : leftRange;
          const y = height - bottomPad - ((value - min) / range) * (height - topPad - bottomPad);
          ctx.beginPath();
          ctx.fillStyle = CHART_POINT_FILL;
          ctx.arc(hover.x, y, 5.5, 0, Math.PI * 2);
          ctx.fill();
          ctx.beginPath();
          ctx.fillStyle = item.color;
          ctx.arc(hover.x, y, 3.5, 0, Math.PI * 2);
          ctx.fill();
        });
      }

      chartState[canvasId] = {
        points,
        series,
        bounds: { leftPad, rightPad, topPad, bottomPad, width, height },
        options,
      };
    }

    function attachChartTooltip(canvasId, tooltipId, formatter) {
      const canvas = document.getElementById(canvasId);
      const tooltip = document.getElementById(tooltipId);
      canvas.addEventListener('mousemove', (event) => {
        const state = chartState[canvasId];
        if (!state || !state.points.length) {
          tooltip.style.display = 'none';
          return;
        }
        const rect = canvas.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const { leftPad, rightPad, width } = state.bounds;
        const usableWidth = width - leftPad - rightPad;
        const ratio = Math.max(0, Math.min(1, (x - leftPad) / Math.max(usableWidth, 1)));
        const index = Math.round(ratio * Math.max(state.points.length - 1, 0));
        const point = state.points[index];
        if (!point) {
          tooltip.style.display = 'none';
          return;
        }
        const pointX = leftPad + ((width - leftPad - rightPad) * index / Math.max(state.points.length - 1, 1));
        chartHoverState[canvasId] = { index, x: pointX };
        drawChart(canvasId, state.points, state.series, state.options || {});
        tooltip.innerHTML = formatter(point);
        tooltip.style.display = 'block';
        tooltip.style.left = Math.min(rect.width - 180, Math.max(12, x + 14)) + 'px';
        tooltip.style.top = Math.max(12, (event.clientY - rect.top) - 24) + 'px';
      });
      canvas.addEventListener('mouseleave', () => {
        const state = chartState[canvasId];
        chartHoverState[canvasId] = null;
        if (state) {
          drawChart(canvasId, state.points, state.series, state.options || {});
        }
        tooltip.style.display = 'none';
      });
    }

    async function refreshLatest() {
      const latestRes = await fetch('/latest?t=' + Date.now(), { cache: 'no-store' });
      const latest = await latestRes.json();
      latestState = latest;
      updateSmoothedBoxes(latest);
      renderMetricValue('detections', String(latest.total_detections ?? '--'), getDetectionLevel(latest.total_detections));
      renderMetricValue('temp', latest.temperature_c != null ? latest.temperature_c + ' C' : '--', getTemperatureLevel(latest.temperature_c));
      renderMetricValue('hum', latest.humidity_pct != null ? latest.humidity_pct + ' %' : '--', getHumidityLevel(latest.humidity_pct));
      document.getElementById('ts').textContent = latest.timestamp ?? '--';
      renderCameraStatus(getCameraStatus(latest));
    }

    async function refreshAnalytics(force = false) {
      if (analyticsRefreshInFlight) return;
      if (!force && !analyticsLiveMode) return;
      analyticsRefreshInFlight = true;
      const limit = document.getElementById('limit').value;
      const dateFrom = document.getElementById('date-from').value;
      const dateTo = document.getElementById('date-to').value;
      try {
        updateAnalyticsSummary(limit, dateFrom, dateTo);
        const params = new URLSearchParams({ limit: String(limit) });
        if (dateFrom) params.set('date_from', dateFrom.replace('T', 'T'));
        if (dateTo) params.set('date_to', dateTo.replace('T', 'T'));
        const eventsParams = new URLSearchParams({ limit: '12' });
        if (dateFrom) eventsParams.set('date_from', dateFrom.replace('T', 'T'));
        if (dateTo) eventsParams.set('date_to', dateTo.replace('T', 'T'));
        const [eventsRes, statsRes] = await Promise.all([
          fetch('/events?' + eventsParams.toString() + '&t=' + Date.now(), { cache: 'no-store' }),
          fetch('/stats?' + params.toString() + '&t=' + Date.now(), { cache: 'no-store' }),
        ]);
        const eventsPayload = await eventsRes.json();
        const statsPayload = await statsRes.json();
        const body = document.getElementById('events-body');
        body.innerHTML = '';
        for (const ev of (eventsPayload.events || [])) {
          const tr = document.createElement('tr');
          tr.innerHTML = `<td>${ev.timestamp || '--'}</td><td>${ev.total_detections ?? '--'}</td><td>${ev.temperature_c ?? '--'}</td><td>${ev.humidity_pct ?? '--'}</td>`;
          body.appendChild(tr);
        }
        const points = statsPayload.points || [];
        drawChart('detections-chart', points, [
          { selector: p => p.total_detections, color: '#556344', axis: 'left' }
        ]);
        drawChart('climate-chart', points, [
          { selector: p => p.temperature_c, color: '#a88f6c', axis: 'left' },
          { selector: p => p.humidity_pct, color: '#1b3022', axis: 'right' }
        ], {
          rightAxis: true,
          leftSelector: p => p.temperature_c,
          rightSelector: p => p.humidity_pct,
        });
      } finally {
        analyticsRefreshInFlight = false;
      }
    }
    document.getElementById('apply-btn').addEventListener('click', () => {
      setAnalyticsMode(false, 'Rango fijado · no se actualiza hasta volver a Grafica actual');
      setAnalyticsDirty(false);
      refreshAnalytics(true);
    });
    document.getElementById('current-chart-btn').addEventListener('click', () => setQuickRange(LIVE_ANALYTICS_RANGE));
    document.getElementById('date-from').addEventListener('input', markAnalyticsDirty);
    document.getElementById('date-to').addEventListener('input', markAnalyticsDirty);
    document.getElementById('limit').addEventListener('change', markAnalyticsDirty);
    document.querySelectorAll('.quick-range button').forEach((button) => {
      button.addEventListener('click', () => setQuickRange(button.dataset.range));
    });
    document.getElementById('mode-live-btn').addEventListener('click', startLiveMode);
    document.getElementById('mode-webrtc-btn').addEventListener('click', startWebRTC);
    attachChartTooltip('detections-chart', 'detections-tooltip', (point) => `
      <div class="tt-title">${point.timestamp || '--'}</div>
      <div>Detecciones: <strong>${point.total_detections ?? '--'}</strong></div>
    `);
    attachChartTooltip('climate-chart', 'climate-tooltip', (point) => `
      <div class="tt-title">${point.timestamp || '--'}</div>
      <div>Temperatura: <strong>${point.temperature_c ?? '--'} C</strong></div>
      <div>Humedad: <strong>${point.humidity_pct ?? '--'} %</strong></div>
    `);
    startLiveMode();
    refreshLatest();
    setAnalyticsDirty(false);
    refreshAnalytics(true);
    setInterval(refreshLatest, 120);
    setInterval(() => refreshAnalytics(false), 5000);
  </script>
</body>
</html>
        """
    html = html.replace("__DEFAULT_FROM__", default_from).replace("__DEFAULT_TO__", default_to)
    return HTMLResponse(html)
