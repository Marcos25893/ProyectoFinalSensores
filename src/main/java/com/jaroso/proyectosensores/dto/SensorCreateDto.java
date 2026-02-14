package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.EstadoSensor;
import com.jaroso.proyectosensores.entities.TipoSensor;

public record SensorCreateDto(String nombre, String description, String sector, TipoSensor tipo, EstadoSensor estado) {
}
