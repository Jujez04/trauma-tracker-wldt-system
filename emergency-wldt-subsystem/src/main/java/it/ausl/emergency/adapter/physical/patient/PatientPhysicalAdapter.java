package it.ausl.emergency.adapter.physical.patient;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.ausl.emergency.adapter.configuration.PatientAdapterConfiguration;
import it.ausl.emergency.payload.PatientTelemetryPayload;
import it.ausl.emergency.utils.PatientKeywords;
import it.wldt.adapter.physical.ConfigurablePhysicalAdapter;
import it.wldt.adapter.physical.PhysicalAssetDescription;
import it.wldt.adapter.physical.PhysicalAssetEvent;
import it.wldt.adapter.physical.PhysicalAssetProperty;
import it.wldt.adapter.physical.event.PhysicalAssetActionWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetEventWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.exception.EventBusException;

public class PatientPhysicalAdapter extends ConfigurablePhysicalAdapter<PatientAdapterConfiguration> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PatientTelemetryPayload lastTelemetry = null;

    public PatientPhysicalAdapter(String id, PatientAdapterConfiguration configuration) {
        super(id, configuration);
    }

    @Override
    public void onIncomingPhysicalAction(PhysicalAssetActionWldtEvent<?> physicalActionEvent) {
        System.out.println("[PatientPhysicalAdapter] -> Action requests are currently unsupported for target: "
                + (physicalActionEvent != null ? physicalActionEvent.getActionKey() : "null"));
    }

    @Override
    public void onAdapterStart() {
        try {
            System.out.println("[PatientPhysicalAdapter] -> Starting with configuration: " + getConfiguration());
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

        // Lettura delle proprietà strutturali iniziali dalla configurazione iniettata
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.STATE_PROPERTY_KEY, getConfiguration().getDefaultState()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.SEVERITY_CODE_PROPERTY_KEY, getConfiguration().getDefaultSeverityCode()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.CONFIRMED_SEVERITY_CODE_PROPERTY_KEY, getConfiguration().getDefaultConfirmedSeverityCode()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.PATHOLOGY_PROPERTY_KEY, getConfiguration().getDefaultPathology()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.GCS_SCORE_PROPERTY_KEY, getConfiguration().getDefaultGcsScore()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.AIRWAY_OBSTRUCTED_PROPERTY_KEY, getConfiguration().isDefaultAirwayObstructed()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.EXTERNAL_HEMORRHAGE_PROPERTY_KEY, getConfiguration().isDefaultExternalHemorrhage()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.CLINICAL_DETERIORATED_PROPERTY_KEY, getConfiguration().isDefaultClinicalDeteriorated()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.LATITUDE_PROPERTY_KEY, getConfiguration().getDefaultLatitude()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.LONGITUDE_PROPERTY_KEY, getConfiguration().getDefaultLongitude()));
        pad.getProperties().add(new PhysicalAssetProperty<>(PatientKeywords.TIME_CALLED_PROPERTY_KEY, getConfiguration().getDefaultTimeCalled()));

        // Eventi di Dominio DDD
        pad.getEvents().add(new PhysicalAssetEvent(PatientKeywords.CLINICAL_ASSESSMENT_EVENT_KEY, "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(PatientKeywords.CLINICAL_DETERIORATION_EVENT_KEY, "application/json"));
        pad.getEvents().add(new PhysicalAssetEvent(PatientKeywords.HANDOVER_COMPLETED_EVENT_KEY, "application/json"));

        return pad;
    }

    public void onPatientJsonTelemetryReceived(String jsonPayload) {
        try {
            PatientTelemetryPayload payload = objectMapper.readValue(jsonPayload, PatientTelemetryPayload.class);
            this.onPatientTelemetryReceived(payload);
        } catch (Exception e) {
            System.err.println("[PatientPhysicalAdapter] Failed to deserialize raw JSON string: " + e.getMessage());
        }
    }

    public void onPatientTelemetryReceived(PatientTelemetryPayload payload) {
        if (payload == null) return;

        try {
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

            // Domain Event 1 — CLINICAL_ASSESSMENT
            boolean isNowBeingTreated  = PatientKeywords.STATE_BEING_TREATED.equalsIgnoreCase(payload.state());
            boolean wasBeingTreated    = lastTelemetry != null && PatientKeywords.STATE_BEING_TREATED.equalsIgnoreCase(lastTelemetry.state());
            if (isNowBeingTreated && !wasBeingTreated) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(PatientKeywords.CLINICAL_ASSESSMENT_EVENT_KEY, payload));
            }

            // Domain Event 2 — CLINICAL_DETERIORATION
            if (payload.isClinicalDeteriorated() && (lastTelemetry == null || !lastTelemetry.isClinicalDeteriorated())) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(PatientKeywords.CLINICAL_DETERIORATION_EVENT_KEY, payload));
            }

            // Domain Event 3 — HANDOVER_COMPLETED
            boolean isNowAtHospital = PatientKeywords.STATE_AT_HOSPITAL.equalsIgnoreCase(payload.state());
            boolean wasAtHospital   = lastTelemetry != null && PatientKeywords.STATE_AT_HOSPITAL.equalsIgnoreCase(lastTelemetry.state());
            if (isNowAtHospital && !wasAtHospital) {
                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(PatientKeywords.HANDOVER_COMPLETED_EVENT_KEY, payload));
            }

            lastTelemetry = payload;

        } catch (EventBusException e) {
            System.err.println("[PatientPhysicalAdapter] Event Bus error: " + e.getMessage());
        }
    }
}