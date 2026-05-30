package com.jaroso.proyectosensores.dto;

import java.util.List;

public record CamaraEventDto(String camera_id, String timestamp, String model, Integer total_detections,
                             Double temperature_c, Double humidity_pct, Integer image_width, Integer image_height,
                             List<CamaraBoxDto> boxes) {
}
