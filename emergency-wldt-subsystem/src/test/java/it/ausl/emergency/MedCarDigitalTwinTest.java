package it.ausl.emergency;

import it.ausl.emergency.adapter.physical.MedCarPhysicalAdapter;
import it.ausl.emergency.payload.MedCarTelemetryPayload;
import it.ausl.emergency.shadowing.MedCarShadowingFunction;
import it.ausl.emergency.twin.MedCarDigitalTwin;
import it.ausl.emergency.utils.MedCarKeywords;
import it.wldt.core.engine.DigitalTwinEngine;
import it.wldt.exception.WldtEngineException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test JUnit 5 per il Digital Twin della MedCar.
 *
 * La sequenza di telemetrie replica i payload reali osservati nel dump della
 * simulazione AnyLogic (dump.txt) e copre tutti e cinque i Domain Events:
 *
 *   1. Mission Assigned      atRest → MovingToPatient
 *   2. On Scene Treating     MovingToPatient → TreatingPatient
 *   3. Mission Completed     TreatingPatient → Returning
 *   4. Critical Fuel Alert   fuelLevel scende sotto 0.20
 *   5. Maintenance Required  fronte di salita su needsMaintenance
 *
 * Nota: gli eventi 4 e 5 vengono testati in una seconda missione (fase 4/5)
 * per non interferire con il ciclo operativo principale.
 */
public class MedCarDigitalTwinTest {

    private static final String AGENT_ID = "CAR-08";

    private DigitalTwinEngine engine;
    private MedCarPhysicalAdapter physicalAdapter;
    private it.ausl.emergency.adapter.digital.MedCarDigitalAdapter digitalAdapter;

    @BeforeEach
    public void setUp() throws Exception {
        MedCarShadowingFunction shadowingFunction =
                new MedCarShadowingFunction("medcar-shadowing-" + AGENT_ID);
        MedCarDigitalTwin digitalTwin =
                new MedCarDigitalTwin("dt-" + AGENT_ID, shadowingFunction);

        physicalAdapter = digitalTwin.getPhysicalAdapter();
        digitalAdapter  = digitalTwin.getDigitalAdapter();

        engine = new DigitalTwinEngine();
        engine.addDigitalTwin(digitalTwin);
        engine.startAll();

        System.out.println("\n[TEST-SETUP] Engine avviato — attendo stato Shadowed...\n");

        boolean synced = digitalAdapter.getSyncLatch().await(10, TimeUnit.SECONDS);
        assertTrue(synced, "Il MedCar DT non ha raggiunto lo stato Shadowed entro 10 secondi");

        System.out.println("\n[TEST-SETUP] DT in stato Shadowed — test pronti.\n");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            try {
                engine.stopAll();
            } catch (WldtEngineException e) {
                e.printStackTrace();
            }
            System.out.println("\n[TEST-TEARDOWN] Engine fermato.\n");
        }
    }

    // ── Test principale ────────────────────────────────────────────────────────

    @Test
    public void testMedCarTwinIngestionAndDomainEvents() throws Exception {

        // ── FASE 1: atRest — stato iniziale ───────────────────────────────────
        System.out.println("[TEST] ═══ FASE 1: atRest ═══\n");
        injectAndWait(new MedCarTelemetryPayload(
                MedCarKeywords.STATE_AT_REST,
                44.49085796893278, 12.165022759344476,
                "null",
                "stationaryPoint9",
                1.0, 0, false, false,
                1761.340911964302, 0.0
        ));

        assertPropertyEquals(MedCarKeywords.STATE_PROPERTY_KEY,           MedCarKeywords.STATE_AT_REST);
        assertPropertyEquals(MedCarKeywords.FUEL_LEVEL_PROPERTY_KEY,      1.0);
        assertPropertyEquals(MedCarKeywords.HOME_BASE_ID_PROPERTY_KEY,    "stationaryPoint9");

        // Nessun evento in fase iniziale
        assertEquals(0, digitalAdapter.getMissionAssignedCount());
        assertEquals(0, digitalAdapter.getOnSceneTreatingCount());
        assertEquals(0, digitalAdapter.getMissionCompletedCount());

        // ── FASE 2: Domain Event 1 — Mission Assigned ─────────────────────────
        System.out.println("[TEST] ═══ FASE 2: MovingToPatient — Mission Assigned ═══\n");
        injectAndWait(new MedCarTelemetryPayload(
                MedCarKeywords.STATE_MOVING_TO_PATIENT,   // ← TRIGGER MISSION_ASSIGNED
                44.49085796893278, 12.165022759344476,
                "P-1456206",
                "stationaryPoint9",
                1.0, 0, false, false,
                1761.340911964302, 0.0
        ));

        assertPropertyEquals(MedCarKeywords.STATE_PROPERTY_KEY,      MedCarKeywords.STATE_MOVING_TO_PATIENT);
        assertPropertyEquals(MedCarKeywords.PATIENT_ID_PROPERTY_KEY, "P-1456206");
        assertEquals(1, digitalAdapter.getMissionAssignedCount(),
                "Atteso 1 evento MISSION_ASSIGNED");

        // ── FASE 3: Domain Event 2 — On Scene Treating ────────────────────────
        System.out.println("[TEST] ═══ FASE 3: TreatingPatient — On Scene ═══\n");
        injectAndWait(new MedCarTelemetryPayload(
                MedCarKeywords.STATE_TREATING_PATIENT,    // ← TRIGGER ON_SCENE_TREATING
                44.436486048034745, 12.146752675607036,
                "P-1456206",
                "stationaryPoint9",
                1.0, 0, false, false,
                2175.8260007354033, 6224.513635804533
        ));

        assertPropertyEquals(MedCarKeywords.STATE_PROPERTY_KEY, MedCarKeywords.STATE_TREATING_PATIENT);
        assertEquals(1, digitalAdapter.getOnSceneTreatingCount(),
                "Atteso 1 evento ON_SCENE_TREATING");

        // ── FASE 4: Domain Event 3 — Mission Completed ────────────────────────
        System.out.println("[TEST] ═══ FASE 4: Returning — Mission Completed ═══\n");
        injectAndWait(new MedCarTelemetryPayload(
                MedCarKeywords.STATE_RETURNING,           // ← TRIGGER MISSION_COMPLETED
                44.436486048034745, 12.146752675607036,
                "null",
                "stationaryPoint9",
                0.9377548636419547, 1, false, false,
                2797.946844383142, 12449.027271609066
        ));

        assertPropertyEquals(MedCarKeywords.STATE_PROPERTY_KEY,      MedCarKeywords.STATE_RETURNING);
        assertPropertyEquals(MedCarKeywords.PATIENT_ID_PROPERTY_KEY, "null");
        assertPropertyEquals(MedCarKeywords.MISSIONS_PROPERTY_KEY,   1);
        assertEquals(1, digitalAdapter.getMissionCompletedCount(),
                "Atteso 1 evento MISSION_COMPLETED");

        // ── FASE 5: Rientro a base ────────────────────────────────────────────
        System.out.println("[TEST] ═══ FASE 5: atRest — Rientro completato ═══\n");
        injectAndWait(new MedCarTelemetryPayload(
                MedCarKeywords.STATE_AT_REST,
                44.49085796893278, 12.165022759344476,
                "null",
                "stationaryPoint9",
                0.9377548636419547, 1, false, false,
                3268.8265484956955, 0.0
        ));

        assertPropertyEquals(MedCarKeywords.STATE_PROPERTY_KEY, MedCarKeywords.STATE_AT_REST);

        // ── FASE 6: Domain Event 4 — Critical Fuel (fronte di discesa) ────────
        System.out.println("[TEST] ═══ FASE 6: Critical Fuel ═══\n");
        injectAndWait(new MedCarTelemetryPayload(
                MedCarKeywords.STATE_MOVING_TO_PATIENT,
                44.20421471419251, 11.807563560101316,
                "P-9793493",
                "stationaryPoint6",
                0.19,              // ← sotto soglia 0.20: TRIGGER CRITICAL_FUEL
                2, false, false,
                10063.0, 29000.0
        ));

        assertEquals(1, digitalAdapter.getCriticalFuelCount(),
                "Atteso 1 evento CRITICAL_FUEL");

        // Un secondo aggiornamento con fuel ancora basso NON deve ri-scattare
        injectAndWait(new MedCarTelemetryPayload(
                MedCarKeywords.STATE_MOVING_TO_PATIENT,
                44.20421471419251, 11.807563560101316,
                "P-9793493",
                "stationaryPoint6",
                0.15,              // ancora sotto soglia: fronte già consumato
                2, false, false,
                10200.0, 30000.0
        ));
        assertEquals(1, digitalAdapter.getCriticalFuelCount(),
                "Il Critical Fuel NON deve ri-scattare se era già sotto soglia");

        // ── FASE 7: Domain Event 5 — Maintenance Required (fronte di salita) ──
        System.out.println("[TEST] ═══ FASE 7: Maintenance Required ═══\n");
        injectAndWait(new MedCarTelemetryPayload(
                MedCarKeywords.STATE_RETURNING,
                44.20421471419251, 11.807563560101316,
                "null",
                "stationaryPoint6",
                0.48778420010290346, 3,
                false,
                true,              // ← needsMaintenance true: TRIGGER MAINTENANCE_REQUIRED
                11817.021148977765, 58316.041803615095
        ));

        assertPropertyEquals(MedCarKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY, true);
        assertEquals(1, digitalAdapter.getMaintenanceRequiredCount(),
                "Atteso 1 evento MAINTENANCE_REQUIRED");

        // Riepilogo finale
        System.out.println("\n[TEST] ════════════════════════════════════════════════════════");
        System.out.println("[TEST]  Tutti i Domain Events verificati:");
        System.out.println("[TEST]    Mission Assigned      : " + digitalAdapter.getMissionAssignedCount());
        System.out.println("[TEST]    On Scene Treating     : " + digitalAdapter.getOnSceneTreatingCount());
        System.out.println("[TEST]    Mission Completed     : " + digitalAdapter.getMissionCompletedCount());
        System.out.println("[TEST]    Critical Fuel Alert   : " + digitalAdapter.getCriticalFuelCount());
        System.out.println("[TEST]    Maintenance Required  : " + digitalAdapter.getMaintenanceRequiredCount());
        System.out.println("[TEST] ════════════════════════════════════════════════════════\n");
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    private void injectAndWait(MedCarTelemetryPayload payload) throws InterruptedException {
        System.out.println("[MQTT-SIM] topic : ces/medcar/" + AGENT_ID + "/state");
        System.out.println("[MQTT-SIM] payload: " + buildMqttJson(payload) + "\n");
        physicalAdapter.onMedCarTelemetryReceived(payload);
        Thread.sleep(500);
    }

    private void assertPropertyEquals(String key, Object expected) {
        Object actual = digitalAdapter.getProperty(key)
                .orElseThrow(() -> new AssertionError(
                        "Proprietà non trovata nello snapshot: " + key));
        assertEquals(String.valueOf(expected), String.valueOf(actual),
                "Proprietà '" + key + "': atteso=" + expected + " effettivo=" + actual);
    }

    private String buildMqttJson(MedCarTelemetryPayload p) {
        return "{"
                + "\"state\":\"" + p.state() + "\","
                + "\"lat\":" + p.lat() + ","
                + "\"lon\":" + p.lon() + ","
                + "\"patientId\":\"" + p.patientId() + "\","
                + "\"homeBaseId\":\"" + p.homeBaseId() + "\","
                + "\"fuelLevel\":" + p.fuelLevel() + ","
                + "\"missionsSinceMaintenance\":" + p.missionsSinceMaintenance() + ","
                + "\"needsRefueling\":" + p.needsRefueling() + ","
                + "\"needsMaintenance\":" + p.needsMaintenance() + ","
                + "\"timestamp\":" + p.timestamp() + ","
                + "\"tripDistanceSinceEmergency\":" + p.tripDistanceSinceEmergency()
                + "}";
    }
}