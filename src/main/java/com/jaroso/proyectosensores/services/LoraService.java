package com.jaroso.proyectosensores.services;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.jaroso.proyectosensores.entities.OrigenLectura;
import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class LoraService {

    private final Logger logger = Logger.getLogger(LoraService.class.getName());

    private final boolean enabled;
    private final String host;
    private final int port;
    private final String topic;
    private final boolean tlsEnabled;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;
    private final SensorRepository sensorRepository;
    private final SensorIngestionService sensorIngestionService;

    private Mqtt3AsyncClient client;

    public LoraService(
            @Value("${mqtt.lora.enabled:false}") boolean enabled,
            @Value("${mqtt.lora.host:10.0.0.12}") String host,
            @Value("${mqtt.lora.port:1883}") int port,
            @Value("${mqtt.lora.topic:lora/up}") String topic,
            @Value("${mqtt.lora.tls-enabled:false}") boolean tlsEnabled,
            @Value("${mqtt.lora.username:}") String username,
            @Value("${mqtt.lora.password:}") String password,
            ObjectMapper objectMapper,
            SensorRepository sensorRepository,
            SensorIngestionService sensorIngestionService
    ) {
        this.enabled = enabled;
        this.host = (host == null || host.isBlank()) ? "10.0.0.12" : host;
        this.port = port;
        this.topic = topic;
        this.tlsEnabled = tlsEnabled;
        this.username = username;
        this.password = password;
        this.objectMapper = objectMapper;
        this.sensorRepository = sensorRepository;
        this.sensorIngestionService = sensorIngestionService;
    }

    @PostConstruct
    public void subscribe() {
        if (!enabled) {
            logger.info("LoraMqttSubscriberService deshabilitado (mqtt.lora.enabled=false)");
            return;
        }

        if (topic == null || topic.isBlank()) {
            logger.warning("Topic LoRa vacío. Configura mqtt.lora.topic para poder suscribirte.");
            return;
        }

        final String brokerHost = Objects.requireNonNull(host);

        Mqtt3ClientBuilder builder = Mqtt3Client.builder()
                .identifier("spring-lora-subscriber-" + UUID.randomUUID())
                .serverHost(brokerHost)
                .serverPort(port);

        if (tlsEnabled) {
            builder = builder.sslWithDefaultConfig();
        }

        client = builder.buildAsync();

        logger.info("Conectando al broker MQTT LoRa en " + host + ":" + port + "...");

        connectClient()
                .thenCompose(connAck -> {
                    logger.info("Conexión MQTT LoRa exitosa. Suscribiendo a topic: " + topic);
                    return client.subscribeWith()
                            .topicFilter(topic)
                            .callback(this::onMessage)
                            .send();
                })
                .thenAccept(subAck -> logger.info("Suscripción LoRa activa en topic: " + topic))
                .exceptionally(throwable -> {
                    logger.severe("Error conectando/suscribiendo MQTT LoRa: " + throwable.getMessage());
                    return null;
                });
    }

    private java.util.concurrent.CompletableFuture<?> connectClient() {
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            logger.info("Conectando con autenticación de usuario MQTT LoRa");
            return client.connectWith()
                    .simpleAuth()
                    .username(username)
                    .password(password.getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                    .send();
        }
        return client.connect();
    }

    private void onMessage(com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish msg) {
        String rawPayload = new String(msg.getPayloadAsBytes(), StandardCharsets.UTF_8).trim();
        Optional<JsonNode> decodedOpt = extractDecodedPayload(rawPayload);

        if (decodedOpt.isEmpty()) {
            logger.warning("Mensaje LoRa sin decoded_payload | topic=" + msg.getTopic());
            return;
        }

        JsonNode decoded = decodedOpt.get();

        for (String key : decoded.propertyNames()) {
            String value = decoded.get(key).asText();

            Optional<Sensor> sensorOpt = sensorRepository.findByNombre(key);
            if (sensorOpt.isEmpty()) {
                logger.warning("LoRa | key=" + key + " | sin sensor en BD, se ignora");
                continue;
            }

            Sensor sensor = sensorOpt.get();
            sensorIngestionService.ingest(sensor.getId(), sensor.getTipo(), value, OrigenLectura.LORA);
        }
    }

    private Optional<JsonNode> extractDecodedPayload(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);

            JsonNode fromTtn = root.path("uplink_message").path("decoded_payload");
            if (!fromTtn.isMissingNode() && !fromTtn.isNull()) {
                return Optional.of(fromTtn);
            }

            JsonNode fromRoot = root.path("decoded_payload");
            if (!fromRoot.isMissingNode() && !fromRoot.isNull()) {
                return Optional.of(fromRoot);
            }

            return Optional.empty();
        } catch (Exception ex) {
            logger.warning("Payload LoRa no es JSON válido: " + ex.getMessage());
            return Optional.empty();
        }
    }

    @PreDestroy
    public void disconnect() {
        if (client != null) {
            client.disconnect();
        }
    }
}
