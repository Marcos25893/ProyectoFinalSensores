package com.jaroso.proyectosensores.dto;

import com.jaroso.proyectosensores.entities.EstadoSensor;
import com.jaroso.proyectosensores.entities.TipoSensor;

public record SensorDto(Long id,
                        String nombre,
                        TipoSensor tipo, EstadoSensor estado,
                        String ubicacion,
                        String topicMQTT,
                        String topicMQTTAct,
                        Integer valorMin,
                        Integer valorMax,
                        Boolean isActuador,
                        Long sectorId) {

}
