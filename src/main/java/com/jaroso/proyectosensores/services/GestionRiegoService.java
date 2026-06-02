package com.jaroso.proyectosensores.services;

import com.jaroso.proyectosensores.dto.*;
import com.jaroso.proyectosensores.entities.*;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GestionRiegoService {

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private MqttService mqttService;

    @Transactional
    public SensorDecisionResponseDto procesarCambioEstado(Long idActuador, EstadoSensor nuevoEstado) {

        Sensor sensor = sensorRepository.findById(idActuador)
                .orElseThrow(() -> new RuntimeException("No se encontró el dispositivo con ID: " + idActuador));

        if (!Boolean.TRUE.equals(sensor.getIsActuador())) {
            return new SensorDecisionResponseDto(false, "El dispositivo no es actuador", List.of());
        }

        Map<Long, EstadoSensor> mapaAcciones = new LinkedHashMap<>();
        mapaAcciones.put(idActuador, nuevoEstado);

        Long sectorId = sensor.getSector().getId();

        if (sensor.getTipo() == TipoSensor.BOMBA && nuevoEstado == EstadoSensor.ACTIVO) {
            boolean hayValvulasAbiertas = sensorRepository.findBySectorIdAndTipo(sectorId, TipoSensor.ELECTROVALVULA)
                    .stream()
                    .anyMatch(v -> v.getEstado() == EstadoSensor.ACTIVO);

            if (!hayValvulasAbiertas) {
                return new SensorDecisionResponseDto(false, "No se puede encender la bomba no hay ninguna valvula abierta", List.of());
            }
        }

        if (sensor.getTipo() == TipoSensor.ELECTROVALVULA) {

            if (nuevoEstado == EstadoSensor.ACTIVO) {
                gestionarBomba(sectorId, EstadoSensor.ACTIVO, mapaAcciones);
            }
            else if (nuevoEstado == EstadoSensor.INACTIVO) {
                if (isUltimaValvulaActiva(sectorId, idActuador)) {
                    gestionarBomba(sectorId, EstadoSensor.INACTIVO, mapaAcciones);
                }
            }
        }
        else if (sensor.getTipo() == TipoSensor.BOMBA && nuevoEstado == EstadoSensor.INACTIVO) {
            forzarCierreValvulas(sectorId, mapaAcciones);
        }

        List<ActuatorActionDto> resultados = publicar(mapaAcciones);
        return new SensorDecisionResponseDto(true, "Comando correcto", resultados);
    }

    private void gestionarBomba(Long sectorId, EstadoSensor estado, Map<Long, EstadoSensor> acciones) {
        sensorRepository.findFirstBySectorIdAndTipo(sectorId, TipoSensor.BOMBA)
                .ifPresent(b -> acciones.put(b.getId(), estado));
    }

    private boolean isUltimaValvulaActiva(Long sectorId, Long valvulaActualId) {
        return sensorRepository.findBySectorIdAndTipo(sectorId, TipoSensor.ELECTROVALVULA).stream()
                .filter(v -> !v.getId().equals(valvulaActualId))
                .noneMatch(v -> v.getEstado() == EstadoSensor.ACTIVO);
    }

    private void forzarCierreValvulas(Long sectorId, Map<Long, EstadoSensor> acciones) {
        sensorRepository.findBySectorIdAndTipo(sectorId, TipoSensor.ELECTROVALVULA)
                .forEach(v -> acciones.put(v.getId(), EstadoSensor.INACTIVO));
    }

    private List<ActuatorActionDto> publicar(Map<Long, EstadoSensor> acciones) {
        List<ActuatorActionDto> historial = new ArrayList<>();
        acciones.forEach((id, estado) -> {
            sensorRepository.findById(id).ifPresent(s -> {
                s.setEstado(estado);
                sensorRepository.save(s);

                if (s.getTopicMQTTAct() != null && !s.getTopicMQTTAct().isEmpty()) {
                    String comando = (estado == EstadoSensor.ACTIVO) ? "0" : "1";
                    mqttService.publish(s.getTopicMQTTAct(), comando);
                }
                historial.add(new ActuatorActionDto(id, estado));
            });
        });
        return historial;
    }
}
