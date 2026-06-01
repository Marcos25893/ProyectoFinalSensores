package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.EstadoSensor;
import com.jaroso.proyectosensores.entities.TipoSensor;

public record SensorCreateDto(Long sectorId, String nombre, String descripcion, String ubicacion, String topicMQTT, String topicMQTTAct,
                              Integer valorMin, Integer valorMax, Boolean isActuador, TipoSensor tipo, EstadoSensor estado) {
}
