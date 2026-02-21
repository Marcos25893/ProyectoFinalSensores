package com.jaroso.proyectosensores.dto;

import java.time.LocalDateTime;

public record FiltroLecturasDto(Long sensorId, LocalDateTime fechaDesde, LocalDateTime fechaHasta) {
}
