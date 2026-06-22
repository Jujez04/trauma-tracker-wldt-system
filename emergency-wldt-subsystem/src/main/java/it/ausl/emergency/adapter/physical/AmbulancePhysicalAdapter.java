package it.ausl.emergency.adapter.physical;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.ausl.emergency.adapter.configuration.AmbulanceAdapterConfiguration;
import it.ausl.emergency.payload.AmbulanceTelemetryPayload;
import it.ausl.emergency.utils.AmbulanceKeywords;
import it.wldt.adapter.physical.ConfigurablePhysicalAdapter;
import it.wldt.adapter.physical.PhysicalAssetDescription;
import it.wldt.adapter.physical.PhysicalAssetEvent;
import it.wldt.adapter.physical.PhysicalAssetProperty;
import it.wldt.adapter.physical.PhysicalAssetAction;
import it.wldt.adapter.physical.event.PhysicalAssetActionWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetEventWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.exception.EventBusException;

/**
 * Ambulance Configurable Physical Adapter.
 * Ingests external real-time multi-variable fleet payloads and exposes
 * the vehicle destination rerouting action loop back to the simulation.
 */
public class AmbulancePhysicalAdapter extends ConfigurablePhysicalAdapter<AmbulanceAdapterConfiguration> {

        private final ObjectMapper objectMapper = new ObjectMapper();
        private AmbulanceTelemetryPayload lastTelemetry = null;

        public AmbulancePhysicalAdapter(String id, AmbulanceAdapterConfiguration configuration) {
                super(id, configuration);
        }

        @Override
        public void onAdapterStart() {
                try {
                        System.out.println(
                                        "[AmbulancePhysicalAdapter] -> Initializing vehicle instance loop with parameters: "
                                                        + getConfiguration());
                        notifyPhysicalAdapterBound(buildPhysicalAssetDescription());
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        @Override
        public void onAdapterStop() {
                System.out.println(
                                "[AmbulancePhysicalAdapter] -> Physical vehicle fleet ingestion pipeline terminated.");
        }

        private PhysicalAssetDescription buildPhysicalAssetDescription() {
                PhysicalAssetDescription pad = new PhysicalAssetDescription();

                // 1. Map Configuration Properties to Physical Asset Description (PAD)
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.STATE_PROPERTY_KEY,
                                getConfiguration().getDefaultState()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.LATITUDE_PROPERTY_KEY,
                                getConfiguration().getDefaultLatitude()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.LONGITUDE_PROPERTY_KEY,
                                getConfiguration().getDefaultLongitude()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.PATIENT_ID_PROPERTY_KEY,
                                getConfiguration().getDefaultPatientId()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.HOSPITAL_ID_PROPERTY_KEY,
                                getConfiguration().getDefaultHospitalId()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.FUEL_LEVEL_PROPERTY_KEY,
                                getConfiguration().getDefaultFuelLevel()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.MISSIONS_PROPERTY_KEY,
                                getConfiguration().getDefaultMissionsSinceMaintenance()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.NEEDS_REFUELING_PROPERTY_KEY,
                                getConfiguration().isDefaultNeedsRefueling()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY,
                                getConfiguration().isDefaultNeedsMaintenance()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.TIMESTAMP_PROPERTY_KEY,
                                getConfiguration().getDefaultTimestamp()));
                pad.getProperties().add(new PhysicalAssetProperty<>(AmbulanceKeywords.TRIP_DISTANCE_PROPERTY_KEY,
                                getConfiguration().getDefaultTripDistanceSinceEmergency()));

                // 2. Register Structural Domain Warnings
                pad.getEvents().add(
                                new PhysicalAssetEvent(AmbulanceKeywords.CRITICAL_FUEL_EVENT_KEY, "application/json"));
                pad.getEvents().add(new PhysicalAssetEvent(AmbulanceKeywords.MAINTENANCE_REQUIRED_EVENT_KEY,
                                "application/json"));

                // 3. Register Closed-Loop Optimization Action Contract
                pad.getActions().add(new PhysicalAssetAction(
                                AmbulanceKeywords.REDIRECT_VEHICLE_ACTION_KEY,
                                "application/json",
                                "java.lang.String"));

                return pad;
        }

        /**
         * Actuation Command Center Endpoint.
         * Triggered by the Operational Core to override the vehicle destination
         * hospital.
         */
        @Override
        public void onIncomingPhysicalAction(PhysicalAssetActionWldtEvent<?> physicalActionEvent) {
                if (physicalActionEvent == null)
                        return;

                String actionKey = physicalActionEvent.getActionKey();
                System.out.println("[AmbulancePhysicalAdapter] -> Incoming control loop command detected on key: "
                                + actionKey);

                if (AmbulanceKeywords.REDIRECT_VEHICLE_ACTION_KEY.equals(actionKey)) {
                        String targetHospitalId = (String) physicalActionEvent.getBody();
                        System.out.println(
                                        "[AmbulancePhysicalAdapter] -> COMMAND RECEIVED: Rerouting vehicle fleet destination to: "
                                                        + targetHospitalId);
                }
        }

        public void onAmbulanceJsonTelemetryReceived(String jsonPayload) {
                try {
                        AmbulanceTelemetryPayload payload = objectMapper.readValue(jsonPayload,
                                        AmbulanceTelemetryPayload.class);
                        this.onAmbulanceTelemetryReceived(payload);
                } catch (Exception e) {
                        System.err.println("[AmbulancePhysicalAdapter] JSON contract matching structure error: "
                                        + e.getMessage());
                }
        }

        public void onAmbulanceTelemetryReceived(AmbulanceTelemetryPayload payload) {
                if (payload == null)
                        return;

                try {
                        // Forward variables onto the core transactional engine
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.STATE_PROPERTY_KEY, payload.state()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.LATITUDE_PROPERTY_KEY, payload.lat()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.LONGITUDE_PROPERTY_KEY, payload.lon()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.PATIENT_ID_PROPERTY_KEY, payload.patientId()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.HOSPITAL_ID_PROPERTY_KEY, payload.hospitalId()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.FUEL_LEVEL_PROPERTY_KEY, payload.fuelLevel()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.MISSIONS_PROPERTY_KEY, payload.missionsSinceMaintenance()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.NEEDS_REFUELING_PROPERTY_KEY, payload.needsRefueling()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY, payload.needsMaintenance()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.TIMESTAMP_PROPERTY_KEY, payload.timestamp()));
                        publishPhysicalAssetPropertyWldtEvent(new PhysicalAssetPropertyWldtEvent<>(
                                        AmbulanceKeywords.TRIP_DISTANCE_PROPERTY_KEY,
                                        payload.tripDistanceSinceEmergency()));

                        // Domain Event 1: Critical Fuel Level Notification Edge
                        if (payload.fuelLevel() < 0.20
                                        && (lastTelemetry == null || lastTelemetry.fuelLevel() >= 0.20)) {
                                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                                                AmbulanceKeywords.CRITICAL_FUEL_EVENT_KEY, payload));
                        }

                        // Domain Event 2: Maintenance Requested Flag Edge
                        if (payload.needsMaintenance()
                                        && (lastTelemetry == null || !lastTelemetry.needsMaintenance())) {
                                publishPhysicalAssetEventWldtEvent(new PhysicalAssetEventWldtEvent<>(
                                                AmbulanceKeywords.MAINTENANCE_REQUIRED_EVENT_KEY, payload));
                        }

                        lastTelemetry = payload;

                } catch (EventBusException e) {
                        System.err.println(
                                        "[AmbulancePhysicalAdapter] Processing error on structural event bus context: "
                                                        + e.getMessage());
                }
        }
}