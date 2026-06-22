package it.ausl.emergency;

import it.ausl.emergency.adapter.physical.MedHelicopterPhysicalAdapter;
import it.ausl.emergency.payload.MedHelicopterTelemetryPayload;
import it.ausl.emergency.shadowing.MedHelicopterShadowingFunction;
import it.ausl.emergency.twin.MedHelicopterDigitalTwin;
import it.ausl.emergency.utils.MedHelicopterKeywords;
import it.wldt.core.engine.DigitalTwinEngine;
import it.wldt.exception.WldtEngineException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test JUnit 5 per il Digital Twin del MedHelicopter.
 *
 * La sequenza di telemetrie replica i payload reali osservati nel dump della
 * simulazione AnyLogic e copre tutti e cinque i Domain Events:
 *
 *   1. Mission Assigned      atRest → MovingToPatient
 *   2. Patient Onboard       TakingPatient → MovingToHospital
 *   3. Hospital Handover     * → Handover
 *   4. Critical Fuel Alert   fuelLevel scende sotto 0.20
 *   5. Maintenance Required  fronte di salita su needsMaintenance
 *
 * Nota: gli eventi 4 e 5 vengono testati in una seconda missione (fase 6/7)
 * per non interferire con il ciclo operativo principale.
 */
public class MedHelicopterDigitalTwinTest {

    private static final String AGENT_ID = "HELI-01";

    private DigitalTwinEngine engine;
    private MedHelicopterPhysicalAdapter physicalAdapter;
    private it.ausl.emergency.adapter.digital.MedHelicopterDigitalAdapter digitalAdapter;

    @BeforeEach
    public void setUp() throws Exception {
        MedHelicopterShadowingFunction shadowingFunction =
                new MedHelicopterShadowingFunction("medhelicopter-shadowing-" + AGENT_ID);
        MedHelicopterDigitalTwin digitalTwin =
                new MedHelicopterDigitalTwin("dt-" + AGENT_ID, shadowingFunction);

        physicalAdapter = digitalTwin.getPhysicalAdapter();
        digitalAdapter  = digitalTwin.getDigitalAdapter();

        engine = new DigitalTwinEngine();
        engine.addDigitalTwin(digitalTwin);
        engine.startAll();

        System.out.println("\n[TEST-SETUP] Engine avviato — attendo stato Shadowed...\n");

        boolean synced = digitalAdapter.getSyncLatch().await(10, TimeUnit.SECONDS);
        assertTrue(synced, "Il MedHelicopter DT non ha raggiunto lo stato Shadowed entro 10 secondi");

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
    public void testMedHelicopterTwinIngestionAndDomainEvents() throws Exception {

        // ── FASE 1: atRest — stato iniziale ───────────────────────────────────
        System.out.println("[TEST] ═══ FASE 1: atRest ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_AT_REST,
                44.49085796893278, 12.165022759344476,
                "null",
                "null",
                1.0, 0, false, false,
                1000.0, 0.0
        ));

        assertPropertyEquals(MedHelicopterKeywords.STATE_PROPERTY_KEY,      MedHelicopterKeywords.STATE_AT_REST);
        assertPropertyEquals(MedHelicopterKeywords.FUEL_LEVEL_PROPERTY_KEY, 1.0);

        // Nessun evento in fase iniziale
        assertEquals(0, digitalAdapter.getMissionAssignedCount());
        assertEquals(0, digitalAdapter.getPatientOnboardCount());
        assertEquals(0, digitalAdapter.getHospitalHandoverCount());

        // ── FASE 2: Domain Event 1 — Mission Assigned ─────────────────────────
        System.out.println("[TEST] ═══ FASE 2: MovingToPatient — Mission Assigned ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_MOVING_TO_PATIENT,    // ← TRIGGER MISSION_ASSIGNED
                44.49085796893278, 12.165022759344476,
                "P-1456206",
                "null",
                1.0, 0, false, false,
                1200.0, 0.0
        ));

        assertPropertyEquals(MedHelicopterKeywords.STATE_PROPERTY_KEY,      MedHelicopterKeywords.STATE_MOVING_TO_PATIENT);
        assertPropertyEquals(MedHelicopterKeywords.PATIENT_ID_PROPERTY_KEY, "P-1456206");
        assertEquals(1, digitalAdapter.getMissionAssignedCount(),
                "Atteso 1 evento MISSION_ASSIGNED");

        // ── FASE 3: TakingPatient — elicottero sul posto ───────────────────────
        System.out.println("[TEST] ═══ FASE 3: TakingPatient ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_TAKING_PATIENT,
                44.436486048034745, 12.146752675607036,
                "P-1456206",
                "null",
                0.95, 0, false, false,
                2000.0, 8000.0
        ));

        assertPropertyEquals(MedHelicopterKeywords.STATE_PROPERTY_KEY, MedHelicopterKeywords.STATE_TAKING_PATIENT);
        // Nessun nuovo evento ancora
        assertEquals(0, digitalAdapter.getPatientOnboardCount());

        // ── FASE 4: Domain Event 2 — Patient Onboard ──────────────────────────
        System.out.println("[TEST] ═══ FASE 4: MovingToHospital — Patient Onboard ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_MOVING_TO_HOSPITAL,   // ← TRIGGER PATIENT_ONBOARD
                44.436486048034745, 12.146752675607036,
                "P-1456206",
                "hospitalCesena",
                0.92, 0, false, false,
                2500.0, 8000.0
        ));

        assertPropertyEquals(MedHelicopterKeywords.STATE_PROPERTY_KEY,       MedHelicopterKeywords.STATE_MOVING_TO_HOSPITAL);
        assertPropertyEquals(MedHelicopterKeywords.HOSPITAL_ID_PROPERTY_KEY, "hospitalCesena");
        assertEquals(1, digitalAdapter.getPatientOnboardCount(),
                "Atteso 1 evento PATIENT_ONBOARD");

        // ── FASE 5: Domain Event 3 — Hospital Handover ────────────────────────
        System.out.println("[TEST] ═══ FASE 5: Handover — Hospital Handover ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_HANDOVER,              // ← TRIGGER HOSPITAL_HANDOVER
                44.13484517784122, 12.259845078918191,
                "P-1456206",
                "hospitalCesena",
                0.88, 1, false, false,
                3200.0, 12449.0
        ));

        assertPropertyEquals(MedHelicopterKeywords.STATE_PROPERTY_KEY,    MedHelicopterKeywords.STATE_HANDOVER);
        assertPropertyEquals(MedHelicopterKeywords.MISSIONS_PROPERTY_KEY, 1);
        assertEquals(1, digitalAdapter.getHospitalHandoverCount(),
                "Atteso 1 evento HOSPITAL_HANDOVER");

        // ── FASE 5b: completamento ciclo — Sanitizing → Returning → atRest ───
        System.out.println("[TEST] ═══ FASE 5b: Sanitizing ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_SANITIZING,
                44.13484517784122, 12.259845078918191,
                "P-1456206",
                "hospitalCesena",
                0.88, 1, false, false,
                3500.0, 12449.0
        ));

        System.out.println("[TEST] ═══ FASE 5c: Returning ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_RETURNING,
                44.13484517784122, 12.259845078918191,
                "null",
                "null",
                0.85, 1, false, false,
                4000.0, 12449.0
        ));

        System.out.println("[TEST] ═══ FASE 5d: atRest — rientro completato ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_AT_REST,
                44.49085796893278, 12.165022759344476,
                "null",
                "null",
                0.85, 1, false, false,
                5000.0, 0.0
        ));

        assertPropertyEquals(MedHelicopterKeywords.STATE_PROPERTY_KEY, MedHelicopterKeywords.STATE_AT_REST);

        // ── FASE 6: Domain Event 4 — Critical Fuel (fronte di discesa) ────────
        System.out.println("[TEST] ═══ FASE 6: Critical Fuel ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_MOVING_TO_PATIENT,
                44.20421471419251, 11.807563560101316,
                "P-9793493",
                "null",
                0.19,              // ← sotto soglia 0.20: TRIGGER CRITICAL_FUEL
                2, false, false,
                10063.0, 29000.0
        ));

        assertEquals(1, digitalAdapter.getCriticalFuelCount(),
                "Atteso 1 evento CRITICAL_FUEL");

        // Un secondo aggiornamento con fuel ancora basso NON deve ri-scattare
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_MOVING_TO_PATIENT,
                44.20421471419251, 11.807563560101316,
                "P-9793493",
                "null",
                0.15,              // ancora sotto soglia: fronte già consumato
                2, false, false,
                10200.0, 30000.0
        ));
        assertEquals(1, digitalAdapter.getCriticalFuelCount(),
                "Il Critical Fuel NON deve ri-scattare se era già sotto soglia");

        // ── FASE 7: Domain Event 5 — Maintenance Required (fronte di salita) ──
        System.out.println("[TEST] ═══ FASE 7: Maintenance Required ═══\n");
        injectAndWait(new MedHelicopterTelemetryPayload(
                MedHelicopterKeywords.STATE_RETURNING,
                44.20421471419251, 11.807563560101316,
                "null",
                "null",
                0.50, 3,
                false,
                true,              // ← needsMaintenance true: TRIGGER MAINTENANCE_REQUIRED
                11817.0, 58316.0
        ));

        assertPropertyEquals(MedHelicopterKeywords.NEEDS_MAINTENANCE_PROPERTY_KEY, true);
        assertEquals(1, digitalAdapter.getMaintenanceRequiredCount(),
                "Atteso 1 evento MAINTENANCE_REQUIRED");

        // Riepilogo finale
        System.out.println("\n[TEST] ════════════════════════════════════════════════════════");
        System.out.println("[TEST]  Tutti i Domain Events verificati:");
        System.out.println("[TEST]    Mission Assigned      : " + digitalAdapter.getMissionAssignedCount());
        System.out.println("[TEST]    Patient Onboard       : " + digitalAdapter.getPatientOnboardCount());
        System.out.println("[TEST]    Hospital Handover     : " + digitalAdapter.getHospitalHandoverCount());
        System.out.println("[TEST]    Critical Fuel Alert   : " + digitalAdapter.getCriticalFuelCount());
        System.out.println("[TEST]    Maintenance Required  : " + digitalAdapter.getMaintenanceRequiredCount());
        System.out.println("[TEST] ════════════════════════════════════════════════════════\n");
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    private void injectAndWait(MedHelicopterTelemetryPayload payload) throws InterruptedException {
        System.out.println("[MQTT-SIM] topic : ces/medhelicopter/" + AGENT_ID + "/state");
        System.out.println("[MQTT-SIM] payload: " + buildMqttJson(payload) + "\n");
        physicalAdapter.onMedHelicopterTelemetryReceived(payload);
        Thread.sleep(500);
    }

    private void assertPropertyEquals(String key, Object expected) {
        Object actual = digitalAdapter.getProperty(key)
                .orElseThrow(() -> new AssertionError(
                        "Proprietà non trovata nello snapshot: " + key));
        assertEquals(String.valueOf(expected), String.valueOf(actual),
                "Proprietà '" + key + "': atteso=" + expected + " effettivo=" + actual);
    }

    private String buildMqttJson(MedHelicopterTelemetryPayload p) {
        return "{"
                + "\"state\":\"" + p.state() + "\","
                + "\"lat\":" + p.lat() + ","
                + "\"lon\":" + p.lon() + ","
                + "\"patientId\":\"" + p.patientId() + "\","
                + "\"hospitalId\":\"" + p.hospitalId() + "\","
                + "\"fuelLevel\":" + p.fuelLevel() + ","
                + "\"missionsSinceMaintenance\":" + p.missionsSinceMaintenance() + ","
                + "\"needsRefueling\":" + p.needsRefueling() + ","
                + "\"needsMaintenance\":" + p.needsMaintenance() + ","
                + "\"timestamp\":" + p.timestamp() + ","
                + "\"tripDistanceSinceEmergency\":" + p.tripDistanceSinceEmergency()
                + "}";
    }
}
