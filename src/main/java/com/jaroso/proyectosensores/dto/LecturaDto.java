package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.OrigenLectura;
import java.time.LocalDateTime;

public record LecturaDto(Long id, Long sensorId, Double valor, String unidad, LocalDateTime fechaHora, OrigenLectura origen) {
}
