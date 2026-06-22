package it.ausl.emergency.payload;

/**
 * Payload MQTT pubblicato su "ces/medhelicopter/{agentId}/state".
 *
 * Campi allineati al sendStatus() AnyLogic del MedHelicopter:
 *   state, lat, lon, patientId, fuelLevel, needsRefueling,
 *   needsMaintenance, timestamp, hospitalId, tripDistanceSinceEmergency
 *
 * Nota: homeBase non è presente nel payload MQTT del MedHelicopter,
 * viene configurato staticamente tramite MedHelicopterAdapterConfiguration.
 */
public record MedHelicopterTelemetryPayload(
        String  state,
        double  lat,
        double  lon,
        String  patientId,
        String  hospitalId,
        double  fuelLevel,
        int     missionsSinceMaintenance,
        boolean needsRefueling,
        boolean needsMaintenance,
        double  timestamp,
        double  tripDistanceSinceEmergency
) {
    /** True se l'elicottero ha un paziente a bordo o assegnato. */
    public boolean hasPatient() {
        return patientId != null
                && !patientId.isBlank()
                && !"null".equalsIgnoreCase(patientId);
    }

    /** True se è stata assegnata una destinazione ospedaliera. */
    public boolean hasHospital() {
        return hospitalId != null
                && !hospitalId.isBlank()
                && !"null".equalsIgnoreCase(hospitalId);
    }
}
