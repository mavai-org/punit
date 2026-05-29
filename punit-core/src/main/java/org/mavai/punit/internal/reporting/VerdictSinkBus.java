package org.mavai.punit.internal.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.mavai.punit.internal.reporting.CompositeVerdictSink;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.VerdictSink;

/**
 * Singleton-style registry of {@link VerdictSink}s that
 * {@code PUnit.assertPasses(...)} dispatches to after a verdict is
 * produced.
 *
 * <p>The bus holds an ordered list of sinks. By default, the list
 * contains the framework's "primary" sink (configured via
 * {@link #installDefaultSink(Supplier)}); tests and embedders can
 * extend with {@link #register(VerdictSink)} or replace the entire
 * list with {@link #replaceAll(VerdictSink...)}.
 *
 * <h2>Default sink</h2>
 *
 * <p>The default sink is supplied externally — typically by
 * {@code punit-report} installing a
 * {@code VerdictXmlSink(ReportConfiguration.resolve())} during JUnit
 * extension bootstrap. The bus does not import {@code punit-report}
 * directly because that would invert the module dependency direction
 * (report depends on core, not the other way round).
 *
 * <h2>Test isolation</h2>
 *
 * <p>Tests should call {@link #reset()} in a {@code @BeforeEach} or
 * {@code @AfterEach} hook to clear registered sinks. The default sink
 * is re-supplied by the next caller that calls
 * {@link #installDefaultSink(Supplier)} (idempotent if the sink is
 * stateless).
 */
public final class VerdictSinkBus {

    private static final List<VerdictSink> SINKS = new ArrayList<>();
    private static Supplier<VerdictSink> defaultSupplier;
    private static boolean defaultInstalled;

    private VerdictSinkBus() { }

    /**
     * Configures the default sink supplier, lazy-evaluated on first
     * dispatch. Idempotent — calling repeatedly has no effect once a
     * default is in place. Callers that want to override should use
     * {@link #replaceAll(VerdictSink...)}.
     *
     * @param supplier supplier whose {@code get} returns the default
     *                 sink instance; called once, lazily
     */
    public static synchronized void installDefaultSink(Supplier<VerdictSink> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        if (defaultSupplier == null) {
            defaultSupplier = supplier;
        }
    }

    /**
     * Registers an additional sink. The bus dispatches to all
     * registered sinks (and the default, if installed) in registration
     * order.
     *
     * @param sink the sink to add
     */
    public static synchronized void register(VerdictSink sink) {
        Objects.requireNonNull(sink, "sink");
        SINKS.add(sink);
    }

    /**
     * Clears all registered sinks (including the default). Used by
     * tests for clean isolation. After {@code reset()},
     * {@link #installDefaultSink(Supplier)} can re-install the default
     * for the next test.
     */
    public static synchronized void reset() {
        SINKS.clear();
        defaultSupplier = null;
        defaultInstalled = false;
    }

    /**
     * Replaces the entire sink list with the supplied sinks, ignoring
     * any previously installed default. Used by tests that need
     * exclusive control over which sinks fire.
     *
     * @param sinks the sinks to install; may be empty (suppresses all
     *              dispatch)
     */
    public static synchronized void replaceAll(VerdictSink... sinks) {
        SINKS.clear();
        defaultInstalled = true;   // suppress default installation
        defaultSupplier = null;
        for (VerdictSink s : sinks) {
            Objects.requireNonNull(s, "sink");
            SINKS.add(s);
        }
    }

    /**
     * Dispatches the verdict to every registered sink. The default
     * sink (if any) is materialised on first call and prepended to
     * the list. Failures in individual sinks are isolated by
     * {@link CompositeVerdictSink}'s logging-and-continue semantics.
     *
     * @param verdict the verdict to dispatch
     */
    public static synchronized void dispatch(ProbabilisticTestVerdict verdict) {
        Objects.requireNonNull(verdict, "verdict");
        installDefaultIfNeeded();
        if (SINKS.isEmpty()) {
            return;
        }
        new CompositeVerdictSink(List.copyOf(SINKS)).accept(verdict);
    }

    private static void installDefaultIfNeeded() {
        if (!defaultInstalled && defaultSupplier != null) {
            VerdictSink defaultSink = defaultSupplier.get();
            if (defaultSink != null) {
                SINKS.add(0, defaultSink);
            }
            defaultInstalled = true;
        }
    }

    /**
     * @return the number of sinks currently registered (excluding the
     *         default until first dispatch materialises it). Test
     *         observability only.
     */
    public static synchronized int registeredCount() {
        return SINKS.size();
    }
}
