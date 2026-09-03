package org.mavai.punit.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * The wait this reader owns: how long it holds a sample open before
 * recording a failed delivery, what it states when that wait elapses,
 * and why the resolved value is always part of what was measured.
 */
@DisplayName("the client deadline")
class ClientDeadlineTest {

    private final ServiceType type = new LanguageModelServiceType();
    private StubLlm stub;

    @BeforeEach
    void start() {
        stub = StubLlm.start();
        System.setProperty("mavai.llm.endpoint", stub.endpoint());
        System.setProperty("mavai.llm.api-key", "test-key");
    }

    @AfterEach
    void stop() {
        stub.close();
        System.clearProperty("mavai.llm.endpoint");
        System.clearProperty("mavai.llm.api-key");
    }

    private ConfiguredService service(Object... pairs) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("system-prompt", "You answer politely.");
        configuration.put("model", "test-model");
        for (int i = 0; i < pairs.length; i += 2) {
            configuration.put((String) pairs[i], pairs[i + 1]);
        }
        return type.configure("svc", configuration);
    }

    @Nested
    @DisplayName("a peer that accepts and then goes silent")
    class Stalling {

        @Test
        @DisplayName("is abandoned at the deadline, not waited on indefinitely")
        void abandonedAtTheDeadline() {
            stub.stalling();
            ConfiguredService service = service("deadline-ms", 250);

            long before = System.nanoTime();
            Outcome<String> outcome = service.invoke("hello");
            Duration waited = Duration.ofNanos(System.nanoTime() - before);

            assertThat(outcome).isInstanceOf(Outcome.Fail.class);
            // The socket connects perfectly and the stub holds it for
            // thirty seconds. Returning inside five proves the deadline
            // bounded the response and not merely the connection — the
            // whole point of the key, and the one property a
            // connect-only timeout would fail while passing every other
            // assertion here.
            assertThat(waited).isLessThan(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("is stated as this framework's own wait elapsing")
        void statedAsOurOwnWait() {
            stub.stalling();

            Outcome<String> outcome = service("deadline-ms", 250).invoke("hello");

            // Not "unreachable": the endpoint answered the connection.
            // Conflating the two would attribute to the service a fact
            // about this framework's patience.
            assertThat(((Outcome.Fail<String>) outcome).failure().message())
                    .contains("did not answer within the 250ms deadline")
                    .doesNotContain("unreachable");
        }

        @Test
        @DisplayName("is a failed sample, not an aborted run")
        void isAFailedSample() {
            stub.stalling();

            // The delivery taxonomy is untouched: a peer that stops
            // answering is evidence about the service, counted, not a
            // defect that stops everything.
            assertThat(service("deadline-ms", 250).invoke("hello"))
                    .isInstanceOf(Outcome.Fail.class);
        }
    }

    @Nested
    @DisplayName("the resolved wait")
    class Resolved {

        @Test
        @DisplayName("is recorded even when the author never wrote it")
        void recordedWhenDefaulted() {
            assertThat(service().configurationCovariates())
                    .containsEntry("deadlineMs",
                            String.valueOf(LanguageModelParameters.DEFAULT_DEADLINE_MS));
        }

        @Test
        @DisplayName("is recorded as declared when the author wrote it")
        void recordedWhenDeclared() {
            assertThat(service("deadline-ms", 90_000).configurationCovariates())
                    .containsEntry("deadlineMs", "90000");
        }

        @Test
        @DisplayName("distinguishes two otherwise identical services")
        void distinguishesIdenticalServices() {
            // Identity, not decoration: a shorter wait converts slow
            // deliveries into failed ones, so these two configurations
            // sample different populations and must not share a
            // fingerprint. This is what makes a baseline measured under
            // one deadline refuse a test run under another.
            assertThat(service("deadline-ms", 30_000).configurationCovariates())
                    .isNotEqualTo(service("deadline-ms", 60_000).configurationCovariates());
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("zero is not a spelling for waiting forever")
        void zeroRefused() {
            assertThatThrownBy(() -> service("deadline-ms", 0))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`deadline-ms:` must be a whole number")
                    .hasMessageContaining(
                            String.valueOf(LanguageModelParameters.DEFAULT_DEADLINE_MS));
        }

        @Test
        @DisplayName("a negative wait is refused")
        void negativeRefused() {
            assertThatThrownBy(() -> service("deadline-ms", -1))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`deadline-ms:` must be a whole number");
        }

        @Test
        @DisplayName("a fractional wait is refused")
        void fractionalRefused() {
            assertThatThrownBy(() -> service("deadline-ms", 1.5))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`deadline-ms:` must be a whole number");
        }

        @Test
        @DisplayName("a boolean is refused")
        void booleanRefused() {
            assertThatThrownBy(() -> service("deadline-ms", true))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("`deadline-ms:` must be a whole number");
        }
    }
}
