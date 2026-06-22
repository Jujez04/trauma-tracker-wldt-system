package it.ausl.emergency;

import it.ausl.emergency.adapter.physical.patient.PatientMqttIngestionAdapter;
import it.ausl.emergency.manager.PatientTwinManager;
import it.ausl.emergency.payload.PatientTelemetryPayload;
import it.ausl.emergency.twin.PatientDigitalTwin;
import it.ausl.emergency.utils.PatientKeywords;
import it.wldt.core.engine.DigitalTwinEngine;
import it.wldt.core.state.DigitalTwinState;
import it.wldt.core.state.DigitalTwinStateProperty;
import it.wldt.exception.WldtEngineException;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test Suite for PatientTwinManager and PatientMqttIngestionAdapter.
 * Verified end-to-end against real AnyLogic simulation message contracts and keywords.
 * 
 * Note this: from test number 3 should be runned without the others due to race condition.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PatientTwinManagerTest {

    // ── Configuration Constants ──────────────────────────────────────────────

    private static final long BOOT_WAIT_MS        = 3_000L;
    private static final long PROPAGATION_MS      = 2_000L;
    private static final long HANDOVER_CLEANUP_MS = 12_000L;

    private static final String AGENT_A = "P-14523";
    private static final String AGENT_B = "P-14891";

    // ── Test Collaborators ───────────────────────────────────────────────────

    private DigitalTwinEngine engine;
    private PatientTwinManager manager;
    private PatientMqttIngestionAdapter ingestionAdapter;

    // ── JUnit Lifecycle Hooks ────────────────────────────────────────────────

    @BeforeEach
    public void setUp() {
        engine = new DigitalTwinEngine();
        manager = new PatientTwinManager(engine);
        // Initializing adapter with localhost broker string (isolated direct injection used in testing)
        ingestionAdapter = new PatientMqttIngestionAdapter("tcp://localhost:1883", manager);
        System.out.println("\n[TEST-SETUP] Processing Engine and Twin Manager successfully initialized.\n");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            try {
                engine.stopAll();
            } catch (WldtEngineException e) {
                e.printStackTrace();
            }
        }
        System.out.println("\n[TEST-TEARDOWN] Processing Engine environment context destroyed.\n");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEST 1 – Dynamic Twin Creation on First Inbound Message
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("First Inbound Message -> PatientDigitalTwin provisioned and added to active registry")
    public void testTwinCreatedOnFirstMessage() throws Exception {

        assertEquals(0, manager.activeTwinCount(),
                "The internal manager registry must be completely empty prior to initial telemetry ingestion.");

        manager.onTelemetryReceived(AGENT_A, buildDispatchPayload());
        waitForTwinSync(AGENT_A, 10_000);
        Thread.sleep(PROPAGATION_MS);

        assertEquals(1, manager.activeTwinCount(),
                "Exactly 1 active twin instance must reside in the manager registry after initial creation call.");

        PatientDigitalTwin twin = manager.getTwin(AGENT_A);
        assertNotNull(twin, "The provisioned twin reference for ID " + AGENT_A + " must be retrievable from registry.");

        assertTrue(engine.getDigitalTwinMap().containsKey("dt-" + AGENT_A),
                "The internal WLDT core map must explicitly contain the dynamically generated key 'dt-" + AGENT_A + "'");

        System.out.println("[TEST 1 ✓] Dynamic runtime twin provisioning successfully validated for target: " + AGENT_A);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEST 2 – Duplication Suppression on Concurrent Updates
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("Multiple continuous messages for same agentId -> Twin instance re-used without duplication")
    public void testNoDuplicateTwinForSameAgent() throws Exception {

        manager.onTelemetryReceived(AGENT_A, buildDispatchPayload());
        Thread.sleep(BOOT_WAIT_MS);

        PatientDigitalTwin twinAfterFirst = manager.getTwin(AGENT_A);
        assertNotNull(twinAfterFirst);

        // Feed subsequent clinical transitions to the exact same tracking target ID
        manager.onTelemetryReceived(AGENT_A, buildOnScenePayload());
        Thread.sleep(PROPAGATION_MS);
        manager.onTelemetryReceived(AGENT_A, buildTransportingPayload());
        Thread.sleep(PROPAGATION_MS);

        assertEquals(1, manager.activeTwinCount(),
                "The manager must suppress generation overhead: twin count must remain at 1.");

        PatientDigitalTwin twinAfterThird = manager.getTwin(AGENT_A);
        assertSame(twinAfterFirst, twinAfterThird,
                "The active manager context must reuse the identical memory reference across continuous state updates.");

        System.out.println("[TEST 2 ✓] Duplicate instance allocation successfully suppressed for target: " + AGENT_A);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEST 3 – State Boundaries Isolation for Independent Active Patients
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("Parallel Patients Telemetries Ingestion -> Complete state boundary isolation")
    public void testIndependentStateForTwoPatients() throws Exception {

        // Patient A: Dispatched status with initial stable parameters
        manager.onTelemetryReceived(AGENT_A, buildDispatchPayload());
        Thread.sleep(BOOT_WAIT_MS);

        // Patient B: Critical on-scene cardiac arrest event state
        manager.onTelemetryReceived(AGENT_B, buildCriticalOnScenePayload());
        Thread.sleep(BOOT_WAIT_MS);

        assertEquals(2, manager.activeTwinCount(),
                "The environment context must contain exactly 2 live independent twins.");
		Thread.sleep(PROPAGATION_MS);
        // Assert isolated state parameters for Patient A (Aligned with real AnyLogic Keywords constants)
        assertPropertyEquals(AGENT_A, PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_SIGNALED);
        assertPropertyEquals(AGENT_A, PatientKeywords.SEVERITY_CODE_PROPERTY_KEY, PatientKeywords.SEVERITY_YELLOW);
        assertPropertyEquals(AGENT_A, PatientKeywords.GCS_SCORE_PROPERTY_KEY, 15);

        // Assert isolated state parameters for Patient B (Ensuring uppercase alignment and no data-leakage)
        assertPropertyEquals(AGENT_B, PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_BEING_TREATED);
        assertPropertyEquals(AGENT_B, PatientKeywords.SEVERITY_CODE_PROPERTY_KEY, PatientKeywords.SEVERITY_RED);
        assertPropertyEquals(AGENT_B, PatientKeywords.GCS_SCORE_PROPERTY_KEY, 6);

        System.out.println("[TEST 3 ✓] State boundaries isolation verified successfully for: " + AGENT_A + " and " + AGENT_B);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEST 4 – Full Clinical Lifecycle Transition & Domain Event Edge Processing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("Sequential AnyLogic Telemetry Loop -> State updates mirror exact simulation timeline")
    public void testFullClinicalLifecycle() throws Exception {

        manager.onTelemetryReceived(AGENT_A, buildDispatchPayload());
        waitForTwinSync(AGENT_A, 10_000); 
        Thread.sleep(PROPAGATION_MS); 
        assertPropertyEquals(AGENT_A, PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_SIGNALED);

        // Step 2: Transition to BeingTreated -> Triggers Clinical Assessment Event
        manager.onTelemetryReceived(AGENT_A, buildOnScenePayload());
        Thread.sleep(PROPAGATION_MS);
        assertPropertyEquals(AGENT_A, PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_BEING_TREATED);
        assertPropertyEquals(AGENT_A, PatientKeywords.CONFIRMED_SEVERITY_CODE_PROPERTY_KEY, PatientKeywords.SEVERITY_YELLOW);

        // Step 3: Transition to MovingToHospital -> Triggers Clinical Deterioration Event
        manager.onTelemetryReceived(AGENT_A, buildTransportingPayload());
        Thread.sleep(PROPAGATION_MS);
        assertPropertyEquals(AGENT_A, PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_MOVING_TO_HOSPITAL);
        assertPropertyEquals(AGENT_A, PatientKeywords.CLINICAL_DETERIORATED_PROPERTY_KEY, true);

        // Step 4: Terminal Handover State Reached (Mapped onto STATE_AT_HOSPITAL)
        manager.onTelemetryReceived(AGENT_A, buildHandoverPayload());
        Thread.sleep(PROPAGATION_MS);
        assertPropertyEquals(AGENT_A, PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_AT_HOSPITAL);
        
        System.out.println("[TEST 4 ✓] Full clinical lifecycle workflow transitions verified successfully.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEST 5 – Garbage Collection Demarcation post-Handover Completion
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("Terminal State Handover -> Twin automatically evicted from registry after delay")
    @Timeout(20) 
    public void testRegistryCleanupAfterHandover() throws Exception {

        manager.onTelemetryReceived(AGENT_A, buildDispatchPayload());
        Thread.sleep(BOOT_WAIT_MS);
        assertEquals(1, manager.activeTwinCount());

        manager.onTelemetryReceived(AGENT_A, buildHandoverPayload());

        assertNotNull(manager.getTwin(AGENT_A),
                "The twin instance must not be purged instantly to ensure the completion of final logging sync routines.");

        // Block thread to wait for the background daemon garbage collection delay loop (10s + margin)
        Thread.sleep(HANDOVER_CLEANUP_MS);

        assertNull(manager.getTwin(AGENT_A),
                "The active registry map reference must be completely evicted post-handover cleanup execution.");
        assertEquals(0, manager.activeTwinCount(),
                "The active manager counter must safely return to 0.");

        assertTrue(engine.getDigitalTwinMap().containsKey("dt-" + AGENT_A),
                "The historical core representation must persist inside the WLDT engine structure for database audits.");

        System.out.println("[TEST 5 ✓] Asynchronous registry garbage collection successfully validated for: " + AGENT_A);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEST 6 – JSON Structural Mapping Contract Validation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("Inbound Ingestion Adapter -> Raw JSON payload mapping contract verification")
    public void testMqttJsonPayloadParsing() throws Exception {
        
        manager.onTelemetryReceived(AGENT_A, buildDispatchPayload());
        waitForTwinSync(AGENT_A, 10_000);
        
        String simulatedMqttTopic = "ces/patient/" + AGENT_A + "/state";
        String matchingAnyLogicJsonString = "{"
                + "\"state\":\"MovingToHospital\","
                + "\"severityCode\":\"RED\","
                + "\"confirmedSeverityCode\":\"YELLOW\","
                + "\"pathology\":\"SEVERE_TRAUMA\","
                + "\"gcsScore\":8,"
                + "\"isAirwayObstructed\":false,"
                + "\"hasExternalHemorrhage\":true,"
                + "\"isClinicalDeteriorated\":true,"
                + "\"lat\":44.152,"
                + "\"lon\":12.221,"
                + "\"timeCalled\":45.0"
                + "}";

        // Inject the simulated JSON payload directly into the uncoupled testing hook
        ingestionAdapter.injectMessageArrived(simulatedMqttTopic, matchingAnyLogicJsonString);
        Thread.sleep(PROPAGATION_MS);

        // Assert that the Jackson unmarshalling mapped parameters correctly to the active twin state
        assertPropertyEquals(AGENT_A, PatientKeywords.STATE_PROPERTY_KEY, PatientKeywords.STATE_MOVING_TO_HOSPITAL);
        assertPropertyEquals(AGENT_A, PatientKeywords.GCS_SCORE_PROPERTY_KEY, 8);
        assertPropertyEquals(AGENT_A, PatientKeywords.EXTERNAL_HEMORRHAGE_PROPERTY_KEY, true);
        assertPropertyEquals(AGENT_A, PatientKeywords.CLINICAL_DETERIORATED_PROPERTY_KEY, true);

        System.out.println("[TEST 6 ✓] Inbound string contract parsed and synchronized perfectly via Jackson deserialization mapping.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Simulation Telemetries Mock Builders (AnyLogic Real State Mappings)
    // ══════════════════════════════════════════════════════════════════════════

    private PatientTelemetryPayload buildDispatchPayload() {
        return new PatientTelemetryPayload(
                PatientKeywords.STATE_SIGNALED, PatientKeywords.SEVERITY_YELLOW, PatientKeywords.SEVERITY_WHITE, PatientKeywords.PATHOLOGY_NONE,
                15, false, false, false, 44.14123, 12.24567, 0.0);
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

    // ── Structural Framework Assertion Utilities ──────────────────────────────

    private <T> void assertPropertyEquals(String agentId, String propertyKey, T expectedValue)
            throws Exception {

        PatientDigitalTwin twin = manager.getTwin(agentId);
        assertNotNull(twin, "Twin reference not found inside active registry for target ID: " + agentId);

        // Extracting transactional state via your exposed getter method patch
        DigitalTwinState state = twin
                .getShadowingFunction()
                .getDigitalTwinStateManager()
                .getDigitalTwinState();

        assertNotNull(state, "Active DigitalTwinState core is null for target ID: " + agentId);

        Optional<DigitalTwinStateProperty<?>> optProp = state.getProperty(propertyKey);
        assertTrue(optProp.isPresent(),
                "Target property key '" + propertyKey + "' was not found in the initialized core state map for ID: " + agentId);

        Object actualValue = optProp.get().getValue();
        assertEquals(expectedValue, actualValue,
                "[" + agentId + "] Validation Mismatch for target property key '" + propertyKey
                        + "': expected=<" + expectedValue + ">, actual=<" + actualValue + ">");

        System.out.println("[ASSERT ✓] [" + agentId + "] " + propertyKey + " = " + actualValue);
    }

    private void waitForTwinSync(String agentId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            PatientDigitalTwin twin = manager.getTwin(agentId);
            if (twin != null) {
                DigitalTwinState state = twin
                        .getShadowingFunction()
                        .getDigitalTwinStateManager()
                        .getDigitalTwinState();

                if (state != null && state.getProperty(PatientKeywords.STATE_PROPERTY_KEY).isPresent()) {
                    System.out.println("[SYNC ✓] Ingestion pipeline successfully verified and synchronized for target: " + agentId);
                    return;
                }
            }
            Thread.sleep(200); 
        }
        fail("Timeout Error: The provisioned child instance '" + agentId + "' failed to initialize its tracking state map properties within " + timeoutMs + "ms");
    }
}