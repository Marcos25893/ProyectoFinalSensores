package com.jaroso.proyectosensores.services;

import com.jaroso.proyectosensores.entities.EstadoSensor;
import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.entities.TipoSensor;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RiegoAutomaticoService {
    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    @Lazy
    private GestionRiegoService gestionRiegoService;

    public void evaluarNivel(Long idSensorNivel, Double valorActual) {
        Map<Long, Long> mapeoValvula = Map.of(
                8L, 2L,
                9L, 3L,
                10L, 4L
        );

        Long idValvulaLlenado = mapeoValvula.get(idSensorNivel);
        if (idValvulaLlenado == null) return;

        sensorRepository.findById(idSensorNivel).ifPresent(sensorNivel -> {
            sensorRepository.findById(idValvulaLlenado).ifPresent(valvula -> {

                Integer min = sensorNivel.getValorMin();
                Integer max = sensorNivel.getValorMax();
                boolean estaAbierta = (valvula.getEstado() == EstadoSensor.ACTIVO);

                if (min != null && valorActual <= min && !estaAbierta) {
                    gestionRiegoService.procesarCambioEstado(idValvulaLlenado, EstadoSensor.ACTIVO);
                }
                else if (max != null && valorActual >= max && estaAbierta) {
                    gestionRiegoService.procesarCambioEstado(idValvulaLlenado, EstadoSensor.INACTIVO);
                }
            });
        });
    }
}
