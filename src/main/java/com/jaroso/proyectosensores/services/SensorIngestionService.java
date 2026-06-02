package com.jaroso.proyectosensores.services;

import com.jaroso.proyectosensores.entities.Lectura;
import com.jaroso.proyectosensores.entities.OrigenLectura;
import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.entities.TipoSensor;
import com.jaroso.proyectosensores.repositories.LecturaRepository;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Servicio centralizado de ingesta de lecturas de sensores.
 * Aplica las fórmulas de conversión originales y evita código duplicado.
 */
@Service
public class SensorIngestionService {

    private final Logger logger = Logger.getLogger(SensorIngestionService.class.getName());

    @Autowired
    private LecturaRepository lecturaRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private RiegoAutomaticoService riegoAutomaticoService;

    @Autowired
    private RiegoAutomaticoHumedadService riegoAutomaticoHumedadService;

    /**
     * Punto de entrada principal para procesar y almacenar cualquier lectura.
     */
    public void ingest(long sensorId, TipoSensor tipo, String rawValue, OrigenLectura origen) {
        switch (tipo) {
            case HUMEDAD      -> procesarHumedad(sensorId, rawValue, origen);
            case NIVEL        -> procesarNivel(sensorId, rawValue, origen);
            case PRESION      -> procesarPresion(sensorId, rawValue, origen);
            case CAUDAL       -> procesarCaudal(sensorId, rawValue, origen);
            case BOMBA, ELECTROVALVULA -> procesarActuador(sensorId, rawValue, origen);
            default           -> logger.warning("Tipo de sensor sin procesador registrado: " + tipo);
        }
    }

    /**
     * Sobrecarga de conveniencia para mantener compatibilidad con MQTT local por defecto.
     */
    public void ingest(long sensorId, TipoSensor tipo, String rawValue) {
        ingest(sensorId, tipo, rawValue, OrigenLectura.MQTT);
    }

    private void procesarHumedad(long sensorId, String payload, OrigenLectura origen) {
        int valor;
        try {
            valor = Integer.parseInt(payload);
        } catch (Exception ex) {
            logger.warning("Error en lectura humedad [" + payload + "]: " + ex.getMessage());
            return;
        }

        var cadMin = 330;
        var cadMax = 715;
        var humedadRH = 100 - ((100.0 / (cadMax - cadMin)) * (valor - cadMin));

        if ((humedadRH > 100) || (humedadRH < 0)) {
            logger.info("No se guarda el valor de humedad fuera de rango: " + humedadRH);
        } else {
            saveLectura(humedadRH, sensorId, origen);
            riegoAutomaticoHumedadService.evaluarHumedad(sensorId, humedadRH);
        }
    }

    private void procesarNivel(long sensorId, String payload, OrigenLectura origen) {
        int valor;
        try {
            valor = Integer.parseInt(payload) - 100;
        } catch (Exception ex) {
            logger.warning("Error en lectura nivel [" + payload + "]: " + ex.getMessage());
            return;
        }

        var areaDm2 = 1.45 * 1.45;
        var capacidadLitros = 8.0;
        var volumenL = areaDm2 * (valor / 10.0);

        var litros = capacidadLitros - volumenL;
        double porcentaje = (litros / capacidadLitros) * 100.0;

        if (porcentaje < 0.0) porcentaje = 0.0;
        if (porcentaje > 100.0) porcentaje = 100.0;

        if (litros < 0 || litros > 8) {
            return;
        } else {
            saveLectura(porcentaje, sensorId, origen);
            riegoAutomaticoService.evaluarNivel(sensorId, porcentaje);
        }
    }

    private void procesarPresion(long sensorId, String payload, OrigenLectura origen) {
        double valorRaw;
        try {
            valorRaw = Double.parseDouble(payload) - 450;
        } catch (Exception ex) {
            logger.warning("Error en lectura presión [" + payload + "]: " + ex.getMessage());
            return;
        }

        if (valorRaw <= 0.0) valorRaw = 0.0;

        double convCadMv = 1;
        double convMvBar = 0.003;
        double presionKgfCm2 = valorRaw * convCadMv * convMvBar;

        if (presionKgfCm2 > 0.4) {
            logger.info("No se guarda el valor de presión fuera de rango: " + presionKgfCm2);
        } else {
            saveLectura(presionKgfCm2, sensorId, origen);
        }
    }

    private void procesarCaudal(long sensorId, String payload, OrigenLectura origen) {
        int valor = 0;
        try {
            valor = Integer.parseInt(payload);
        } catch (Exception ex) {
            logger.warning("Error en la lectura del caudal [" + payload + "]: " + ex.getMessage());
            return;
        }

        int caudalRawInt = valor - 100;
        double caudalLMin = 0.0;

        if (caudalRawInt <= 0) {
            caudalLMin = 0;
        } else {
            caudalLMin = (caudalRawInt / 75.0);
        }

        if ((caudalLMin > 10) || (caudalLMin < 0)) {
            logger.info("No se guarda el valor de caudal fuera de rango: " + caudalLMin);
        } else {
            saveLectura(caudalLMin, sensorId, origen);
        }
    }

    private void procesarActuador(long sensorId, String payload, OrigenLectura origen) {
        String normalizado = "";
        try {
            normalizado = String.valueOf(payload.toLowerCase().trim().charAt(0));
        } catch (Exception ex) {
            logger.warning("Error en lectura actuador [" + payload + "]: " + ex.getMessage());
            return;
        }

        double valorEstado = switch (normalizado) {
            case "o" -> payload.toLowerCase().trim().startsWith("on") ? 1.0 : 0.0;
            case "t", "1" -> 1.0;
            case "f", "0" -> 0.0;
            default -> -1.0;
        };

        if (valorEstado >= 0) {
            saveLectura(valorEstado, sensorId, origen);
        }
    }

    private void saveLectura(Double valor, long sensorId, OrigenLectura origen) {
        Optional<Sensor> sensor = sensorRepository.findById(sensorId);
        if (sensor.isEmpty()) {
            logger.info("Sensor incorrecto, no se puede grabar lectura: " + sensorId);
            return;
        }

        Lectura lectura = new Lectura();
        lectura.setValor(valor);
        lectura.setFechaHora(LocalDateTime.now());
        lectura.setSensor(sensor.get());
        lectura.setOrigen(origen);
        lecturaRepository.save(lectura);
    }
}
