package it.ausl.emergency.payload;

public record PatientTelemetryPayload(
    String state,
    String severityCode,
    String confirmedSeverityCode,
    String pathology,
    int gcsScore,
    boolean isAirwayObstructed,
    boolean hasExternalHemorrhage,
    boolean isClinicalDeteriorated,
    double lat,
    double lon,
    double timeCalled
) {
    public boolean isTriageConfirmed() {
        return "BeingTreated".equalsIgnoreCase(state)
                || "MovingToHospital".equalsIgnoreCase(state)
                || "AtHospital".equalsIgnoreCase(state);
    }
}