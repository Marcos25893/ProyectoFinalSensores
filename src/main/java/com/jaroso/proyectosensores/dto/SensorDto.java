package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.EstadoSensor;
import com.jaroso.proyectosensores.entities.TipoSensor;

public record SensorDto(Long id, String nombre, String description, TipoSensor tipo, EstadoSensor estado, Long sectorId) {
}
