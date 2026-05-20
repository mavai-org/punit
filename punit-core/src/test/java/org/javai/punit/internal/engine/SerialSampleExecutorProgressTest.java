package org.javai.punit.internal.engine;

import static org.javai.punit.api.criterion.Criteria.meeting;
import org.javai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.javai.outcome.Outcome;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.ServiceContractOutcome;
import org.javai.punit.api.spec.SampleObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Progress-counter behavioural contract for {@link SerialSampleExecutor}.
 *
 * <p>Captures {@code System.out} during a controlled sample-loop run
 * and asserts the executor emits one {@code [PUNIT-PROGRESS] m/n\n}
 * line per sample, immediately flushed. The marker lets the
 * punit-gradle-plugin's {@code TestOutputListener} recognise
 * progress chunks on the receiving side of Gradle's test-stdout
 * pipe and reformat them as clean in-place updates on the build's
 * terminal.
 */
@DisplayName("SerialSampleExecutor — per-sample progress counter")
class SerialSampleExecutorProgressTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void redirectStdout() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    /** Service contract whose outcome alternates pass/fail by input parity. */
    private static final class AlternatingServiceContract implements ServiceContract<Void, Integer, String> {
        @Override public String id() { return "Alternating"; }
        @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
            return input % 2 == 0
                    ? Outcome.ok("even-" + input)
                    : Outcome.fail("odd", "input was " + input);
        }
        @Override public Criteria<String> criteria() {
            return meeting().<String>zeroFailures();
        }

    }

    private static final String MARKER = SerialSampleExecutor.SAMPLE_PROGRESS_MARKER;

    @Test
    @DisplayName("emits one [PUNIT-PROGRESS] m/n line after every sample, regardless of pass/fail")
    void emitsCounterPerSample() {
        runWith(new AlternatingServiceContract(), List.of(0, 1, 2, 3));

        // 4 samples → 4 newline-terminated lines, each prefixed with the marker.
        // System.out.println uses the platform line separator; assert using lines().
        String captured = this.captured.toString(StandardCharsets.UTF_8);
        assertThat(captured.lines().toList())
                .containsExactly(
                        MARKER + "1/4",
                        MARKER + "2/4",
                        MARKER + "3/4",
                        MARKER + "4/4");
    }

    @Test
    @DisplayName("width-pads the numerator to the decimal width of the total — keeps the "
            + "rendered counter aligned regardless of where the consumer renders it")
    void widthPadsNumerator() {
        runWith(new AlternatingServiceContract(), List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

        // total = 11 → width = 2; numerator padded with one leading space for single digits.
        assertThat(captured.toString(StandardCharsets.UTF_8).lines().toList())
                .containsExactly(
                        MARKER + " 1/11", MARKER + " 2/11", MARKER + " 3/11",
                        MARKER + " 4/11", MARKER + " 5/11", MARKER + " 6/11",
                        MARKER + " 7/11", MARKER + " 8/11", MARKER + " 9/11",
                        MARKER + "10/11", MARKER + "11/11");
    }

    @Test
    @DisplayName("a defect (service contract throws) still advances the counter")
    void defectAlsoAdvancesCounter() {
        ServiceContract<Void, Integer, String> alwaysThrows = new ServiceContract<>() {
            @Override public String id() { return "AlwaysThrows"; }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                throw new RuntimeException("synthetic defect");
            }
            @Override public Criteria<String> criteria() {
                return meeting().<String>zeroFailures();
            }

        };
        runWith(alwaysThrows, List.of(0, 1));

        assertThat(captured.toString(StandardCharsets.UTF_8).lines().toList())
                .as("defect path emits the same counter format as the normal path")
                .containsExactly(MARKER + "1/2", MARKER + "2/2");
    }

    @Test
    @DisplayName("each emission is flushed before the next sample begins — no buffering across "
            + "samples, which is the property the feature exists to provide")
    void eachEmissionFlushedImmediately() {
        ServiceContract<Void, Integer, String> counterPeek = new ServiceContract<>() {
            @Override public String id() { return "CounterPeek"; }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                // Sample-internal observable: line count visible inside this
                // sample's invoke. emitProgress is called *after*
                // observer.onSample, so for sample i (0-indexed) we expect
                // exactly i prior progress lines in the captured stream.
                long priorLines = captured.toString(StandardCharsets.UTF_8).lines().count();
                assertThat(priorLines).isEqualTo(input);
                return Outcome.ok("pass");
            }
            @Override public Criteria<String> criteria() {
                return meeting().<String>zeroFailures();
            }

        };
        runWith(counterPeek, List.of(0, 1, 2, 3));

        assertThat(captured.toString(StandardCharsets.UTF_8).lines().toList())
                .containsExactly(
                        MARKER + "1/4", MARKER + "2/4", MARKER + "3/4", MARKER + "4/4");
    }

    private void runWith(ServiceContract<Void, Integer, String> serviceContract, List<Integer> inputs) {
        new SerialSampleExecutor().runSamples(
                serviceContract,
                inputs,
                inputs.size(),
                0,
                new IgnoringObserver(),
                () -> false);
    }

    /** Observer that records nothing — the test asserts on stdout only. */
    private static final class IgnoringObserver implements SampleObserver<String> {
        @Override
        public void onSample(int index, ServiceContractOutcome<?, String> outcome, Duration elapsed) { }

        @Override
        public void onDefect(int index, Throwable throwable, Duration elapsed) { }
    }
}
