package it.ausl.emergency.adapter.physical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.ausl.emergency.manager.VehicleTwinManager;
import it.ausl.emergency.twin.AmbulanceDigitalTwin;
import it.ausl.emergency.twin.MedCarDigitalTwin;
import it.ausl.emergency.twin.MedHelicopterDigitalTwin;
import it.wldt.core.engine.DigitalTwin;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Ingestion Adapter dedicato alla flotta veicoli.
 * Ascolta i messaggi di registrazione strutturati e smista lo stato verso i Physical Adapter di WLDT.
 */
public class VehicleMqttIngestionAdapter implements MqttCallback {

    private static final String REGISTRY_TOPIC = "ces/registry";
    // Ascolta la telemetria di tutte le categorie di veicoli (ambulance, medcar, medhelicopter)
    private static final String VEHICLE_STATE_WILDCARD = "ces/+/+/state"; 
    private static final int QOS = 1;

    private final String brokerUrl;
    private final VehicleTwinManager twinManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private MqttClient client;

    public VehicleMqttIngestionAdapter(String brokerUrl, VehicleTwinManager twinManager) {
        this.brokerUrl = brokerUrl;
        this.twinManager = twinManager;
    }

    public void start() throws MqttException {
        client = new MqttClient(brokerUrl, "ces-vehicle-ingestion-" + System.currentTimeMillis(), new MemoryPersistence());
        client.setCallback(this);
        
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        
        client.connect(opts);
        
        // Doppia sottoscrizione: Logica di Creazione + Logica di Stato Telemetrico
        client.subscribe(REGISTRY_TOPIC, QOS);
        client.subscribe(VEHICLE_STATE_WILDCARD, QOS);
        
        System.out.println("[VehicleMqttIngestionAdapter] Ingestion attiva su broker: " + brokerUrl);
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
            String payloadString = new String(message.getPayload());
            JsonNode rootNode = mapper.readTree(payloadString);

            // CASO 1: Gestione Messaggio di Registrazione Unica (ces/registry)
            if (topic.equals(REGISTRY_TOPIC)) {
                String action = rootNode.path("action").asText();
                String type = rootNode.path("type").asText();
                String id = rootNode.path("id").asText();

                if ("CREATED".equalsIgnoreCase(action) && !type.equalsIgnoreCase("patient")) {
                    twinManager.onVehicleCreated(type, id);
                }
                return;
            }

            // CASO 2: Gestione Messaggi Telemetrici (ces/ambulance/AMB-01/state, ces/medcar/CAR-02/state, ecc.)
            if (topic.startsWith("ces/") && topic.endsWith("/state")) {
                String[] segments = topic.split("/");
                if (segments.length < 4) return;

                String vehicleType = segments[1]; // ambulance, medcar, medhelicopter
                String agentId = segments[2];     // AMB-XX, CAR-XX, HEL-XX

                DigitalTwin twin = twinManager.getVehicleTwin(agentId);
                if (twin == null) {
                    // Fallback di sicurezza: se la telemetria arriva prima del messaggio di registry, creiamo il twin on-the-fly
                    twinManager.onVehicleCreated(vehicleType, agentId);
                    twin = twinManager.getVehicleTwin(agentId);
                }

                if (twin != null) {
                    // Propaga l'aggiornamento telemetrico grezzo o deserializzato al rispettivo Physical Adapter interno
                    forwardTelemetryToAdapter(twin, vehicleType, payloadString);
                }
            }

        } catch (Exception e) {
            System.err.println("[VehicleMqttIngestionAdapter] Errore elaborazione messaggio su topic [" + topic + "]: " + e.getMessage());
        }
    }

    /**
     * Dispatch polimorfico verso lo specifico Physical Adapter del veicolo target.
     */
    private void forwardTelemetryToAdapter(DigitalTwin twin, String vehicleType, String rawJson) {
        // Supponendo che i tuoi Physical Adapter abbiano dei metodi pubblici esposti per iniettare i dati di simulazione 
        // (esattamente come nel tuo PatientTwinManager con twin.getPhysicalAdapter().onPatientTelemetryReceived(payload))
        try {
            switch (vehicleType.toLowerCase()) {
                case "ambulance":
                    AmbulanceDigitalTwin ambTwin = (AmbulanceDigitalTwin) twin;
                    // TODO: Integra con il reale metodo del tuo AmbulancePhysicalAdapter, ad esempio:
                    // ambTwin.getPhysicalAdapter().onVehicleTelemetryReceived(rawJson);
                    break;
                case "medcar":
                    MedCarDigitalTwin carTwin = (MedCarDigitalTwin) twin;
                    // carTwin.getPhysicalAdapter().onVehicleTelemetryReceived(rawJson);
                    break;
                case "medhelicopter":
                    MedHelicopterDigitalTwin helTwin = (MedHelicopterDigitalTwin) twin;
                    // helTwin.getPhysicalAdapter().onVehicleTelemetryReceived(rawJson);
                    break;
            }
        } catch (Exception e) {
            System.err.println("[VehicleMqttIngestionAdapter] Errore nel routing verso l'adapter fisico per: " + vehicleType);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("[VehicleMqttIngestionAdapter] Connessione persa con il broker MQTT: " + cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}
}