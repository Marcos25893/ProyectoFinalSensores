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

                    logger.info("Suscribiéndose a iot/sensor/1/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/1/")
                            .callback(msg -> procesarTemperatura(msg, 1))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/2/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/2/")
                            .callback(msg -> procesarTemperatura(msg, 2))
                            .send();

                    logger.info("Suscribiéndose a iot/sensor/3/");
                    client.subscribeWith()
                            .topicFilter("iot/sensor/3/")
                            .callback(msg -> procesarHumedad(msg, 3))
                            .send();

                })
                .exceptionally(throwable -> {
                    logger.severe("Error conectando al broker MQTT: " + throwable.getMessage());
                    //throwable.printStackTrace();
                    return null;
                });
    }

    private void procesarTemperatura(Mqtt3Publish msg, long sensorId) {
        logger.info("Recibiendo mensaje temperatura de: " + msg.getTopic());
        String payload = new String(msg.getPayloadAsBytes());
        //Convertir dato a lo que necesitamos
        JsonNode json = objectMapper.readTree(payload);
        double valor = json.get("valor").asDouble();

        //Guardar la lectura en BBDD
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

    /**
     * Guarda una lectura en la BBDD
     * @param valor
     * @param sensorId
     */
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
