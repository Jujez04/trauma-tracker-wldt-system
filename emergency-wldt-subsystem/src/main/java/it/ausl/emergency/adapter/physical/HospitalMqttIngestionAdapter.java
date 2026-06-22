package it.ausl.emergency.adapter.physical;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.ausl.emergency.manager.HospitalTwinManager;
import it.ausl.emergency.payload.HospitalTelemetryPayload;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MQTT Subscriber in ascolto sui topic dell'infrastruttura ospedaliera flotta.
 * Sottoscrive "ces/registry/hospital" e "ces/hospital/+/state".
 * Implementa una logica di sanificazione stringa per ovviare ai refusi di virgolette del simulatore.
 */
public class HospitalMqttIngestionAdapter implements MqttCallback {

    private static final String TOPIC_REGISTRY = "ces/registry/hospital";
    private static final String TOPIC_STATE_WILDCARD = "ces/hospital/+/state";
    private static final int QOS = 1;

    private final String brokerUrl;
    private final HospitalTwinManager twinManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private MqttClient client;

    // Pattern Regex per estrarre in sicurezza i campi dal JSON malformato del messaggio di boot registry
    private final Pattern registryIdPattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private final Pattern registryLevelPattern = Pattern.compile("\"assistanceLevel\"\\s*:\\s*\"?(\\d+)\"?");

    public HospitalMqttIngestionAdapter(String brokerUrl, HospitalTwinManager twinManager) {
        this.brokerUrl = brokerUrl;
        this.twinManager = twinManager;
    }

    public void start() throws MqttException {
        client = new MqttClient(brokerUrl, "ces-hospital-ingestion-" + System.currentTimeMillis(),
                new MemoryPersistence());
        client.setCallback(this);
        
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        
        client.connect(opts);
        client.subscribe(TOPIC_REGISTRY, QOS);
        client.subscribe(TOPIC_STATE_WILDCARD, QOS);
        
        System.out.println("[HospitalMqttIngestionAdapter] Connesso a " + brokerUrl
                + " — In ascolto su registry e stati telemetrici ospedali.");
    }

    public void stop() throws MqttException {
        if (client != null && client.isConnected()) {
            client.disconnect();
            client.close();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payloadRaw = new String(message.getPayload());

            // ── FLUSSO 1: Gestione Registrazione Unica (ces/registry/hospital) ──────
            if (topic.equals(TOPIC_REGISTRY)) {
                Matcher idMatcher = registryIdPattern.matcher(payloadRaw);
                Matcher levelMatcher = registryLevelPattern.matcher(payloadRaw);

                if (idMatcher.find() && levelMatcher.find()) {
                    String agentId = idMatcher.group(1);
                    int assistanceLevel = Integer.parseInt(levelMatcher.group(1));
                    
                    twinManager.onHospitalCreated(agentId, assistanceLevel);
                } else {
                    System.err.println("[HospitalMqttIngestionAdapter] Payload di boot non interpretabile: " + payloadRaw);
                }
                return;
            }

            // ── FLUSSO 2: Gestione Telemetria Periodica (ces/hospital/HOS-XX/state) ──
            if (topic.startsWith("ces/hospital/") && topic.endsWith("/state")) {
                String[] segments = topic.split("/");
                if (segments.length < 4) return;
                String agentId = segments[2];

                // Sanificazione del refuso AnyLogic: trasforma es. "assistanceLevel":2", in "assistanceLevel":2,
                // Il regex intercetta la chiave, cattura i numeri dell'int primitivo e rimuove la virgoletta isolata successiva
                String payloadSanitized = payloadRaw.replaceAll(
                        "\"assistanceLevel\"\\s*:\\s*(\\d+)\"\\s*,", 
                        "\"assistanceLevel\":$1,"
                );

                HospitalTelemetryPayload payload = mapper.readValue(
                        payloadSanitized, 
                        HospitalTelemetryPayload.class
                );
                
                twinManager.onTelemetryReceived(agentId, payload);
            }

        } catch (Exception e) {
            System.err.println("[HospitalMqttIngestionAdapter] Errore nell'elaborazione del messaggio sul topic " 
                    + topic + ": " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("[HospitalMqttIngestionAdapter] Connessione persa: " + cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Modalità unicamente Subscriber: inutilizzato
    }
}