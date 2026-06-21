package it.ausl.emergency;

import it.ausl.emergency.adapter.physical.PatientPhysicalAdapter;
import it.ausl.emergency.model.payload.PatientTelemetryPayload;
import it.ausl.emergency.shadowing.PatientShadowingFunction;
import it.ausl.emergency.twin.PatientDigitalTwin;
import it.ausl.emergency.utils.PatientKeywords;
import it.wldt.core.engine.DigitalTwinEngine;
import it.wldt.core.state.DigitalTwinState;
import it.wldt.core.state.DigitalTwinStateProperty;
import it.wldt.exception.WldtEngineException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PatientDigitalTwinTest {

    // ── Configuration Constants ──────────────────────────────────────────────

    private static final String AGENT_ID = "patient-001";
    private static final long PROPAGATION_MS = 2_000L;
    private static final long BOOT_WAIT_MS = 3_000L;

    // ── Test Collaborators ───────────────────────────────────────────────────

    private DigitalTwinEngine engine;
    private PatientDigitalTwin digitalTwin;
    private PatientPhysicalAdapter physicalAdapter;

    // ── JUnit Lifecycle Hooks ────────────────────────────────────────────────

    @BeforeEach
    public void setUp() throws Exception {
        PatientShadowingFunction shadowingFunction =
                new PatientShadowingFunction("patient-shadowing-" + AGENT_ID);

        // Custom twin instance automatically handles internal adapters registration
        digitalTwin = new PatientDigitalTwin("dt-" + AGENT_ID, shadowingFunction);
        physicalAdapter = digitalTwin.getPhysicalAdapter();

        engine = new DigitalTwinEngine();
        engine.addDigitalTwin(digitalTwin);
        engine.startAll();

        System.out.println("\n[TEST-SETUP] PatientDigitalTwin Engine started. Waiting for adapter binding warmup...\n");
        Thread.sleep(BOOT_WAIT_MS);
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            try {
                engine.stopAll();
            } catch (WldtEngineException e) {
                e.printStackTrace();
            }
            System.out.println("\n[TEST-TEARDOWN] PatientDigitalTwin Engine execution context stopped.\n");
        }
    }

    // ── Core Lifecycle Test Case ─────────────────────────────────────────────

    /**
     * Simulates a full sequential emergency mission timeline:
     * Dispatch → On Scene → In Transit (Clinical Deterioration) → Stabilization → Handover.
     * Verifies that the transactional state engine maps properties correctly according to custom keywords.
     */
    @Test
    public void testClinicalMissionLifecycleAndDomainEvents() throws Exception {

        // ── PHASE 1: Dispatch ──────────────────────────────────────────────────
        log("PHASE 1: Dispatch Request Ingested");
        injectTelemetry(buildDispatchPayload()); // Using the correct helper mapping

        Thread.sleep(PROPAGATION_MS);
        assertPropertyEquals(PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_SIGNALED);
        assertPropertyEquals(PatientKeywords.SEVERITY_CODE_PROPERTY_KEY, PatientKeywords.SEVERITY_YELLOW);

        // ── PHASE 2: On Scene – Clinical Assessment Performed ─────────────────
        log("PHASE 2: On Scene – Clinical Assessment Performed");
        injectTelemetry(buildOnScenePayload()); // Using the correct helper mapping

        Thread.sleep(PROPAGATION_MS);
        assertPropertyEquals(PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_BEING_TREATED);
        assertPropertyEquals(PatientKeywords.CONFIRMED_SEVERITY_CODE_PROPERTY_KEY, PatientKeywords.SEVERITY_YELLOW);
        assertPropertyEquals(PatientKeywords.PATHOLOGY_PROPERTY_KEY, PatientKeywords.PATHOLOGY_SEVERE_TRAUMA);

        // ── PHASE 3: In Transit – Clinical Deterioration Detected ─────────────
        log("PHASE 3: In Transit – Clinical Deterioration Detected");
        injectTelemetry(buildTransportingPayload()); // Using the correct helper mapping

        Thread.sleep(PROPAGATION_MS);
        assertPropertyEquals(PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_MOVING_TO_HOSPITAL);
        assertPropertyEquals(PatientKeywords.GCS_SCORE_PROPERTY_KEY, 7);
        assertPropertyEquals(PatientKeywords.CLINICAL_DETERIORATED_PROPERTY_KEY, true);

        // ── PHASE 4: Stabilization ───────────────────────────────────────────
        log("PHASE 4: Advanced Stabilization inside Ambulance");
        // Inline update to test runtime recovery variations (GCS improves, airways cleared)
        injectTelemetry(new PatientTelemetryPayload(
                PatientKeywords.STATE_MOVING_TO_HOSPITAL, 
                PatientKeywords.SEVERITY_RED, 
                PatientKeywords.SEVERITY_YELLOW, 
                PatientKeywords.PATHOLOGY_SEVERE_TRAUMA,
                9, 
                false, 
                false, 
                true, 
                44.15200, 
                12.22100, 
                25.0
        ));

        Thread.sleep(PROPAGATION_MS);
        assertPropertyEquals(PatientKeywords.GCS_SCORE_PROPERTY_KEY, 9);
        assertPropertyEquals(PatientKeywords.AIRWAY_OBSTRUCTED_PROPERTY_KEY, false);

        // ── PHASE 5: Handover Completed ───────────────────────────────────────
        log("PHASE 5: Destination Reached – Handover Completed");
        injectTelemetry(buildHandoverPayload()); // Using the correct helper mapping

        Thread.sleep(PROPAGATION_MS);
        assertPropertyEquals(PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_AT_HOSPITAL);

        System.out.println("\n[TEST] === All core transitions successfully synchronized and verified. ===\n");
    }

    // ── Telemetry Injection Framework Mocks ──────────────────────────────────

    private void injectTelemetry(PatientTelemetryPayload payload) {
        try {
            String simulatedJson = buildMqttJson(payload);
            System.out.println("[ANYLOGIC-MQTT-SIM] topic  : ces/patient/" + AGENT_ID + "/state");
            System.out.println("[ANYLOGIC-MQTT-SIM] payload: " + simulatedJson + "\n");

            physicalAdapter.onPatientTelemetryReceived(payload);

        } catch (Exception e) {
            fail("Exception caught during automated telemetry injection: " + e.getMessage());
        }
    }

    private PatientTelemetryPayload buildDispatchPayload() {
        return new PatientTelemetryPayload(
                PatientKeywords.STATE_SIGNALED, PatientKeywords.SEVERITY_YELLOW, PatientKeywords.SEVERITY_WHITE, PatientKeywords.PATHOLOGY_NONE,
                15, false, false, false, 44.14123, 12.24567, 0.0);
    }

    private PatientTelemetryPayload buildWaitingSupportPayload() {
        return new PatientTelemetryPayload(
                PatientKeywords.STATE_WAITING_SUPPORT, PatientKeywords.SEVERITY_YELLOW, PatientKeywords.SEVERITY_WHITE, PatientKeywords.PATHOLOGY_NONE,
                15, false, false, false, 44.14123, 12.24567, 3.0);
    }

    private PatientTelemetryPayload buildOnScenePayload() {
        return new PatientTelemetryPayload(
                PatientKeywords.STATE_BEING_TREATED, PatientKeywords.SEVERITY_YELLOW, PatientKeywords.SEVERITY_YELLOW, PatientKeywords.PATHOLOGY_SEVERE_TRAUMA,
                11, true, true, false, 44.14123, 12.24567, 5.0);
    }

    private PatientTelemetryPayload buildTransportingPayload() {
        return new PatientTelemetryPayload(
                PatientKeywords.STATE_MOVING_TO_HOSPITAL, PatientKeywords.SEVERITY_RED, PatientKeywords.SEVERITY_YELLOW, PatientKeywords.PATHOLOGY_SEVERE_TRAUMA,
                7, true, true, true, 44.14500, 12.23900, 18.0);
    }

    private PatientTelemetryPayload buildHandoverPayload() {
        return new PatientTelemetryPayload(
                PatientKeywords.STATE_AT_HOSPITAL, PatientKeywords.SEVERITY_RED, PatientKeywords.SEVERITY_YELLOW, PatientKeywords.PATHOLOGY_SEVERE_TRAUMA,
                7, true, true, true, 44.13900, 12.25300, 38.0);
    }

    private PatientTelemetryPayload buildCriticalOnScenePayload() {
        return new PatientTelemetryPayload(
                PatientKeywords.STATE_BEING_TREATED, PatientKeywords.SEVERITY_RED, PatientKeywords.SEVERITY_RED, PatientKeywords.PATHOLOGY_CARDIAC_ARREST,
                6, true, true, false, 44.15000, 12.23000, 3.0);
    }

    private String buildMqttJson(PatientTelemetryPayload p) {
        return "{"
                + "\"state\":\""               + p.state()               + "\","
                + "\"severityCode\":\""         + p.severityCode()        + "\","
                + "\"confirmedSeverityCode\":\"" + (p.confirmedSeverityCode() != null ? p.confirmedSeverityCode() : "null") + "\","
                + "\"pathology\":\""            + p.pathology()           + "\","
                + "\"gcsScore\":"               + p.gcsScore()            + ","
                + "\"isAirwayObstructed\":"     + p.isAirwayObstructed()  + ","
                + "\"hasExternalHemorrhage\":"  + p.hasExternalHemorrhage() + ","
                + "\"isClinicalDeteriorated\":" + p.isClinicalDeteriorated() + ","
                + "\"lat\":"                    + p.lat()                 + ","
                + "\"lon\":"                    + p.lon()                 + ","
                + "\"timeCalled\":"             + p.timeCalled()
                + "}";
    }

    // ── State Assertion Extraction Utilities ─────────────────────────────────

    private <T> void assertPropertyEquals(String propertyKey, T expectedValue) throws Exception {
        DigitalTwinState state = digitalTwin
                .getShadowingFunction()
                .getDigitalTwinStateManager()
                .getDigitalTwinState();

        assertNotNull(state, "The current DigitalTwinState core runtime object cannot be null.");

        Optional<DigitalTwinStateProperty<?>> optProp = state.getProperty(propertyKey);
        assertTrue(optProp.isPresent(), "Property key '" + propertyKey + "' was not found inside the active core map.");

        Object actualValue = optProp.get().getValue();
        assertEquals(expectedValue, actualValue, "Value boundary verification failure for property key: '" + propertyKey + "'");

        System.out.println("[ASSERT] Property verified: " + propertyKey + " = " + actualValue);
    }

    private static void log(String phase) {
        System.out.println("\n[TEST] == " + phase.toUpperCase() + " ==========================\n");
    }
}