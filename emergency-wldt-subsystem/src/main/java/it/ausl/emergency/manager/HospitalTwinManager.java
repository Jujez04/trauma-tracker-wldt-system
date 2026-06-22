package it.ausl.emergency.manager;

import it.ausl.emergency.payload.HospitalTelemetryPayload;
import it.ausl.emergency.shadowing.HospitalShadowingFunction;
import it.ausl.emergency.twin.HospitalDigitalTwin;
import it.wldt.core.engine.DigitalTwinEngine;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory e registry dei Digital Twin dell'Ospedale.
 * * Responsabilità:
 * - Creare un nuovo HospitalDigitalTwin alla notifica di boot o telemetria sconosciuta
 * - Smistare i dati aggregati al Physical Adapter del twin corretto
 * - Isolare il core di WLDT dalla logica dei protocolli di rete esterni (MQTT)
 */
public class HospitalTwinManager {

    private final DigitalTwinEngine engine;

    // Registro thread-safe per la gestione concorrente delle istanze ospedaliere
    private final ConcurrentHashMap<String, HospitalDigitalTwin> registry = 
            new ConcurrentHashMap<>();

    public HospitalTwinManager(DigitalTwinEngine engine) {
        this.engine = engine;
    }

    /**
     * Invocato dall'adapter alla ricezione di un comando di CREATED su ces/registry/hospital
     */
    public synchronized void onHospitalCreated(String agentId, int assistanceLevel) {
        registry.computeIfAbsent(agentId, id -> {
            try {
                System.out.println("[HospitalTwinManager] Rilevato boot ospedale: " + id 
                        + " [Livello Assistenza Primario: " + assistanceLevel + "] — creo HospitalDigitalTwin...");

                HospitalShadowingFunction sf = new HospitalShadowingFunction("hospital-shadowing-" + id);
                HospitalDigitalTwin twin = new HospitalDigitalTwin("dt-" + id, sf);

                // Configura il livello di assistenza di default impostandolo nella configurazione dell'adapter
                twin.getPhysicalAdapter().getConfiguration().setDefaultAssistanceLevel(assistanceLevel);

                engine.addDigitalTwin(twin);
                engine.startDigitalTwin("dt-" + id);

                System.out.println("[HospitalTwinManager] HospitalDigitalTwin avviato con successo per: " + id);
                return twin;
            } catch (Exception e) {
                throw new RuntimeException("Creazione core fallita per l'ospedale: " + id, e);
            }
        });
    }

    public void onTelemetryReceived(String agentId, HospitalTelemetryPayload payload) {
        // Se l'ospedale non è ancora a registro (fallback di sicurezza), lo istanzia on-the-fly
        HospitalDigitalTwin twin = registry.computeIfAbsent(agentId, id -> {
            try {
                System.out.println("[HospitalTwinManager] Fallback telemetrico per ospedale non registrato: " + id);
                HospitalShadowingFunction sf = new HospitalShadowingFunction("hospital-shadowing-" + id);
                HospitalDigitalTwin t = new HospitalDigitalTwin("dt-" + id, sf);

                engine.addDigitalTwin(t);
                engine.startDigitalTwin("dt-" + id);
                return t;
            } catch (Exception e) {
                throw new RuntimeException("Creazione di fallback fallita per l'ospedale: " + id, e);
            }
        });

        // Inoltra il payload tipizzato primitivo al rispettivo Configurable Physical Adapter
        twin.getPhysicalAdapter().onHospitalTelemetryReceived(payload);
    }

    public int activeHospitalCount() {
        return registry.size();
    }

    public HospitalDigitalTwin getHospital(String agentId) {
        return registry.get(agentId);
    }
}