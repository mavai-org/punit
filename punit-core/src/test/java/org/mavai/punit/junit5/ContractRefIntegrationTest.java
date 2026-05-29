package org.mavai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.mavai.punit.junit5.testsubjects.PUnitSubjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Pins the audit-traceability contract for the typed builder's
 * {@code contractRef(...)}: the author-supplied reference must
 * surface on every verdict — pass or fail — so anyone reading the
 * output can trace the threshold back to its source document.
 *
 * <p>Renders are pinned in two places: the JUnit assertion message
 * (failure path) and the verbose statistical analysis emitted on
 * stderr (transparentStats path).
 */
@DisplayName("Contract reference — audit traceability on the verdict")
class ContractRefIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";
    private static final String EXPECTED_REF = "Acme API SLA v3.2 §2.1";

    private PrintStream originalErr;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void redirectStderr() {
        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("transparentStats output includes the contract reference")
    void renderedInTransparentStats() {
        Events events = run(PUnitSubjects.ContractRefPassingTest.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        String stderr = capturedErr.toString(StandardCharsets.UTF_8);
        assertThat(stderr).contains("Contract: " + EXPECTED_REF);
    }

    @Test
    @DisplayName("failure message includes the contract reference")
    void renderedInFailureMessage() {
        Events events = run(PUnitSubjects.ContractRefFailingTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));

        String failureMessage = events.failed().stream()
                .findFirst()
                .orElseThrow()
                .getPayload(org.junit.platform.engine.TestExecutionResult.class)
                .orElseThrow()
                .getThrowable()
                .orElseThrow()
                .getMessage();
        assertThat(failureMessage).contains("Contract: " + EXPECTED_REF);
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
