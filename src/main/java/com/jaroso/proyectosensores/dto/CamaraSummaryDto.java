package com.jaroso.proyectosensores.dto;

public record CamaraSummaryDto(Integer count, Integer max_detections, Double avg_detections,
                               Double avg_temperature_c, Double avg_humidity_pct) {
}
