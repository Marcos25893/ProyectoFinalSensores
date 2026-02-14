package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.EstadoSensor;
import com.jaroso.proyectosensores.entities.TipoSensor;

public record SensorDto(String id, String nombre, String description, String sector, TipoSensor tipo, EstadoSensor estado) {
}
