package it.ausl.emergency.adapter.physical;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.ausl.emergency.manager.PatientTwinManager;
import it.ausl.emergency.model.payload.PatientTelemetryPayload;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * MQTT Subscriber listening to the wildcard topic "ces/patient/+/state".
 * Extracts the agentId from the topic, deserializes the JSON payload, and delegates
 * the Digital Twin lifecycle management to the PatientTwinManager.
 */
public class PatientMqttIngestionAdapter implements MqttCallback {

    private static final String TOPIC_WILDCARD = "ces/patient/+/state";
    private static final int QOS = 1;

    private final String brokerUrl;
    private final PatientTwinManager twinManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private MqttClient client;

    public PatientMqttIngestionAdapter(String brokerUrl, PatientTwinManager twinManager) {
        this.brokerUrl = brokerUrl;
        this.twinManager = twinManager;
    }

    public void start() throws MqttException {
        client = new MqttClient(brokerUrl, "ces-dt-ingestion-" + System.currentTimeMillis(),
                new MemoryPersistence());
        client.setCallback(this);

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);

        client.connect(opts);
        client.subscribe(TOPIC_WILDCARD, QOS);

        System.out.println("[PatientMqttIngestionAdapter] Connected to " + brokerUrl
                + " — listening on: " + TOPIC_WILDCARD);
    }

    public void stop() throws MqttException {
        if (client != null && client.isConnected()) {
            client.disconnect();
            client.close();
        }
    }

    /**
     * Utility method to expose the raw data processing loop to JUnit test suites
     * without requiring an active network connection to a physical MQTT broker.
     */
    public void injectMessageArrived(String topic, String jsonPayload) throws Exception {
        MqttMessage mockMessage = new MqttMessage(jsonPayload.getBytes());
        this.messageArrived(topic, mockMessage);
    }

    // ── MqttCallback Implementation ──────────────────────────────────────────

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            // Contract decomposition: "ces/patient/P-14523/state" -> segments[2] = "P-14523"
            String[] segments = topic.split("/");
            if (segments.length < 4) {
                System.err.println("[PatientMqttIngestionAdapter] Unexpected topic format: " + topic);
                return;
            }
            String agentId = segments[2];

            PatientTelemetryPayload payload = mapper.readValue(
                    message.getPayload(), PatientTelemetryPayload.class);

            twinManager.onTelemetryReceived(agentId, payload);

        } catch (Exception e) {
            System.err.println("[PatientMqttIngestionAdapter] Failed to parse message on topic "
                    + topic + ": " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("[PatientMqttIngestionAdapter] Connection lost: " + cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) { 
        // Subscriber-only mode: unused
    }
}