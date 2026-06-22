package it.ausl.emergency.adapter.physical;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.ausl.emergency.adapter.configuration.MedCarAdapterConfiguration;
import it.ausl.emergency.payload.MedCarTelemetryPayload;
import it.ausl.emergency.utils.MedCarKeywords;
import it.wldt.adapter.physical.ConfigurablePhysicalAdapter;
import it.wldt.adapter.physical.PhysicalAssetDescription;
import it.wldt.adapter.physical.PhysicalAssetEvent;
import it.wldt.adapter.physical.PhysicalAssetProperty;
import it.wldt.adapter.physical.event.PhysicalAssetActionWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetEventWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.exception.EventBusException;

public class MedCarPhysicalAdapter extends ConfigurablePhysicalAdapter<MedCarAdapterConfiguration> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Ultima telemetria ricevuta: serve per rilevare i fronti di stato
    private MedCarTelemetryPayload lastTelemetry = null;

    public MedCarPhysicalAdapter(String id, MedCarAdapterConfiguration configuration) {
        super(id, configuration);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onAdapterStart() {
        try {
            System.out.println("[MedCarPhysicalAdapter] -> Initializing PAD for MedCar: " + getId());
            notifyPhysicalAdapterBound(buildPhysicalAssetDescription());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAdapterStop() {
        System.out.println("[MedCarPhysicalAdapter] -> Adapter stopped: " + getId());
    }

    // ── PAD Builder ───────────────────────────────────────────────────────────

    private PhysicalAssetDescription buildPhysicalAssetDescription() {
        PhysicalAssetDescription pad = new PhysicalAssetDescription();

        // Properties (specchio 1:1 dei campi di MedCarTelemetryPayload)
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.STATE_PROPERTY_KEY,            getConfiguration().getDefaultState()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.LATITUDE_PROPERTY_KEY,         getConfiguration().getDefaultLatitude()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.LONGITUDE_PROPERTY_KEY,        getConfiguration().getDefaultLongitude()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.PATIENT_ID_PROPERTY_KEY,       getConfiguration().getDefaultPatientId()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.HOME_BASE_ID_PROPERTY_KEY,     getConfiguration().getDefaultHomeBaseId()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.FUEL_LEVEL_PROPERTY_KEY,       getConfiguration().getDefaultFuelLevel()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.MISSIONS_PROPERTY_KEY,         getConfiguration().getDefaultMissionsSinceMaintenance()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.NEEDS_REFUELING_PROPERTY_KEY,  getConfiguration().isDefaultNeedsRefueling()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY, getConfiguration().isDefaultNeedsMaintenance()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.TIMESTAMP_PROPERTY_KEY,        getConfiguration().getDefaultTimestamp()));
        pad.getProperties().add(new PhysicalAssetProperty<>(
                MedCarKeywords.TRIP_DISTANCE_PROPERTY_KEY,    getConfiguration().getDefaultTripDistanceSinceEmergency()));

        // Domain Events
        pad.getEvents().add(new PhysicalAssetEvent(
                MedCarKeywords.MISSION_ASSIGNED_EVENT_KEY,     "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(
                MedCarKeywords.ON_SCENE_TREATING_EVENT_KEY,    "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(
                MedCarKeywords.MISSION_COMPLETED_EVENT_KEY,    "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(
                MedCarKeywords.CRITICAL_FUEL_EVENT_KEY,        "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(
                MedCarKeywords.MAINTENANCE_REQUIRED_EVENT_KEY, "application/json"));

        // Nessuna azione: la MedCar non riceve comandi di redirect
        return pad;
    }

    // ── Telemetry Ingestion ───────────────────────────────────────────────────

    public void onMedCarJsonTelemetryReceived(String jsonPayload) {
        try {
            MedCarTelemetryPayload payload = objectMapper.readValue(jsonPayload, MedCarTelemetryPayload.class);
            onMedCarTelemetryReceived(payload);
        } catch (Exception e) {
            System.err.println("[MedCarPhysicalAdapter] JSON parsing error: " + e.getMessage());
        }
    }

    public void onMedCarTelemetryReceived(MedCarTelemetryPayload payload) {
        if (payload == null) return;

        try {
            // ── 1. Aggiornamento delle proprietà ──────────────────────────────
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.STATE_PROPERTY_KEY,            payload.state()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.LATITUDE_PROPERTY_KEY,         payload.lat()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.LONGITUDE_PROPERTY_KEY,        payload.lon()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.PATIENT_ID_PROPERTY_KEY,
                    payload.patientId() != null ? payload.patientId() : "null"));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.HOME_BASE_ID_PROPERTY_KEY,
                    payload.homeBaseId() != null ? payload.homeBaseId() : "null"));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.FUEL_LEVEL_PROPERTY_KEY,       payload.fuelLevel()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.MISSIONS_PROPERTY_KEY,         payload.missionsSinceMaintenance()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.NEEDS_REFUELING_PROPERTY_KEY,  payload.needsRefueling()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY, payload.needsMaintenance()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.TIMESTAMP_PROPERTY_KEY,        payload.timestamp()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                    MedCarKeywords.TRIP_DISTANCE_PROPERTY_KEY,    payload.tripDistanceSinceEmergency()));

            // ── 2. Domain Events (fronti di salita/discesa) ───────────────────

            String prevState = lastTelemetry != null ? lastTelemetry.state() : null;
            String currState = payload.state();

            // Mission Assigned: atRest → MovingToPatient
            if (MedCarKeywords.STATE_MOVING_TO_PATIENT.equals(currState)
                    && !MedCarKeywords.STATE_MOVING_TO_PATIENT.equals(prevState)) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedCarKeywords.MISSION_ASSIGNED_EVENT_KEY, payload));
            }

            // On Scene Treating: MovingToPatient → TreatingPatient
            if (MedCarKeywords.STATE_TREATING_PATIENT.equals(currState)
                    && !MedCarKeywords.STATE_TREATING_PATIENT.equals(prevState)) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedCarKeywords.ON_SCENE_TREATING_EVENT_KEY, payload));
            }

            // Mission Completed: TreatingPatient → Returning
            if (MedCarKeywords.STATE_RETURNING.equals(currState)
                    && MedCarKeywords.STATE_TREATING_PATIENT.equals(prevState)) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedCarKeywords.MISSION_COMPLETED_EVENT_KEY, payload));
            }

            // Critical Fuel: fronte di discesa sotto 0.20
            if (payload.fuelLevel() < MedCarKeywords.CRITICAL_FUEL_THRESHOLD
                    && (lastTelemetry == null
                        || lastTelemetry.fuelLevel() >= MedCarKeywords.CRITICAL_FUEL_THRESHOLD)) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedCarKeywords.CRITICAL_FUEL_EVENT_KEY, payload));
            }

            // Maintenance Required: fronte di salita su needsMaintenance
            if (payload.needsMaintenance()
                    && (lastTelemetry == null || !lastTelemetry.needsMaintenance())) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                        MedCarKeywords.MAINTENANCE_REQUIRED_EVENT_KEY, payload));
            }

            lastTelemetry = payload;

        } catch (EventBusException e) {
            System.err.println("[MedCarPhysicalAdapter] EventBus error: " + e.getMessage());
        }
    }

    // ── Action (no-op) ────────────────────────────────────────────────────────

    @Override
    public void onIncomingPhysicalAction(PhysicalAssetActionWldtEvent<?> event) {
        // La MedCar non espone azioni: log per debug e nulla più
        System.out.println("[MedCarPhysicalAdapter] -> Unsupported action received: "
                + (event != null ? event.getActionKey() : "null"));
    }
}