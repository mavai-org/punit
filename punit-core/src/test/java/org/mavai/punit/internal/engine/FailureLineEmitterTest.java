package org.mavai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.PostconditionResult;
import org.mavai.punit.api.spec.SampleClassification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioural contract for {@link FailureLineEmitter}. Captures
 * {@code System.err} during emission and asserts the
 * {@code [PUNIT-FAIL]} line shape — one line per failed clause,
 * single-line, kind-discriminated.
 */
@DisplayName("FailureLineEmitter — System.err line shape")
class FailureLineEmitterTest {

    private PrintStream originalErr;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void redirectStderr() {
        originalErr = System.err;
        captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("emits one line per failed postcondition with kind=postcondition")
    void postconditionFailures() {
        SampleClassification classification = SampleClassification.from(
                List.of(
                        PostconditionResult.passed("non-empty"),
                        PostconditionResult.failed("valid JSON", "Parse error at line 5"),
                        PostconditionResult.failed("contains action", "no 'action' key")),
                Optional.empty(),
                Duration.ofMillis(42),
                0L);

        FailureLineEmitter.emit(17, classification);

        assertThat(stderrLines())
                .containsExactly(
                        "[PUNIT-FAIL] sample 17 postcondition: valid JSON: Parse error at line 5",
                        "[PUNIT-FAIL] sample 17 postcondition: contains action: no 'action' key");
    }

    @Test
    @DisplayName("emits one apply: line for an apply-level Outcome.fail and skips postconditions")
    void applyLevelFailure() {
        SampleClassification classification = SampleClassification.failedAtApply(
                "timeout", "upstream did not respond within 5s",
                Duration.ofSeconds(5), 0L);

        FailureLineEmitter.emit(3, classification);

        assertThat(stderrLines())
                .containsExactly(
                        "[PUNIT-FAIL] sample 3 apply: timeout: upstream did not respond within 5s");
    }

    @Test
    @DisplayName("emits a defect: line when the apply-failure name is the engine sentinel 'defect'")
    void defectFailure() {
        SampleClassification classification = SampleClassification.failedAtApply(
                "defect", "java.lang.NullPointerException: cart is null",
                Duration.ofMillis(12), 0L);

        FailureLineEmitter.emit(8, classification);

        assertThat(stderrLines())
                .containsExactly(
                        "[PUNIT-FAIL] sample 8 defect: defect: "
                                + "java.lang.NullPointerException: cart is null");
    }

    @Test
    @DisplayName("a fully-passing classification emits nothing")
    void allPassingSilent() {
        SampleClassification classification = SampleClassification.from(
                List.of(PostconditionResult.passed("non-empty"),
                        PostconditionResult.passed("valid JSON")),
                Optional.empty(),
                Duration.ofMillis(40),
                0L);

        FailureLineEmitter.emit(0, classification);

        assertThat(stderrLines()).isEmpty();
    }

    @Test
    @DisplayName("collapses embedded newlines / carriage returns in the reason to single spaces — "
            + "preserves the one-line invariant for downstream tools")
    void collapsesEmbeddedNewlines() {
        Outcome<Object> failure = Outcome.fail("multi", "line one\nline two\rline three");
        PostconditionResult result = new PostconditionResult("multi-line clause", failure);
        SampleClassification classification = SampleClassification.from(
                List.of(result),
                Optional.empty(),
                Duration.ofMillis(1),
                0L);

        FailureLineEmitter.emit(5, classification);

        assertThat(stderrLines())
                .containsExactly(
                        "[PUNIT-FAIL] sample 5 postcondition: multi-line clause: "
                                + "line one line two line three");
    }

    private List<String> stderrLines() {
        return captured.toString(StandardCharsets.UTF_8).lines().toList();
    }
}
