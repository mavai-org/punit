package org.mavai.punit.internal.runtime;

import java.lang.reflect.Method;
import java.util.Optional;

import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.verdict.RunMetadata;

/**
 * Resolves the {@code className} / {@code methodName} of the typed
 * test currently executing, by walking the stack to find the
 * nearest {@link ProbabilisticTest @ProbabilisticTest}-annotated
 * method.
 *
 * <p>The typed pipeline's {@code PUnit} entry-point is a static facade
 * called from inside test bodies — there is no JUnit
 * {@code ExtensionContext} available at that call site. A stack walk
 * is the cheapest and least-invasive way to recover identity without
 * making the author pass it explicitly on every {@code .assertPasses()}
 * call.
 *
 * <p>The resolver is intentionally lenient: it returns
 * {@link Optional#empty()} when no annotated frame is found (e.g. when
 * called outside a JUnit-driven typed test, such as a hand-written
 * integration test or a REPL demo). Callers should fall back to a
 * generic identity in that case.
 */
public final class TestIdentityResolver {

    private TestIdentityResolver() { }

    /**
     * @return the identity of the nearest enclosing
     *         {@link ProbabilisticTest @ProbabilisticTest}-annotated
     *         method on the current call stack, or
     *         {@link Optional#empty()} if none is found
     */
    public static Optional<RunMetadata> resolve() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(TestIdentityResolver::asMetadata)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst());
    }

    private static Optional<RunMetadata> asMetadata(StackWalker.StackFrame frame) {
        Class<?> declaring = frame.getDeclaringClass();
        String methodName = frame.getMethodName();
        // Match by name only — overload disambiguation isn't worth the
        // complexity here. Multiple overloads of the same JUnit test
        // method are vanishingly rare; a stack frame's method name maps
        // back to at most one annotated method 99.9% of the time.
        for (Method m : declaring.getDeclaredMethods()) {
            if (m.getName().equals(methodName)
                    && m.isAnnotationPresent(ProbabilisticTest.class)) {
                return Optional.of(RunMetadata.of(declaring.getName(), methodName));
            }
        }
        return Optional.empty();
    }
}
