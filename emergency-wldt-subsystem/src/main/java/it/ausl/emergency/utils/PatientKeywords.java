package it.ausl.emergency.utils;

/**
 * Chiavi utilizzate per descrivere il Physical Asset del Paziente (proprietà ed
 * eventi) e per
 * mappare in modo coerente i campi di
 * {@link it.ausl.emergency.payload.PatientTelemetryPayload}
 * sulle proprietà del Digital Twin State.
 *
 * Gli eventi ricalcano i Domain Events individuati nell'analisi DDD della tesi
 * (cap. 3.2.4):
 * Riscontro Clinico Eseguito, Deterioramento Clinico Rilevato, Handover
 * Completato.
 */
public class PatientKeywords {
    public static final String STATE_PROPERTY_KEY = "patient-state-property-key";
    public static final String SEVERITY_CODE_PROPERTY_KEY = "patient-severity-code-property-key";
    public static final String CONFIRMED_SEVERITY_CODE_PROPERTY_KEY = "patient-confirmed-severity-code-property-key";
    public static final String PATHOLOGY_PROPERTY_KEY = "patient-pathology-property-key";
    public static final String GCS_SCORE_PROPERTY_KEY = "patient-gcs-score-property-key";
    public static final String AIRWAY_OBSTRUCTED_PROPERTY_KEY = "patient-airway-obstructed-property-key";
    public static final String EXTERNAL_HEMORRHAGE_PROPERTY_KEY = "patient-external-hemorrhage-property-key";
    public static final String CLINICAL_DETERIORATED_PROPERTY_KEY = "patient-clinical-deteriorated-property-key";
    public static final String LATITUDE_PROPERTY_KEY = "patient-latitude-property-key";
    public static final String LONGITUDE_PROPERTY_KEY = "patient-longitude-property-key";
    public static final String TIME_CALLED_PROPERTY_KEY = "patient-time-called-property-key";

    // ── Event Keys (Domain Events DDD) ────────────────────────────────────────
    public static final String CLINICAL_ASSESSMENT_EVENT_KEY = "clinical-assessment-event-key";
    public static final String CLINICAL_DETERIORATION_EVENT_KEY = "clinical-deterioration-event-key";
    public static final String HANDOVER_COMPLETED_EVENT_KEY = "handover-completed-event-key";

    // ── Valori degli stati dello statechart AnyLogic ──────────────────────────
    public static final String STATE_SIGNALED = "Signaled";
    public static final String STATE_WAITING_SUPPORT = "WaitingSupport";
    public static final String STATE_BEING_TREATED = "BeingTreated";
    public static final String STATE_MOVING_TO_HOSPITAL = "MovingToHospital";
    public static final String STATE_AT_HOSPITAL = "AtHospital";

    // Stato terminale che identifica la fine della missione (handover implicito)
    public static final String HANDOVER_STATE_VALUE = STATE_AT_HOSPITAL;

    // ── Valori SeverityCode (enum AnyLogic → lowercase nel payload JSON) ──────
    public static final String SEVERITY_WHITE = "WHITE";
    public static final String SEVERITY_GREEN = "GREEN";
    public static final String SEVERITY_YELLOW = "YELLOW";
    public static final String SEVERITY_RED = "RED";

    // ── Valori FHQPathology (enum AnyLogic → uppercase nel payload JSON) ──────
    public static final String PATHOLOGY_CARDIAC_ARREST = "CARDIAC_ARREST";
    public static final String PATHOLOGY_STEMI = "STEMI";
    public static final String PATHOLOGY_STROKE = "STROKE";
    public static final String PATHOLOGY_RESPIRATORY_FAILURE = "RESPIRATORY_FAILURE";
    public static final String PATHOLOGY_SEVERE_TRAUMA = "SEVERE_TRAUMA";
    public static final String PATHOLOGY_NONE = "NONE";

    private PatientKeywords() {
    }
}
