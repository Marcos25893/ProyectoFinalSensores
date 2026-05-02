package com.jaroso.proyectosensores.dto;

import java.util.List;

public record SensorDecisionResponseDto(boolean permiso, String motivo, List<ActuatorActionDto> accion) {
}
