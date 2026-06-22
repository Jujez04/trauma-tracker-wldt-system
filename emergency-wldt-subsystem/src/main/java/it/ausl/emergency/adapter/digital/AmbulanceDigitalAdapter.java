package it.ausl.emergency.adapter.digital;

import it.ausl.emergency.adapter.configuration.AmbulanceAdapterConfiguration;
import it.ausl.emergency.payload.AmbulanceTelemetryPayload;
import it.ausl.emergency.utils.AmbulanceKeywords;
import it.wldt.adapter.digital.DigitalAdapter;
import it.wldt.core.state.*;
import it.wldt.exception.EventBusException;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Ambulance Digital Adapter.
 * Exposes vehicle fleet logistics, coordinates, and operational updates to the control center dashboard.
 */
public class AmbulanceDigitalAdapter extends DigitalAdapter<AmbulanceAdapterConfiguration> {

    public AmbulanceDigitalAdapter(String id, AmbulanceAdapterConfiguration configuration) {
        super(id, configuration);
    }

    // ── Lifecycle Callbacks ──────────────────────────────────────────────────

    @Override
    public void onAdapterStart() {
        System.out.println("[AmbulanceDigitalAdapter] -> Digital Adapter Lifecycle Started: " + getId());
    }

    @Override
    public void onAdapterStop() {
        System.out.println("[AmbulanceDigitalAdapter] -> Digital Adapter Lifecycle Stopped: " + getId());
    }

    // ── Digital Twin Engine Lifecycle Callbacks ──────────────────────────────

    @Override
    public void onDigitalTwinCreate() {
        System.out.println("[AmbulanceDigitalAdapter] -> Vehicle Twin registered in core engine.");
    }

    @Override
    public void onDigitalTwinStart() {
        System.out.println("[AmbulanceDigitalAdapter] -> Vehicle Twin processing layer active.");
    }

    @Override
    public void onDigitalTwinSync(DigitalTwinState currentDigitalTwinState) {
        System.out.println("[AmbulanceDigitalAdapter] -> Synchronization achieved. Binding event observers...");
        printStateSnapshot("INITIAL SYNCHRONIZED AMBULANCE STATE", currentDigitalTwinState);

        try {
            currentDigitalTwinState.getEventList()
                    .map(eventList -> eventList.stream()
                            .map(DigitalTwinStateEvent::getKey)
                            .collect(Collectors.toList()))
                    .ifPresent(keys -> {
                        try {
                            observeDigitalTwinEventsNotifications(keys);
                            System.out.println("[AmbulanceDigitalAdapter] -> Monitoring vehicle alerts: " + keys);
                        } catch (EventBusException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDigitalTwinUnSync(DigitalTwinState currentDigitalTwinState) {
        System.out.println("[AmbulanceDigitalAdapter] -> Warning: Vehicle Twin desynchronized.");
    }

    @Override
    public void onDigitalTwinStop() {
        System.out.println("[AmbulanceDigitalAdapter] -> Vehicle Twin monitoring suspended.");
    }

    @Override
    public void onDigitalTwinDestroy() {
        System.out.println("[AmbulanceDigitalAdapter] -> Vehicle Twin resource context destroyed.");
    }

    // ── Transactional State Monitoring Callback ──────────────────────────────

    @Override
    protected void onStateUpdate(DigitalTwinState newState,
                                 DigitalTwinState previousState,
                                 ArrayList<DigitalTwinStateChange> changes) {

        System.out.println("\n[AmbulanceDigitalAdapter] ─── VEHICLE STATE TRANSACTION UPDATE ───");

        if (changes != null && !changes.isEmpty()) {
            changes.forEach(change -> System.out.printf("  [%s] %s -> %s%n",
                    change.getOperation(), change.getResourceType(), change.getResource()));
        } else {
            System.out.println("  (No kinematic variations detected in this state frame)");
        }

        printFleetSnapshot(newState);
        System.out.println("──────────────────────────────────────────────────────────────────\n");
    }

    // ── Domain Event Notification Callback ───────────────────────────────────

    @Override
    protected void onEventNotificationReceived(DigitalTwinStateEventNotification<?> notification) {
        if (notification == null) return;

        String eventKey = notification.getDigitalEventKey();
        Object body = notification.getBody();

        System.out.println("\n[AmbulanceDigitalAdapter] ═══ ASYNCHRONOUS VEHICLE EVENT INBOUND ═══");
        System.out.println("  Alert Key : " + eventKey);
        System.out.println("  Timestamp : " + notification.getTimestamp());

        if (AmbulanceKeywords.CRITICAL_FUEL_EVENT_KEY.equals(eventKey)) {
            System.out.println("  ► ALERT: CRITICAL FUEL RESERVE DETECTED — REFUELING REQUIRED ⚠");
            printPayloadSummary(body);
        } else if (AmbulanceKeywords.MAINTENANCE_REQUIRED_EVENT_KEY.equals(eventKey)) {
            System.out.println("  ► WARNING: MISSION COMPLIANCE THRESHOLD EXCEEDED — MAINTENANCE REQUIRED ⚠");
            printPayloadSummary(body);
        } else {
            System.out.println("  (Unmanaged infrastructure alert context: " + eventKey + ")");
        }
        System.out.println("====================================================================\n");
    }

    // ── Diagnostic Helpers ───────────────────────────────────────────────────

    private void printStateSnapshot(String title, DigitalTwinState state) {
        System.out.println("\n[AmbulanceDigitalAdapter] ── " + title + " ──");
        if (state == null) return;
        try {
            state.getPropertyList().ifPresent(props ->
                    props.forEach(p -> System.out.printf("  [PROPERTY] %-45s = %s%n", p.getKey(), p.getValue()))
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }

    private void printFleetSnapshot(DigitalTwinState state) {
        if (state == null) return;

        String[] logisticsKeys = {
                AmbulanceKeywords.STATE_PROPERTY_KEY,
                AmbulanceKeywords.PATIENT_ID_PROPERTY_KEY,
                AmbulanceKeywords.HOSPITAL_ID_PROPERTY_KEY,
                AmbulanceKeywords.FUEL_LEVEL_PROPERTY_KEY,
                AmbulanceKeywords.MISSIONS_PROPERTY_KEY,
                AmbulanceKeywords.NEEDS_REFUELING_PROPERTY_KEY,
                AmbulanceKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY,
                AmbulanceKeywords.TRIP_DISTANCE_PROPERTY_KEY
        };

        System.out.println("  [Fleet Asset Snapshot]");
        for (String key : logisticsKeys) {
            try {
                state.getProperty(key).ifPresent(p -> 
                        System.out.printf("    %-50s = %s%n", p.getKey(), p.getValue()));
            } catch (Exception ignored) {}
        }
    }

    private void printPayloadSummary(Object body) {
        if (body instanceof AmbulanceTelemetryPayload p) {
            System.out.printf("    State: %-15s | Patient Bound: %-10s | Hospital Target: %-15s%n",
                    p.state(), p.patientId(), p.hospitalId());
            System.out.printf("    Fuel: %-5.2f | Missions Done: %-4d | Dist. Travelled: %.1f meters%n",
                    p.fuelLevel(), p.missionsSinceMaintenance(), p.tripDistanceSinceEmergency());
        } else {
            System.out.println("    Raw body context: " + body);
        }
    }
}