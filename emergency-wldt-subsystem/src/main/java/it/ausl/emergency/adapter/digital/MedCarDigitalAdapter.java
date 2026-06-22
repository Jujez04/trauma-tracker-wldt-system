package it.ausl.emergency.adapter.digital;

import it.ausl.emergency.adapter.configuration.MedCarAdapterConfiguration;
import it.ausl.emergency.payload.MedCarTelemetryPayload;
import it.ausl.emergency.utils.MedCarKeywords;
import it.wldt.adapter.digital.DigitalAdapter;
import it.wldt.core.state.*;
import it.wldt.exception.EventBusException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Digital Adapter della MedCar.
 *
 * Espone lo stato operativo del mezzo verso l'esterno (dashboard, test, servizi).
 * Segue lo stesso pattern del PatientDigitalAdapter:
 *  - ConcurrentHashMap come snapshot leggibile dall'esterno
 *  - CountDownLatch per la sincronizzazione con i test JUnit
 *  - Contatori per ogni Domain Event ricevuto
 *
 * La MedCar non accetta azioni digitali: onDigitalActionEvent è no-op.
 */
public class MedCarDigitalAdapter extends DigitalAdapter<MedCarAdapterConfiguration> {

    // Snapshot aggiornato ad ogni onStateUpdate() — leggibile dai test
    private final ConcurrentHashMap<String, Object> propertySnapshot = new ConcurrentHashMap<>();

    // Si sblocca quando il DT raggiunge lo stato Shadowed
    private final CountDownLatch syncLatch = new CountDownLatch(1);

    // Contatori dei Domain Events — utili per le assert nei test
    private volatile int missionAssignedCount    = 0;
    private volatile int onSceneTreatingCount    = 0;
    private volatile int missionCompletedCount   = 0;
    private volatile int criticalFuelCount       = 0;
    private volatile int maintenanceRequiredCount = 0;

    public MedCarDigitalAdapter(String id, MedCarAdapterConfiguration configuration) {
        super(id, configuration);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onAdapterStart() {
        System.out.println("[MedCarDigitalAdapter] -> onAdapterStart(): " + getId());
    }

    @Override
    public void onAdapterStop() {
        System.out.println("[MedCarDigitalAdapter] -> onAdapterStop(): " + getId());
    }

    // ── DT Life Cycle callbacks ───────────────────────────────────────────────

    @Override
    public void onDigitalTwinCreate() {
        System.out.println("[MedCarDigitalAdapter] -> Twin registered in engine.");
    }

    @Override
    public void onDigitalTwinStart() {
        System.out.println("[MedCarDigitalAdapter] -> Processing layer active.");
    }

    @Override
    public void onDigitalTwinSync(DigitalTwinState currentState) {
        System.out.println("[MedCarDigitalAdapter] -> Synchronization achieved.");
        refreshSnapshot(currentState);
        printStateSnapshot("INITIAL SYNCHRONIZED MEDCAR STATE", currentState);

        // Iscrizione a tutti gli eventi dichiarati nella DT State
        try {
            currentState.getEventList()
                    .map(list -> list.stream()
                            .map(DigitalTwinStateEvent::getKey)
                            .collect(Collectors.toList()))
                    .ifPresent(keys -> {
                        try {
                            observeDigitalTwinEventsNotifications(keys);
                            System.out.println("[MedCarDigitalAdapter] -> Observing events: " + keys);
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
        System.out.println("[MedCarDigitalAdapter] -> Warning: MedCar Twin desynchronized.");
    }

    @Override
    public void onDigitalTwinStop() {
        System.out.println("[MedCarDigitalAdapter] -> Monitoring suspended.");
    }

    @Override
    public void onDigitalTwinDestroy() {
        System.out.println("[MedCarDigitalAdapter] -> Twin destroyed.");
    }

    // ── State Update ──────────────────────────────────────────────────────────

    @Override
    protected void onStateUpdate(DigitalTwinState newState,
                                 DigitalTwinState previousState,
                                 ArrayList<DigitalTwinStateChange> changes) {

        System.out.println("\n[MedCarDigitalAdapter] ─── STATE UPDATE ───────────────────────");

        if (changes != null && !changes.isEmpty()) {
            changes.forEach(c -> System.out.printf("  [%s] %s -> %s%n",
                    c.getOperation(), c.getResourceType(), c.getResource()));
        } else {
            System.out.println("  (no changes detected)");
        }

        refreshSnapshot(newState);
        printOperationalSnapshot(newState);
        System.out.println("───────────────────────────────────────────────────────────────\n");
    }

    // ── Domain Event Callbacks ────────────────────────────────────────────────

    @Override
    protected void onEventNotificationReceived(DigitalTwinStateEventNotification<?> notification) {
        if (notification == null) return;

        String eventKey = notification.getDigitalEventKey();
        Object body     = notification.getBody();

        System.out.println("\n[MedCarDigitalAdapter] ══ DOMAIN EVENT ══════════════════════════");
        System.out.println("  Event Key : " + eventKey);

        if (MedCarKeywords.MISSION_ASSIGNED_EVENT_KEY.equals(eventKey)) {
            missionAssignedCount++;
            System.out.println("  ► MISSION ASSIGNED — MedCar dispatched to patient  (#" + missionAssignedCount + ")");
            printPayloadSummary(body);

        } else if (MedCarKeywords.ON_SCENE_TREATING_EVENT_KEY.equals(eventKey)) {
            onSceneTreatingCount++;
            System.out.println("  ► ON SCENE — Advanced treatment started  (#" + onSceneTreatingCount + ")");
            printPayloadSummary(body);

        } else if (MedCarKeywords.MISSION_COMPLETED_EVENT_KEY.equals(eventKey)) {
            missionCompletedCount++;
            System.out.println("  ► MISSION COMPLETED — MedCar returning to base  (#" + missionCompletedCount + ")");
            printPayloadSummary(body);

        } else if (MedCarKeywords.CRITICAL_FUEL_EVENT_KEY.equals(eventKey)) {
            criticalFuelCount++;
            System.out.println("  ► CRITICAL FUEL RESERVE ⚠  (#" + criticalFuelCount + ")");
            printPayloadSummary(body);

        } else if (MedCarKeywords.MAINTENANCE_REQUIRED_EVENT_KEY.equals(eventKey)) {
            maintenanceRequiredCount++;
            System.out.println("  ► MAINTENANCE REQUIRED ⚠  (#" + maintenanceRequiredCount + ")");
            printPayloadSummary(body);

        } else {
            System.out.println("  (unhandled event: " + eventKey + ")");
        }

        System.out.println("═════════════════════════════════════════════════════════════════\n");
    }

    // ── API pubblica per i test ───────────────────────────────────────────────

    /** Valore corrente di una proprietà del DT State. */
    public Optional<Object> getProperty(String key) {
        return Optional.ofNullable(propertySnapshot.get(key));
    }

    /** Latch sbloccato quando il DT è in stato Shadowed. */
    public CountDownLatch getSyncLatch() { return syncLatch; }

    public int getMissionAssignedCount()    { return missionAssignedCount; }
    public int getOnSceneTreatingCount()    { return onSceneTreatingCount; }
    public int getMissionCompletedCount()   { return missionCompletedCount; }
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
            System.err.println("[MedCarDigitalAdapter] Snapshot refresh error: " + e.getMessage());
        }
    }

    private void printStateSnapshot(String title, DigitalTwinState state) {
        System.out.println("\n[MedCarDigitalAdapter] ── " + title + " ──");
        if (state == null) return;
        try {
            state.getPropertyList().ifPresent(props ->
                    props.forEach(p -> System.out.printf(
                            "  [PROP] %-50s = %s%n", p.getKey(), p.getValue())));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }

    private void printOperationalSnapshot(DigitalTwinState state) {
        if (state == null) return;
        String[] keys = {
                MedCarKeywords.STATE_PROPERTY_KEY,
                MedCarKeywords.PATIENT_ID_PROPERTY_KEY,
                MedCarKeywords.HOME_BASE_ID_PROPERTY_KEY,
                MedCarKeywords.FUEL_LEVEL_PROPERTY_KEY,
                MedCarKeywords.MISSIONS_PROPERTY_KEY,
                MedCarKeywords.NEEDS_REFUELING_PROPERTY_KEY,
                MedCarKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY,
                MedCarKeywords.TRIP_DISTANCE_PROPERTY_KEY
        };
        System.out.println("  [Operational Snapshot]");
        for (String key : keys) {
            try {
                state.getProperty(key).ifPresent(p ->
                        System.out.printf("    %-52s = %s%n", p.getKey(), p.getValue()));
            } catch (Exception ignored) {}
        }
    }

    private void printPayloadSummary(Object body) {
        if (body instanceof MedCarTelemetryPayload p) {
            System.out.printf("    State: %-20s | Patient: %-15s | HomeBase: %s%n",
                    p.state(), p.patientId(), p.homeBaseId());
            System.out.printf("    Fuel: %.2f | Missions: %d | TripDist: %.1f m%n",
                    p.fuelLevel(), p.missionsSinceMaintenance(), p.tripDistanceSinceEmergency());
        } else {
            System.out.println("    Body: " + body);
        }
    }
}