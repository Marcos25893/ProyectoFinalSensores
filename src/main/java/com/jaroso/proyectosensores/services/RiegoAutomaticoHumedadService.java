package com.jaroso.proyectosensores.services;

import com.jaroso.proyectosensores.entities.EstadoSensor;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RiegoAutomaticoHumedadService {
    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    @Lazy
    private GestionRiegoService gestionRiegoService;

    @Autowired
    private ModoHumedadService modoHumedadService;

    public void evaluarHumedad(Long idSensorHumedad, Double valorActual) {

        if (!modoHumedadService.isAutomatico()) {
            return;
        }

        Map<Long, Long> mapeoValvulas = Map.of(
                14L, 10L,
                15L, 11L,
                21L, 17L,
                22L, 18L,
                28L, 24L,
                29L, 25L
        );

        Long idValvula = mapeoValvulas.get(idSensorHumedad);
        if (idValvula == null) {
            return;
        }

        sensorRepository.findById(idSensorHumedad).ifPresent(sensorHumedad -> {
            sensorRepository.findById(idValvula).ifPresent(valvula -> {

                Integer min = sensorHumedad.getValorMin();
                Integer max = sensorHumedad.getValorMax();

                boolean estaAbierta =
                        valvula.getEstado() == EstadoSensor.ACTIVO;

                if (min != null && valorActual <= min && !estaAbierta) {
                    gestionRiegoService.procesarCambioEstado(
                            idValvula,
                            EstadoSensor.ACTIVO
                    );
                }
                else if (max != null && valorActual >= max && estaAbierta) {
                    gestionRiegoService.procesarCambioEstado(
                            idValvula,
                            EstadoSensor.INACTIVO
                    );
                }
            });
        });
    }
}
