package com.jaroso.proyectosensores.services;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.jaroso.proyectosensores.entities.Lectura;
import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.repositories.LecturaRepository;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

                    logger.info("Suscribiéndose a iot/sensor/1/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/1/")
                            .callback(msg -> procesarElectrovalvula(msg, 1))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/2/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/2/")
                            .callback(msg -> procesarElectrovalvula(msg, 2))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/3/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/3/")
                            .callback(msg -> procesarBomba(msg, 3))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/4/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/4/")
                            .callback(msg -> procesarHumedad(msg, 4))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/5/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/5/")
                            .callback(msg -> procesarCaudalimetro(msg, 5))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/6/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/6/")
                            .callback(msg -> procesarPresion(msg, 6))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/7/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/7/")
                            .callback(msg -> procesarPulsador(msg, 7))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/8/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/8/")
                            .callback(msg -> procesarVolumen(msg, 8))
                            .send();

                })
                .exceptionally(throwable -> {
                    logger.severe("Error conectando al broker MQTT: " + throwable.getMessage());
                    //throwable.printStackTrace();
                    return null;
                });
    }

    private void procesarElectrovalvula(Mqtt3Publish msg, long sensorId) {
        logger.info("Recibiendo mensaje humedad de: " + msg.getTopic());
        String payload = new String(msg.getPayloadAsBytes());
        JsonNode json = objectMapper.readTree(payload);
        double valor = json.get("valor").asDouble();

        saveLectura(valor, sensorId);
    }

    private void procesarBomba(Mqtt3Publish msg, long sensorId) {
        logger.info("Recibiendo mensaje humedad de: " + msg.getTopic());
        String payload = new String(msg.getPayloadAsBytes());
        JsonNode json = objectMapper.readTree(payload);
        double valor = json.get("valor").asDouble();

        saveLectura(valor, sensorId);
    }

    private void procesarHumedad(Mqtt3Publish msg, long sensorId) {
        logger.info("Recibiendo mensaje humedad de: " + msg.getTopic());
        String payload = new String(msg.getPayloadAsBytes());
        //Convertir dato a lo que necesitamos
        JsonNode json = objectMapper.readTree(payload);
        double valor = json.get("valor").asDouble();

        //Guardar la lectura en BBDD
        saveLectura(valor, sensorId);
    }

    private void procesarCaudalimetro(Mqtt3Publish msg, long sensorId) {
        logger.info("Recibiendo mensaje humedad de: " + msg.getTopic());
        String payload = new String(msg.getPayloadAsBytes());
        JsonNode json = objectMapper.readTree(payload);
        double valor = json.get("valor").asDouble();

        saveLectura(valor, sensorId);
    }

    private void procesarPresion(Mqtt3Publish msg, long sensorId) {
        logger.info("Recibiendo mensaje humedad de: " + msg.getTopic());
        String payload = new String(msg.getPayloadAsBytes());
        JsonNode json = objectMapper.readTree(payload);
        double valor = json.get("valor").asDouble();

        saveLectura(valor, sensorId);
    }

    private void procesarPulsador(Mqtt3Publish msg, long sensorId) {
        logger.info("Recibiendo mensaje humedad de: " + msg.getTopic());
        String payload = new String(msg.getPayloadAsBytes());
        JsonNode json = objectMapper.readTree(payload);
        double valor = json.get("valor").asDouble();

        saveLectura(valor, sensorId);
    }

    private void procesarVolumen(Mqtt3Publish msg, long sensorId) {
        logger.info("Recibiendo mensaje humedad de: " + msg.getTopic());
        String payload = new String(msg.getPayloadAsBytes());
        JsonNode json = objectMapper.readTree(payload);
        double valor = json.get("valor").asDouble();

        saveLectura(valor, sensorId);
    }


    private void saveLectura(Double valor, long sensorId) {
        Lectura lectura = new Lectura();
        lectura.setValor(valor);
        Optional<Sensor> sensor = sensorRepository.findById(sensorId);
        if (sensor.isEmpty()) {
            logger.info("Sensor incorrecto, no se puede grabar lectura: " + sensorId);
            return;
        } else {
            lectura.setSensor(sensor.get());
            lecturaRepository.save(lectura);
        }
    }

}
