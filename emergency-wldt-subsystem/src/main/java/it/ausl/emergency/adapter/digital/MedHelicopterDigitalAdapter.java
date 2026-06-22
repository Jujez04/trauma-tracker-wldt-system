package it.ausl.emergency.adapter.digital;

import it.ausl.emergency.adapter.configuration.MedHelicopterAdapterConfiguration;
import it.ausl.emergency.payload.MedHelicopterTelemetryPayload;
import it.ausl.emergency.utils.MedHelicopterKeywords;
import it.wldt.adapter.digital.DigitalAdapter;
import it.wldt.core.state.*;
import it.wldt.exception.EventBusException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Digital Adapter del MedHelicopter.
 *
 * Espone lo stato operativo dell'elisoccorso verso l'esterno
 * (dashboard, test, servizi REST).
 *
 * Segue lo stesso pattern di MedCarDigitalAdapter:
 *  - ConcurrentHashMap come snapshot leggibile dall'esterno
 *  - CountDownLatch per la sincronizzazione con i test JUnit
 *  - Contatori per ogni Domain Event ricevuto
 *
 * Il MedHelicopter non accetta azioni digitali: onDigitalActionEvent è no-op.
 */
public class MedHelicopterDigitalAdapter extends DigitalAdapter<MedHelicopterAdapterConfiguration> {

    // Snapshot aggiornato ad ogni onStateUpdate() — leggibile dai test
    private final ConcurrentHashMap<String, Object> propertySnapshot = new ConcurrentHashMap<>();

    // Si sblocca quando il DT raggiunge lo stato Shadowed
    private final CountDownLatch syncLatch = new CountDownLatch(1);

    // Contatori Domain Events — per le assert nei test
    private volatile int missionAssignedCount    = 0;
    private volatile int patientOnboardCount     = 0;
    private volatile int hospitalHandoverCount   = 0;
    private volatile int criticalFuelCount       = 0;
    private volatile int maintenanceRequiredCount = 0;

    public MedHelicopterDigitalAdapter(String id, MedHelicopterAdapterConfiguration configuration) {
        super(id, configuration);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onAdapterStart() {
        System.out.println("[MedHelicopterDigitalAdapter] -> onAdapterStart(): " + getId());
    }

    @Override
    public void onAdapterStop() {
        System.out.println("[MedHelicopterDigitalAdapter] -> onAdapterStop(): " + getId());
    }

    // ── DT Life Cycle Callbacks ───────────────────────────────────────────────

    @Override
    public void onDigitalTwinCreate() {
        System.out.println("[MedHelicopterDigitalAdapter] -> Twin registered in engine.");
    }

    @Override
    public void onDigitalTwinStart() {
        System.out.println("[MedHelicopterDigitalAdapter] -> Processing layer active.");
    }

    @Override
    public void onDigitalTwinSync(DigitalTwinState currentState) {
        System.out.println("[MedHelicopterDigitalAdapter] -> Synchronization achieved.");
        refreshSnapshot(currentState);
        printStateSnapshot("INITIAL SYNCHRONIZED MEDHELICOPTER STATE", currentState);

        // Iscrizione a tutti gli eventi dichiarati nella DT State
        try {
            currentState.getEventList()
                    .map(list -> list.stream()
                            .map(DigitalTwinStateEvent::getKey)
                            .collect(Collectors.toList()))
                    .ifPresent(keys -> {
                        try {
                            observeDigitalTwinEventsNotifications(keys);
                            System.out.println("[MedHelicopterDigitalAdapter] -> Observing events: " + keys);
                        } catch (EventBusException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Segnala ai test che il DT è pronto
        syncLatch.countDown();
    }

    @Override
    public void onDigitalTwinUnSync(DigitalTwinState currentState) {
        System.out.println("[MedHelicopterDigitalAdapter] -> Warning: MedHelicopter Twin desynchronized.");
    }

    @Override
    public void onDigitalTwinStop() {
        System.out.println("[MedHelicopterDigitalAdapter] -> Monitoring suspended.");
    }

    @Override
    public void onDigitalTwinDestroy() {
        System.out.println("[MedHelicopterDigitalAdapter] -> Twin destroyed.");
    }

    // ── State Update ──────────────────────────────────────────────────────────

    @Override
    protected void onStateUpdate(DigitalTwinState newState,
                                 DigitalTwinState previousState,
                                 ArrayList<DigitalTwinStateChange> changes) {

        System.out.println("\n[MedHelicopterDigitalAdapter] ─── STATE UPDATE ──────────────────────");

        if (changes != null && !changes.isEmpty()) {
            changes.forEach(c -> System.out.printf("  [%s] %s -> %s%n",
                    c.getOperation(), c.getResourceType(), c.getResource()));
        } else {
            System.out.println("  (no changes detected)");
        }

        refreshSnapshot(newState);
        printOperationalSnapshot(newState);
        System.out.println("────────────────────────────────────────────────────────────────────\n");
    }

    // ── Domain Event Callbacks ────────────────────────────────────────────────

    @Override
    protected void onEventNotificationReceived(DigitalTwinStateEventNotification<?> notification) {
        if (notification == null) return;

        String eventKey = notification.getDigitalEventKey();
        Object body     = notification.getBody();

        System.out.println("\n[MedHelicopterDigitalAdapter] ══ DOMAIN EVENT ════════════════════════");
        System.out.println("  Event Key : " + eventKey);

        if (MedHelicopterKeywords.MISSION_ASSIGNED_EVENT_KEY.equals(eventKey)) {
            missionAssignedCount++;
            System.out.println("  ► MISSION ASSIGNED — Helicopter dispatched to patient  (#"
                    + missionAssignedCount + ")");
            printPayloadSummary(body);

        } else if (MedHelicopterKeywords.PATIENT_ONBOARD_EVENT_KEY.equals(eventKey)) {
            patientOnboardCount++;
            System.out.println("  ► PATIENT ONBOARD — En route to hospital  (#"
                    + patientOnboardCount + ")");
            printPayloadSummary(body);

        } else if (MedHelicopterKeywords.HOSPITAL_HANDOVER_EVENT_KEY.equals(eventKey)) {
            hospitalHandoverCount++;
            System.out.println("  ► HOSPITAL HANDOVER — Patient delivered  (#"
                    + hospitalHandoverCount + ")");
            printPayloadSummary(body);

        } else if (MedHelicopterKeywords.CRITICAL_FUEL_EVENT_KEY.equals(eventKey)) {
            criticalFuelCount++;
            System.out.println("  ► CRITICAL FUEL RESERVE ⚠  (#" + criticalFuelCount + ")");
            printPayloadSummary(body);

        } else if (MedHelicopterKeywords.MAINTENANCE_REQUIRED_EVENT_KEY.equals(eventKey)) {
            maintenanceRequiredCount++;
            System.out.println("  ► MAINTENANCE REQUIRED ⚠  (#" + maintenanceRequiredCount + ")");
            printPayloadSummary(body);

        } else {
            System.out.println("  (unhandled event: " + eventKey + ")");
        }

        System.out.println("═════════════════════════════════════════════════════════════════════\n");
    }

    // ── API pubblica per i test ───────────────────────────────────────────────

    /** Valore corrente di una proprietà del DT State. */
    public Optional<Object> getProperty(String key) {
        return Optional.ofNullable(propertySnapshot.get(key));
    }

    /** Latch sbloccato quando il DT è in stato Shadowed. */
    public CountDownLatch getSyncLatch() { return syncLatch; }

    public int getMissionAssignedCount()    { return missionAssignedCount; }
    public int getPatientOnboardCount()     { return patientOnboardCount; }
    public int getHospitalHandoverCount()   { return hospitalHandoverCount; }
    public int getCriticalFuelCount()       { return criticalFuelCount; }
    public int getMaintenanceRequiredCount(){ return maintenanceRequiredCount; }

    // ── Helper privati ────────────────────────────────────────────────────────

    private void refreshSnapshot(DigitalTwinState state) {
        if (state == null) return;
        try {
            state.getPropertyList().ifPresent(props ->
                    props.forEach(p -> {
                        if (p.getValue() != null) propertySnapshot.put(p.getKey(), p.getValue());
                    }));
        } catch (Exception e) {
            System.err.println("[MedHelicopterDigitalAdapter] Snapshot refresh error: " + e.getMessage());
        }
    }

    private void printStateSnapshot(String title, DigitalTwinState state) {
        System.out.println("\n[MedHelicopterDigitalAdapter] ── " + title + " ──");
        if (state == null) return;
        try {
            state.getPropertyList().ifPresent(props ->
                    props.forEach(p -> System.out.printf(
                            "  [PROP] %-55s = %s%n", p.getKey(), p.getValue())));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }

    private void printOperationalSnapshot(DigitalTwinState state) {
        if (state == null) return;
        String[] keys = {
                MedHelicopterKeywords.STATE_PROPERTY_KEY,
                MedHelicopterKeywords.PATIENT_ID_PROPERTY_KEY,
                MedHelicopterKeywords.HOSPITAL_ID_PROPERTY_KEY,
                MedHelicopterKeywords.HOME_BASE_PROPERTY_KEY,
                MedHelicopterKeywords.FUEL_LEVEL_PROPERTY_KEY,
                MedHelicopterKeywords.MISSIONS_PROPERTY_KEY,
                MedHelicopterKeywords.NEEDS_REFUELING_PROPERTY_KEY,
                MedHelicopterKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY,
                MedHelicopterKeywords.TRIP_DISTANCE_PROPERTY_KEY
        };
        System.out.println("  [Operational Snapshot]");
        for (String key : keys) {
            try {
                state.getProperty(key).ifPresent(p ->
                        System.out.printf("    %-55s = %s%n", p.getKey(), p.getValue()));
            } catch (Exception ignored) {}
        }
    }

    private void printPayloadSummary(Object body) {
        if (body instanceof MedHelicopterTelemetryPayload p) {
            System.out.printf("    State: %-20s | Patient: %-15s | Hospital: %s%n",
                    p.state(), p.patientId(), p.hospitalId());
            System.out.printf("    Fuel: %.2f | Missions: %d | TripDist: %.1f m%n",
                    p.fuelLevel(), p.missionsSinceMaintenance(), p.tripDistanceSinceEmergency());
        } else {
            System.out.println("    Body: " + body);
        }
    }
}
