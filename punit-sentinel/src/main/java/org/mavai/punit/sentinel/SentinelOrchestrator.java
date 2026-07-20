package org.mavai.punit.sentinel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.mavai.punit.api.Experiment;
import org.mavai.punit.api.ProbabilisticTest;

/**
 * Iterates registered classes, finds {@link ProbabilisticTest} or
 * {@link Experiment} methods, and dispatches each one to
 * {@link SentinelExecutor}.
 *
 * <p>Discovery is purely reflective. There is no class-level marker —
 * a class is "Sentinel-runnable" by virtue of being in the binary's
 * build-time registration manifest. The orchestrator iterates the
 * registered list and invokes the right methods.
 *
 * <p>Methods are iterated in alphabetical order by name, so a Sentinel
 * binary's behaviour is deterministic across runs.
 *
 * <p>Inherited methods are not invoked. {@code getDeclaredMethods()} —
 * not {@code getMethods()} — is used so that a parent class's methods
 * aren't run twice when a subclass is also registered. If inheritance
 * becomes a real authoring pattern, this is the place to extend.
 */
// mavai-ref: JVI-0PYEB09 — do not remove (resolves in mavai-orchestrator)
public final class SentinelOrchestrator {

    private final SentinelExecutor executor;

    public SentinelOrchestrator() {
        this(new SentinelExecutor());
    }

    SentinelOrchestrator(SentinelExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Runs every {@code @ProbabilisticTest} method declared on each
     * registered class.
     *
     * @param registeredClasses the classes the binary bundles; must be
     *                          non-null and may be empty (returns an
     *                          empty outcome list)
     * @return the outcomes in the order they were executed
     */
    public List<SentinelExecutor.Outcome> runTests(List<Class<?>> registeredClasses) {
        return run(registeredClasses, ProbabilisticTest.class);
    }

    /**
     * Runs every {@code @Experiment} method declared on each registered
     * class.
     *
     * @param registeredClasses the classes the binary bundles
     * @return the outcomes in the order they were executed
     */
    public List<SentinelExecutor.Outcome> runExperiments(List<Class<?>> registeredClasses) {
        return run(registeredClasses, Experiment.class);
    }

    private List<SentinelExecutor.Outcome> run(
            List<Class<?>> registeredClasses,
            Class<? extends Annotation> annotation) {
        Objects.requireNonNull(registeredClasses, "registeredClasses");
        List<SentinelExecutor.Outcome> outcomes = new ArrayList<>();
        for (Class<?> cls : registeredClasses) {
            for (Method method : methodsAnnotatedWith(cls, annotation)) {
                outcomes.add(executor.execute(cls, method));
            }
        }
        return List.copyOf(outcomes);
    }

    private static List<Method> methodsAnnotatedWith(
            Class<?> cls, Class<? extends Annotation> annotation) {
        return Arrays.stream(cls.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(annotation))
                .sorted(Comparator.comparing(Method::getName))
                .toList();
    }
}
