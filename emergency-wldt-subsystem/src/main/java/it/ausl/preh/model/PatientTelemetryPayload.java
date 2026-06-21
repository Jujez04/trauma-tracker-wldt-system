package it.ausl.preh.model;

public record PatientTelemetryPayload(
    double latitude,
    double longitude,
    String severityCode,
    boolean medicalAssisted
) {}
