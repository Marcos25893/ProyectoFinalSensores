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

        if (sensor.getTipo() == TipoSensor.ELECTROVALVULA) {

            if (nuevoEstado == EstadoSensor.ENCENDIDO) {
                gestionarBomba(sectorId, EstadoSensor.ENCENDIDO, mapaAcciones);
            }
            else if (nuevoEstado == EstadoSensor.APAGADO) {
                if (isUltimaValvulaActiva(sectorId, idActuador)) {
                    gestionarBomba(sectorId, EstadoSensor.APAGADO, mapaAcciones);
                }
            }
        }
        else if (sensor.getTipo() == TipoSensor.BOMBA && nuevoEstado == EstadoSensor.APAGADO) {
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
                .noneMatch(v -> v.getEstado() == EstadoSensor.ENCENDIDO);
    }

    private void forzarCierreValvulas(Long sectorId, Map<Long, EstadoSensor> acciones) {
        sensorRepository.findBySectorIdAndTipo(sectorId, TipoSensor.ELECTROVALVULA)
                .forEach(v -> acciones.put(v.getId(), EstadoSensor.APAGADO));
    }

    private List<ActuatorActionDto> publicar(Map<Long, EstadoSensor> acciones) {
        List<ActuatorActionDto> historial = new ArrayList<>();
        acciones.forEach((id, estado) -> {
            sensorRepository.findById(id).ifPresent(s -> {
                s.setEstado(estado);
                sensorRepository.save(s);

                if (s.getTopicMQTTAct() != null && !s.getTopicMQTTAct().isEmpty()) {
                    String comando = (estado == EstadoSensor.ENCENDIDO) ? "1" : "0";
                    mqttService.publish(s.getTopicMQTTAct(), comando);
                }
                historial.add(new ActuatorActionDto(id, estado));
            });
        });
        return historial;
    }
}
