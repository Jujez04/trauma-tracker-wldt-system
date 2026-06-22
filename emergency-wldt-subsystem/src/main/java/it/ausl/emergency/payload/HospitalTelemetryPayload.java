package it.ausl.emergency.payload;

public record HospitalTelemetryPayload(
    int assistanceLevel,
    int patientAssisted,
    double timestamp
) {}