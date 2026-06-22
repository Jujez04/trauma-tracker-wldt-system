package it.ausl.emergency.adapter.physical;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.ausl.emergency.adapter.configuration.MedHelicopterAdapterConfiguration;
import it.ausl.emergency.payload.MedHelicopterTelemetryPayload;
import it.ausl.emergency.utils.MedHelicopterKeywords;
import it.wldt.adapter.physical.ConfigurablePhysicalAdapter;
import it.wldt.adapter.physical.PhysicalAssetDescription;
import it.wldt.adapter.physical.PhysicalAssetEvent;
import it.wldt.adapter.physical.PhysicalAssetProperty;
import it.wldt.adapter.physical.event.PhysicalAssetActionWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetEventWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.exception.EventBusException;

/**
 * Physical Adapter del MedHelicopter.
 *
 * Riceve i payload MQTT pubblicati su "ces/medhelicopter/{agentId}/state",
 * aggiorna le proprietà del DT e pubblica i Domain Events rilevando
 * fronti di salita/discesa rispetto alla telemetria precedente.
 *
 * Domain Events:
 *  1. MISSION_ASSIGNED    — atRest → MovingToPatient
 *  2. PATIENT_ONBOARD     — TakingPatient → MovingToHospital
 *  3. HOSPITAL_HANDOVER   — * → Handover
 *  4. CRITICAL_FUEL       — fuelLevel scende sotto 0.20 (fronte di discesa)
 *  5. MAINTENANCE_REQUIRED — fronte di salita su needsMaintenance
 *
 * Nessuna azione: il MedHelicopter non riceve comandi di redirect.
 */
public class MedHelicopterPhysicalAdapter
        extends ConfigurablePhysicalAdapter<MedHelicopterAdapterConfiguration> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Ultima telemetria ricevuta — serve per rilevare i fronti di stato. */
    private MedHelicopterTelemetryPayload lastTelemetry = null;

    public MedHelicopterPhysicalAdapter(String id, MedHelicopterAdapterConfiguration configuration) {
        super(id, configuration);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onAdapterStart() {
        try {
            System.out.println("[MedHelicopterPhysicalAdapter] -> Initializing PAD for MedHelicopter: " + getId());
            notifyPhysicalAdapterBound(buildPhysicalAssetDescription());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAdapterStop() {
        System.out.println("[MedHelicopterPhysicalAdapter] -> Adapter stopped: " + getId());
    }

    // ── PAD Builder ───────────────────────────────────────────────────────────

    private PhysicalAssetDescription buildPhysicalAssetDescription() {
        PhysicalAssetDescription pad = new PhysicalAssetDescription();

        // Properties — specchio 1:1 dei campi di MedHelicopterTelemetryPayload
        // più homeBase (configurato staticamente, non presente nel payload MQTT)
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.STATE_PROPERTY_KEY,
                getConfiguration().getDefaultState()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.LATITUDE_PROPERTY_KEY,
                getConfiguration().getDefaultLatitude()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.LONGITUDE_PROPERTY_KEY,
                getConfiguration().getDefaultLongitude()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.PATIENT_ID_PROPERTY_KEY,
                getConfiguration().getDefaultPatientId()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.HOSPITAL_ID_PROPERTY_KEY,
                getConfiguration().getDefaultHospitalId()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.HOME_BASE_PROPERTY_KEY,
                getConfiguration().getDefaultHomeBase()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.FUEL_LEVEL_PROPERTY_KEY,
                getConfiguration().getDefaultFuelLevel()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.MISSIONS_PROPERTY_KEY,
                getConfiguration().getDefaultMissionsSinceMaintenance()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.NEEDS_REFUELING_PROPERTY_KEY,
                getConfiguration().isDefaultNeedsRefueling()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY,
                getConfiguration().isDefaultNeedsMaintenance()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.TIMESTAMP_PROPERTY_KEY,
                getConfiguration().getDefaultTimestamp()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedHelicopterKeywords.TRIP_DISTANCE_PROPERTY_KEY,
                getConfiguration().getDefaultTripDistanceSinceEmergency()));

        // Domain Events
        pad.getEvents().add(new PhysicalAssetEvent(
                MedHelicopterKeywords.MISSION_ASSIGNED_EVENT_KEY,     "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(
                MedHelicopterKeywords.PATIENT_ONBOARD_EVENT_KEY,      "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(
                MedHelicopterKeywords.HOSPITAL_HANDOVER_EVENT_KEY,    "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(
                MedHelicopterKeywords.CRITICAL_FUEL_EVENT_KEY,        "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(
                MedHelicopterKeywords.MAINTENANCE_REQUIRED_EVENT_KEY, "application/json"));

        // Nessuna azione: il MedHelicopter non riceve comandi di redirect
        return pad;
    }

    // ── Telemetry Ingestion ───────────────────────────────────────────────────

    /**
     * Entry point per payload JSON grezzi (es. da un client MQTT reale).
     */
    public void onMedHelicopterJsonTelemetryReceived(String jsonPayload) {
        try {
            MedHelicopterTelemetryPayload payload =
                    objectMapper.readValue(jsonPayload, MedHelicopterTelemetryPayload.class);
            onMedHelicopterTelemetryReceived(payload);
        } catch (Exception e) {
            System.err.println("[MedHelicopterPhysicalAdapter] JSON parsing error: " + e.getMessage());
        }
    }

    /**
     * Entry point principale: riceve un payload già deserializzato
     * (usato direttamente nei test JUnit).
     */
    public void onMedHelicopterTelemetryReceived(MedHelicopterTelemetryPayload payload) {
        if (payload == null) return;

        try {
            // ── 1. Aggiornamento delle proprietà ──────────────────────────────
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.STATE_PROPERTY_KEY,
                    payload.state()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.LATITUDE_PROPERTY_KEY,
                    payload.lat()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.LONGITUDE_PROPERTY_KEY,
                    payload.lon()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.PATIENT_ID_PROPERTY_KEY,
                    payload.patientId() != null ? payload.patientId() : "null"));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.HOSPITAL_ID_PROPERTY_KEY,
                    payload.hospitalId() != null ? payload.hospitalId() : "null"));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.FUEL_LEVEL_PROPERTY_KEY,
                    payload.fuelLevel()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.MISSIONS_PROPERTY_KEY,
                    payload.missionsSinceMaintenance()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.NEEDS_REFUELING_PROPERTY_KEY,
                    payload.needsRefueling()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY,
                    payload.needsMaintenance()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.TIMESTAMP_PROPERTY_KEY,
                    payload.timestamp()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedHelicopterKeywords.TRIP_DISTANCE_PROPERTY_KEY,
                    payload.tripDistanceSinceEmergency()));

            // ── 2. Domain Events (rilevazione fronti) ─────────────────────────

            String prevState = lastTelemetry != null ? lastTelemetry.state() : null;
            String currState = payload.state();

            // Event 1 — Mission Assigned: atRest → MovingToPatient
            if (MedHelicopterKeywords.STATE_MOVING_TO_PATIENT.equals(currState)
                    && !MedHelicopterKeywords.STATE_MOVING_TO_PATIENT.equals(prevState)) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedHelicopterKeywords.MISSION_ASSIGNED_EVENT_KEY, payload));
                System.out.println("[MedHelicopterPhysicalAdapter] -> EVENT: MISSION_ASSIGNED");
            }

            // Event 2 — Patient Onboard: TakingPatient → MovingToHospital
            if (MedHelicopterKeywords.STATE_MOVING_TO_HOSPITAL.equals(currState)
                    && MedHelicopterKeywords.STATE_TAKING_PATIENT.equals(prevState)) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedHelicopterKeywords.PATIENT_ONBOARD_EVENT_KEY, payload));
                System.out.println("[MedHelicopterPhysicalAdapter] -> EVENT: PATIENT_ONBOARD");
            }

            // Event 3 — Hospital Handover: * → Handover (fronte di ingresso)
            if (MedHelicopterKeywords.STATE_HANDOVER.equals(currState)
                    && !MedHelicopterKeywords.STATE_HANDOVER.equals(prevState)) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedHelicopterKeywords.HOSPITAL_HANDOVER_EVENT_KEY, payload));
                System.out.println("[MedHelicopterPhysicalAdapter] -> EVENT: HOSPITAL_HANDOVER");
            }

            // Event 4 — Critical Fuel: fronte di discesa sotto soglia 0.20
            if (payload.fuelLevel() < MedHelicopterKeywords.CRITICAL_FUEL_THRESHOLD
                    && (lastTelemetry == null
                        || lastTelemetry.fuelLevel() >= MedHelicopterKeywords.CRITICAL_FUEL_THRESHOLD)) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedHelicopterKeywords.CRITICAL_FUEL_EVENT_KEY, payload));
                System.out.println("[MedHelicopterPhysicalAdapter] -> EVENT: CRITICAL_FUEL");
            }

            // Event 5 — Maintenance Required: fronte di salita su needsMaintenance
            if (payload.needsMaintenance()
                    && (lastTelemetry == null || !lastTelemetry.needsMaintenance())) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedHelicopterKeywords.MAINTENANCE_REQUIRED_EVENT_KEY, payload));
                System.out.println("[MedHelicopterPhysicalAdapter] -> EVENT: MAINTENANCE_REQUIRED");
            }

            lastTelemetry = payload;

        } catch (EventBusException e) {
            System.err.println("[MedHelicopterPhysicalAdapter] EventBus error: " + e.getMessage());
        }
    }

    // ── Action (no-op) ────────────────────────────────────────────────────────

    @Override
    public void onIncomingPhysicalAction(PhysicalAssetActionWldtEvent<?> event) {
        // Il MedHelicopter non espone azioni
        System.out.println("[MedHelicopterPhysicalAdapter] -> Unsupported action received: "
                + (event != null ? event.getActionKey() : "null"));
    }
}
