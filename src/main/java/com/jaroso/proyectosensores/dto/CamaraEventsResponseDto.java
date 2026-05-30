package com.jaroso.proyectosensores.dto;

import java.util.List;

public record CamaraEventsResponseDto(String camera_id, Integer count, CamaraSummaryDto summary,
                                      List<CamaraEventDto> events) {
}
