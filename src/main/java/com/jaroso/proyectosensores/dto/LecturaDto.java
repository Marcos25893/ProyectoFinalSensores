package com.jaroso.proyectosensores.dto;

import java.time.LocalDateTime;

public record LecturaDto(Long id, Long sensorId, Double valor, String unidad, LocalDateTime timestamp) {
}
