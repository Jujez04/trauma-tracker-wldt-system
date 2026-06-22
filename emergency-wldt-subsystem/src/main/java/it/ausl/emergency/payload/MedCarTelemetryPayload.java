package it.ausl.emergency.payload;

/**
 * topic MQTT "ces/medcar/{agentId}/state".
 */
public record MedCarTelemetryPayload(
        String state,
        double lat,
        double lon,
        String patientId,
        String homeBaseId,
        double fuelLevel,
        int    missionsSinceMaintenance,
        boolean needsRefueling,
        boolean needsMaintenance,
        double timestamp,
        double tripDistanceSinceEmergency
) {
    /** True se la MedCar è attualmente assegnata a un paziente. */
    public boolean hasPatient() {
        return patientId != null
                && !patientId.isBlank()
                && !"null".equalsIgnoreCase(patientId);
    }
}