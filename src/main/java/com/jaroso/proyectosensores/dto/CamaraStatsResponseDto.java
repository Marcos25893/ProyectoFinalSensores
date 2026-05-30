package com.jaroso.proyectosensores.dto;

import java.util.List;

public record CamaraStatsResponseDto(String camera_id, CamaraSummaryDto summary, List<CamaraEventDto> points) {
}
