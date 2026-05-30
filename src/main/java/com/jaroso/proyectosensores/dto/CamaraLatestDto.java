package com.jaroso.proyectosensores.dto;

import java.util.List;

public record CamaraLatestDto(String camera_id, String timestamp, String model, Integer total_detections,
                              Double temperature_c, Double humidity_pct, Integer image_width, Integer image_height,
                              String status, List<CamaraBoxDto> boxes) {
}
