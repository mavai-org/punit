package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.RunMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VerdictSinkBus")
class VerdictSinkBusTest {

    @BeforeEach
    void resetBus() {
        VerdictSinkBus.reset();
    }

    @AfterEach
    void cleanUp() {
        VerdictSinkBus.reset();
    }

    @Test
    @DisplayName("dispatch with no sinks is a no-op")
    void dispatchWithNoSinks() {
        VerdictSinkBus.dispatch(stubVerdict());

        assertThat(VerdictSinkBus.registeredCount()).isZero();
    }

    @Test
    @DisplayName("registered sink receives dispatched verdicts")
    void registeredSinkReceives() {
        List<ProbabilisticTestVerdict> received = new ArrayList<>();
        VerdictSinkBus.register(received::add);

        ProbabilisticTestVerdict verdict = stubVerdict();
        VerdictSinkBus.dispatch(verdict);

        assertThat(received).containsExactly(verdict);
    }

    @Test
    @DisplayName("multiple registered sinks all receive in registration order")
    void multipleSinksAllReceive() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        VerdictSinkBus.register(v -> first.incrementAndGet());
        VerdictSinkBus.register(v -> second.incrementAndGet());

        VerdictSinkBus.dispatch(stubVerdict());
        VerdictSinkBus.dispatch(stubVerdict());

        assertThat(first.get()).isEqualTo(2);
        assertThat(second.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("default sink supplier is materialised on first dispatch")
    void defaultSinkLazy() {
        AtomicInteger supplierCalled = new AtomicInteger();
        AtomicInteger sinkAccepts = new AtomicInteger();
        VerdictSinkBus.installDefaultSink(() -> {
            supplierCalled.incrementAndGet();
            return v -> sinkAccepts.incrementAndGet();
        });

        // Supplier not yet called
        assertThat(supplierCalled.get()).isZero();

        VerdictSinkBus.dispatch(stubVerdict());

        // Materialised on first dispatch
        assertThat(supplierCalled.get()).isEqualTo(1);
        assertThat(sinkAccepts.get()).isEqualTo(1);

        VerdictSinkBus.dispatch(stubVerdict());

        // Not re-materialised
        assertThat(supplierCalled.get()).isEqualTo(1);
        assertThat(sinkAccepts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("installDefaultSink is idempotent — first install wins")
    void installDefaultSinkIdempotent() {
        AtomicInteger firstSupplier = new AtomicInteger();
        AtomicInteger secondSupplier = new AtomicInteger();
        VerdictSinkBus.installDefaultSink(() -> {
            firstSupplier.incrementAndGet();
            return v -> { };
        });
        VerdictSinkBus.installDefaultSink(() -> {
            secondSupplier.incrementAndGet();
            return v -> { };
        });

        VerdictSinkBus.dispatch(stubVerdict());

        assertThat(firstSupplier.get()).isEqualTo(1);
        assertThat(secondSupplier.get()).isZero();
    }

    @Test
    @DisplayName("replaceAll suppresses default and installs only the supplied sinks")
    void replaceAllSuppressesDefault() {
        AtomicInteger defaultSink = new AtomicInteger();
        AtomicInteger replacement = new AtomicInteger();
        VerdictSinkBus.installDefaultSink(() -> v -> defaultSink.incrementAndGet());
        VerdictSinkBus.replaceAll(v -> replacement.incrementAndGet());

        VerdictSinkBus.dispatch(stubVerdict());

        assertThat(defaultSink.get()).isZero();
        assertThat(replacement.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("replaceAll with empty array suppresses all dispatch")
    void replaceAllEmpty() {
        AtomicInteger anySink = new AtomicInteger();
        VerdictSinkBus.installDefaultSink(() -> v -> anySink.incrementAndGet());
        VerdictSinkBus.replaceAll();

        VerdictSinkBus.dispatch(stubVerdict());

        assertThat(anySink.get()).isZero();
    }

    @Test
    @DisplayName("a throwing sink does not prevent other sinks from receiving")
    void throwingSinkIsolated() {
        AtomicInteger goodSink = new AtomicInteger();
        VerdictSinkBus.register(v -> { throw new RuntimeException("boom"); });
        VerdictSinkBus.register(v -> goodSink.incrementAndGet());

        VerdictSinkBus.dispatch(stubVerdict());

        assertThat(goodSink.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("reset clears registered sinks and the default-installed flag")
    void resetClears() {
        AtomicInteger registered = new AtomicInteger();
        VerdictSinkBus.register(v -> registered.incrementAndGet());
        assertThat(VerdictSinkBus.registeredCount()).isEqualTo(1);

        VerdictSinkBus.reset();

        assertThat(VerdictSinkBus.registeredCount()).isZero();
        VerdictSinkBus.dispatch(stubVerdict());
        assertThat(registered.get()).isZero();
    }

    private static ProbabilisticTestVerdict stubVerdict() {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-04-30T00:00:00Z"),
                new ProbabilisticTestVerdict.TestIdentity(
                        "com.example.MyTest", "stub", Optional.empty()),
                new ProbabilisticTestVerdict.ExecutionSummary(
                        10, 10, 10, 0, 0.9, 1.0, 100,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95,
                        ServiceContractAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new ProbabilisticTestVerdict.StatisticalAnalysis(
                        0.95, 0.0, 0.9,
                        Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), List.of()),
                ProbabilisticTestVerdict.CovariateStatus.allAligned(),
                new ProbabilisticTestVerdict.CostSummary(
                        0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new ProbabilisticTestVerdict.Termination(
                        TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                true,
                PUnitVerdict.PASS,
                "stub");
    }
}
