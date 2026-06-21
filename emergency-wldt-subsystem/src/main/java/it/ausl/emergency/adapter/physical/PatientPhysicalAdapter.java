package it.ausl.emergency.adapter.physical;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.ausl.emergency.model.payload.PatientTelemetryPayload;
import it.ausl.emergency.utils.PatientKeywords;
import it.wldt.adapter.physical.PhysicalAdapter;
import it.wldt.adapter.physical.PhysicalAssetDescription;
import it.wldt.adapter.physical.PhysicalAssetEvent;
import it.wldt.adapter.physical.PhysicalAssetProperty;
import it.wldt.adapter.physical.event.PhysicalAssetActionWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetEventWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.exception.EventBusException;

/**
 * Patient Physical Ingestion Adapter.
 * Bridges the external stochastically generated AnyLogic ABM agent telemetry 
 * (forwarded via MQTT into the management layers) with the logical Digital Twin state core.
 */
public class PatientPhysicalAdapter extends PhysicalAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PatientTelemetryPayload lastTelemetry = null;

    public PatientPhysicalAdapter(String id) {
        super(id);
    }

    @Override
    public void onIncomingPhysicalAction(PhysicalAssetActionWldtEvent<?> physicalActionEvent) {
        System.out.println("[PatientPhysicalAdapter] -> Action requests are currently unsupported for target: "
                + (physicalActionEvent != null ? physicalActionEvent.getActionKey() : "null"));
    }

    @Override
    public void onAdapterStart() {
        try {
            System.out.println("[PatientPhysicalAdapter] -> Publishing Physical Asset Description (PAD)...");
            notifyPhysicalAdapterBound(buildPhysicalAssetDescription());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAdapterStop() {
        System.out.println("[PatientPhysicalAdapter] -> Physical ingestion pipeline stopped.");
    }

    private PhysicalAssetDescription buildPhysicalAssetDescription() {
        PhysicalAssetDescription pad = new PhysicalAssetDescription();

        // Structural Property declarations matching the JSON telemetry schema
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.STATE_PROPERTY_KEY, "created"));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.SEVERITY_CODE_PROPERTY_KEY, "WHITE"));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.CONFIRMED_SEVERITY_CODE_PROPERTY_KEY, "WHITE"));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.PATHOLOGY_PROPERTY_KEY, "none"));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.GCS_SCORE_PROPERTY_KEY, 15));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.AIRWAY_OBSTRUCTED_PROPERTY_KEY, false));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.EXTERNAL_HEMORRHAGE_PROPERTY_KEY, false));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.CLINICAL_DETERIORATED_PROPERTY_KEY, false));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.LATITUDE_PROPERTY_KEY, 0.0));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.LONGITUDE_PROPERTY_KEY, 0.0));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.TIME_CALLED_PROPERTY_KEY, 0.0));

        // Domain Event declarations derived from clinical DDD analysis
        pad.getEvents().add(new PhysicalAssetEvent(PatientKeywords.CLINICAL_ASSESSMENT_EVENT_KEY, "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(PatientKeywords.CLINICAL_DETERIORATION_EVENT_KEY, "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(PatientKeywords.HANDOVER_COMPLETED_EVENT_KEY, "application/json"));

        return pad;
    }

    /**
     * Test Interface Helper.
     * Allows mock test runners to bypass network overhead and feed raw JSON strings directly.
     */
    public void onPatientJsonTelemetryReceived(String jsonPayload) {
        try {
            PatientTelemetryPayload payload = objectMapper.readValue(jsonPayload, PatientTelemetryPayload.class);
            this.onPatientTelemetryReceived(payload);
        } catch (Exception e) {
            System.err.println("[PatientPhysicalAdapter] Failed to deserialize raw JSON string: " + e.getMessage());
        }
    }

    /**
     * Primary data ingestion endpoint. Receives unmarshalled telemetry models,
     * updates properties, and triggers explicit Domain Events based on state transitions.
     */
    public void onPatientTelemetryReceived(PatientTelemetryPayload payload) {
        if (payload == null) return;

        try {
            // Pubblicazione proprietà — invariata
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.STATE_PROPERTY_KEY, payload.state()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.SEVERITY_CODE_PROPERTY_KEY, payload.severityCode()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.CONFIRMED_SEVERITY_CODE_PROPERTY_KEY,
                    payload.confirmedSeverityCode() != null ? payload.confirmedSeverityCode() : "WHITE"));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.PATHOLOGY_PROPERTY_KEY, payload.pathology()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.GCS_SCORE_PROPERTY_KEY, payload.gcsScore()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.AIRWAY_OBSTRUCTED_PROPERTY_KEY, payload.isAirwayObstructed()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.EXTERNAL_HEMORRHAGE_PROPERTY_KEY, payload.hasExternalHemorrhage()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.CLINICAL_DETERIORATED_PROPERTY_KEY, payload.isClinicalDeteriorated()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.LATITUDE_PROPERTY_KEY, payload.lat()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.LONGITUDE_PROPERTY_KEY, payload.lon()));
            publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(PatientKeywords.TIME_CALLED_PROPERTY_KEY, payload.timeCalled()));

            // Domain Event 1 — CLINICAL_ASSESSMENT: fronte → BeingTreated
            // (confirmedSeverityCode non è affidabile: arriva sempre "WHITE" dalla simulazione
            //  prima che il triage sia eseguito; lo stato BeingTreated è il segnale corretto)
            boolean isNowBeingTreated  = PatientKeywords.STATE_BEING_TREATED.equalsIgnoreCase(payload.state());
            boolean wasBeingTreated    = lastTelemetry != null
                    && PatientKeywords.STATE_BEING_TREATED.equalsIgnoreCase(lastTelemetry.state());
            if (isNowBeingTreated && !wasBeingTreated) {
                publishPhysicalAssetEventWldtEvent(
                        new PhysicalAssetEventWldtEvent<>(PatientKeywords.CLINICAL_ASSESSMENT_EVENT_KEY, payload));
            }

            // Domain Event 2 — CLINICAL_DETERIORATION: fronte false→true su isClinicalDeteriorated
            if (payload.isClinicalDeteriorated()
                    && (lastTelemetry == null || !lastTelemetry.isClinicalDeteriorated())) {
                publishPhysicalAssetEventWldtEvent(
                        new PhysicalAssetEventWldtEvent<>(PatientKeywords.CLINICAL_DETERIORATION_EVENT_KEY, payload));
            }

            // Domain Event 3 — HANDOVER_COMPLETED: fronte → AtHospital
            boolean isNowAtHospital = PatientKeywords.STATE_AT_HOSPITAL.equalsIgnoreCase(payload.state());
            boolean wasAtHospital   = lastTelemetry != null
                    && PatientKeywords.STATE_AT_HOSPITAL.equalsIgnoreCase(lastTelemetry.state());
            if (isNowAtHospital && !wasAtHospital) {
                publishPhysicalAssetEventWldtEvent(
                        new PhysicalAssetEventWldtEvent<>(PatientKeywords.HANDOVER_COMPLETED_EVENT_KEY, payload));
            }

            lastTelemetry = payload;

        } catch (EventBusException e) {
            System.err.println("[PatientPhysicalAdapter] Event Bus error: " + e.getMessage());
        }
    }
}