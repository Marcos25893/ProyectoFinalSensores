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

    private final Mqtt3AsyncClient client;
    private final String host;
    private final int port;

    Logger logger = Logger.getLogger(MqttService.class.getName());

    public MqttService(@Value("${mqtt.host:184.73.39.231}") String host,
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
        } else if (tipo == TipoSensor.NIVEL || tipo == TipoSensor.PRESION || tipo == TipoSensor.TEMPERATURA ||
                tipo == TipoSensor.CONDUCTIVIDAD || tipo == TipoSensor.PRESION_EXTERNA) {
            procesarNivelPresion(mensaje, sensorId);
        } else if (tipo == TipoSensor.BOMBA || tipo == TipoSensor.ELECTROVALVULA || tipo == TipoSensor.EV_NUTRIENTES) {
            procesarActuador(mensaje, sensorId);
        }
    }

    private void procesarCaudal(Mqtt3Publish mensaje, long sensorId) {
        logger.info("Recibiendo mensaje presion/nivel de: " + mensaje.getTopic());
        String contenido = new String(mensaje.getPayloadAsBytes(), StandardCharsets.UTF_8).trim();
        double pulsos = Double.parseDouble(contenido);
        double caudalLMin = pulsos / 75.0;
        saveLectura(caudalLMin, sensorId);
    }

    private void procesarNivelPresion(Mqtt3Publish mensaje, long sensorId) {
        logger.info("Recibiendo mensaje presion/nivel de: " + mensaje.getTopic());
        String contenido = new String(mensaje.getPayloadAsBytes());
        double valor = Double.parseDouble(contenido);
        saveLectura(valor, sensorId);
    }

    private void procesarHumedad(Mqtt3Publish mensaje, long sensorId) {
        logger.info("Recibiendo mensaje humedad de: " + mensaje.getTopic());
        String contenido = new String(mensaje.getPayloadAsBytes());
        contenido = contenido.substring(0, contenido.length() - 1);
        double valor = Double.parseDouble(contenido);
        saveLectura(valor, sensorId);
    }

    private void procesarActuador(Mqtt3Publish mensaje, long sensorId) {
        logger.info("Recibiendo mensaje actuador de: " + mensaje.getTopic());
        String estadoRaw = new String(mensaje.getPayloadAsBytes(), StandardCharsets.UTF_8).trim().toLowerCase();

        double valorEstado = switch (estadoRaw) {
            case "on", "true", "1" -> 1.0;
            case "off", "false", "0" -> 0.0;
            default -> 0.0;
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