package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.OrigenLectura;

public record LecturaCreateDto(Long sensorId, Double valor, String unidad, OrigenLectura origen) {
}
