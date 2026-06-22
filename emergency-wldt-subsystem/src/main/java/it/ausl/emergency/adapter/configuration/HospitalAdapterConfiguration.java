package it.ausl.emergency.adapter.configuration;

public class HospitalAdapterConfiguration {

    private int defaultAssistanceLevel = 1;
    private int defaultPatientAssisted = 0;
    private double defaultTimestamp = 0.0;

    public HospitalAdapterConfiguration() {
    }

    public HospitalAdapterConfiguration(int defaultAssistanceLevel,
            int defaultPatientAssisted,
            double defaultTimestamp) {
        this.defaultAssistanceLevel = defaultAssistanceLevel;
        this.defaultPatientAssisted = defaultPatientAssisted;
        this.defaultTimestamp = defaultTimestamp;
    }

    public int getDefaultAssistanceLevel() {
        return defaultAssistanceLevel;
    }

    public void setDefaultAssistanceLevel(int v) {
        this.defaultAssistanceLevel = v;
    }

    public int getDefaultPatientAssisted() {
        return defaultPatientAssisted;
    }

    public void setDefaultPatientAssisted(int v) {
        this.defaultPatientAssisted = v;
    }

    public double getDefaultTimestamp() {
        return defaultTimestamp;
    }

    public void setDefaultTimestamp(double v) {
        this.defaultTimestamp = v;
    }

    @Override
    public String toString() {
        return "HospitalAdapterConfiguration{assistanceLevel=" + defaultAssistanceLevel
                + ", patientAssisted=" + defaultPatientAssisted
                + ", timestamp=" + defaultTimestamp + '}';
    }
}