package it.ausl.emergency.payload;

public record AmbulanceTelemetryPayload(
        String state,
        double lat,
        double lon,
        String patientId,
        String hospitalId,
        String homeBaseId,
        double fuelLevel,
        int missionsSinceMaintenance,
        boolean needsRefueling,
        boolean needsMaintenance,
        double timestamp,
        double tripDistanceSinceEmergency) {

    public boolean hasPatient() {
        return patientId != null && !patientId.isBlank() && !"null".equalsIgnoreCase(patientId);
    }

    public boolean hasHospital() {
        return hospitalId != null && !hospitalId.isBlank() && !"null".equalsIgnoreCase(hospitalId);
    }
}
