package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.EstadoSensor;

public record ActuatorActionDto(Long id, EstadoSensor estado) {
}
