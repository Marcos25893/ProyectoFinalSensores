package com.jaroso.proyectosensores.services;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.jaroso.proyectosensores.entities.Lectura;
import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.entities.TipoSensor;
import com.jaroso.proyectosensores.repositories.LecturaRepository;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
@Service
public class MqttService {

    @Autowired
    private LecturaRepository lecturaRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RiegoAutomaticoService riegoAutomaticoService;

    private final Mqtt3AsyncClient client;
    private final String host;
    private final int port;

    Logger logger = Logger.getLogger(MqttService.class.getName());

    public MqttService(@Value("${mqtt.host:localhost}") String host,
                       @Value("${mqtt.port:1883}") int port) {
        this.host = host;
        this.port = port;
        client = Mqtt3Client.builder()
                .identifier("springSubscriber-" + UUID.randomUUID())
                .serverHost(host)
                .serverPort(port)
                .buildAsync();
    }

    public void publish(String topic, String payload) {
        logger.info("Publicando en " + topic + ": " + payload);
        client.publishWith()
                .topic(topic)
                .payload(payload.getBytes())
                .send();
    }

    @PostConstruct
    public void conectarYSuscribir() {
        logger.info("Conectando al broker MQTT en " + host + ":" + port + "...");

        client.connect()
                .thenAccept(connAck -> {
                    logger.info("Conexión exitosa al broker MQTT");

                    sensorRepository.findAll().forEach(sensor -> {
                        String topicMqtt = sensor.getTopicMQTT();

                        if (topicMqtt != null && !topicMqtt.isEmpty()) {
                            logger.info("Suscribiéndose a " + topicMqtt);

                            client.subscribeWith()
                                    .topicFilter(topicMqtt)
                                    .callback(mensaje -> derivarAMetodoProcesador(mensaje, sensor))
                                    .send();
                        }
                    });
                })
                .exceptionally(throwable -> {
                    logger.severe("Error conectando al broker MQTT: " + throwable.getMessage());
                    return null;
                });
    }

    private void derivarAMetodoProcesador(Mqtt3Publish mensaje, Sensor sensor) {
        TipoSensor tipo = sensor.getTipo();
        long sensorId = sensor.getId();

        if (tipo == TipoSensor.HUMEDAD || tipo == TipoSensor.HUMEDAD_EXTERNA) {
            procesarHumedad(mensaje, sensorId);
        } else if (tipo == TipoSensor.CAUDAL) {
            procesarCaudal(mensaje, sensorId);
        } else if (tipo == TipoSensor.NIVEL) {
            procesarNivel(mensaje, sensorId);
        } else if (tipo == TipoSensor.PRESION || tipo == TipoSensor.PRESION_EXTERNA) {
            procesarPresion(mensaje, sensorId);
        } else if (tipo == TipoSensor.TEMPERATURA || tipo == TipoSensor.CONDUCTIVIDAD) {
            procesarGenerico(mensaje, sensorId);
        } else if (tipo == TipoSensor.BOMBA || tipo == TipoSensor.ELECTROVALVULA || tipo == TipoSensor.EV_NUTRIENTES) {
            procesarActuador(mensaje, sensorId);
        }
    }

    private void procesarCaudal(Mqtt3Publish mensaje, long sensorId) {
        String payload = new String(mensaje.getPayloadAsBytes(), StandardCharsets.UTF_8).trim();
        int valor = 0;
        try {
            valor = Integer.parseInt(payload);
        } catch (Exception ex) {
            logger.warning("Error en la lectura del caudal " + payload);
            return;
        }

        logger.info("Recibiendo mensaje caudal de: " + mensaje.getTopic() + " con valor: " + valor);

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
            saveLectura(caudalLMin, sensorId);
        }
    }

    private void procesarNivel(Mqtt3Publish mensaje, long sensorId) {
        String payload = new String(mensaje.getPayloadAsBytes(), StandardCharsets.UTF_8).trim();
        logger.info("Recibiendo mensaje de sensor de nivel: " + mensaje.getTopic() + " con valor: " + payload);

        int valor = 0;
        try {
            valor = Integer.parseInt(payload) - 100;
        } catch (Exception ex) {
            logger.warning("Error en lectura nivel " + payload);
            return;
        }

        var areaDm2 = 1.45 * 1.45;
        var capacidadLitros = 8.0;
        var volumenL = areaDm2 * (valor / 10.0);

        var litros = capacidadLitros - volumenL;
        double porcentaje = (litros / capacidadLitros) * 100.0;

        if (porcentaje < 0.0) porcentaje = 0.0;
        if (porcentaje > 100.0) porcentaje = 100.0;

        logger.info("Nivel valor: " + valor + " con litros: " + litros + " con porcentaje " + porcentaje);

        if (litros < 0 || litros > 8) {
            return;
        } else {
            saveLectura(porcentaje, sensorId);
            riegoAutomaticoService.evaluarNivel(sensorId, porcentaje);
        }
    }

    private void procesarPresion(Mqtt3Publish mensaje, long sensorId) {
        String payload = new String(mensaje.getPayloadAsBytes(), StandardCharsets.UTF_8).trim();
        logger.info("Recibiendo mensaje de sensor de presion: " + mensaje.getTopic() + " con valor: " + payload);

        double valorRaw;
        try {
            valorRaw = Double.parseDouble(payload) - 450;
        } catch (Exception ex) {
            logger.warning("Error en lectura presión " + payload);
            return;
        }

        if (valorRaw <= 0.0) valorRaw = 0.0;

        double convCadMv = 1;
        double convMvBar = 0.003;
        double presionKgfCm2 = valorRaw * convCadMv * convMvBar;

        if (presionKgfCm2 > 0.4) {
            logger.info("No se guarda el valor de presión fuera de rango: " + presionKgfCm2);
        } else {
            saveLectura(presionKgfCm2, sensorId);
        }
    }

    private void procesarHumedad(Mqtt3Publish mensaje, long sensorId) {
        String payload = new String(mensaje.getPayloadAsBytes(), StandardCharsets.UTF_8).trim();
        logger.info("Recibiendo mensaje de sensor de humedad: " + mensaje.getTopic() + " con valor: " + payload);

        int valor = 0;
        try {
            valor = Integer.parseInt(payload);
        } catch (Exception ex) {
            logger.warning("Error en lectura humedad " + payload);
            return;
        }

        var cadMin = 330;
        var cadMax = 715;
        var humedadRH = 100 - ((100.0 / (cadMax - cadMin)) * (valor - cadMin));

        if ((humedadRH > 100) || (humedadRH < 0)) {
            logger.info("No se guarda el valor de humedad fuera de rango: " + humedadRH);
        } else {
            saveLectura(humedadRH, sensorId);
        }
    }

    private void procesarGenerico(Mqtt3Publish mensaje, long sensorId) {
        String contenido = new String(mensaje.getPayloadAsBytes(), StandardCharsets.UTF_8).trim();
        try {
            double valor = Double.parseDouble(contenido);
            saveLectura(valor, sensorId);
        } catch (Exception ex) {
            logger.warning("Error en lectura de sensor genérico " + contenido);
        }
    }

    private void procesarActuador(Mqtt3Publish mensaje, long sensorId) {
        logger.info("Recibiendo mensaje de sensor de actuador: " + mensaje.getTopic());
        String payload = new String(mensaje.getPayloadAsBytes(), StandardCharsets.UTF_8).trim();

        String normalizado = "";
        try {
            normalizado = String.valueOf(payload.toLowerCase().charAt(0));
        } catch (Exception ex) {
            logger.warning("Error en lectura actuador " + payload);
            return;
        }

        double valorEstado = switch (normalizado) {
            case "on", "true", "1" -> 1.0;
            case "off", "false", "0" -> 0.0;
            default -> throw new IllegalArgumentException("Payload de actuador no soportado: " + normalizado);
        };

        saveLectura(valorEstado, sensorId);
    }

    private void saveLectura(Double valor, long sensorId) {
        Lectura lectura = new Lectura();
        lectura.setValor(valor);
        lectura.setFechaHora(LocalDateTime.now());

        Optional<Sensor> sensor = sensorRepository.findById(sensorId);
        if (sensor.isEmpty()) {
            logger.info("Sensor incorrecto, no se puede grabar lectura: " + sensorId);
        } else {
            lectura.setSensor(sensor.get());
            lecturaRepository.save(lectura);
        }
    }
}