package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.EstadoSensor;

public record SensorUpdateDto(EstadoSensor estado, String sector) {
}
