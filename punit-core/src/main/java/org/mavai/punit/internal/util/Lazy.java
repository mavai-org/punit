package org.mavai.punit.internal.util;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A lazily-evaluated, memoized value supplier.
 *
 * <p>{@code Lazy<T>} wraps a {@link Supplier} and ensures that:
 * <ul>
 *   <li>The supplier is only called once (on first access)</li>
 *   <li>The result is cached for subsequent accesses</li>
 *   <li>If the supplier throws, the exception is cached and re-thrown on subsequent accesses</li>
 * </ul>
 *
 * <p>This is particularly useful in success criteria where expensive operations
 * (like JSON parsing) should only be performed once, even if multiple criteria
 * reference the result.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Parsing happens once, on first access
 * Lazy<JsonNode> parsed = Lazy.of(() -> parseJson(response));
 *
 * ServiceContract<Void, String> contract = ServiceContract
 *     .<Void, String>define()
 *     .ensure("JSON parsed", s -> parsed.get() != null)
 *     .ensure("Has products", s -> countProducts(parsed.get()) > 0)
 *     .build();
 * }</pre>
 *
 * <h2>Exception Behavior</h2>
 * <p>If the supplier throws an exception:
 * <ul>
 *   <li>The exception is cached (not the stack trace, the actual exception instance)</li>
 *   <li>Subsequent calls to {@link #get()} re-throw the same exception instance</li>
 *   <li>This ensures consistent behavior and allows cascade detection in criteria evaluation</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This implementation is <strong>not thread-safe</strong>. If thread safety is
 * required, external synchronization must be used.
 *
 * @param <T> the type of the lazy value
 */
public final class Lazy<T> implements Supplier<T> {

    private final Supplier<T> supplier;
    private T value;
    private Throwable exception;
    private boolean evaluated;

    private Lazy(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier must not be null");
        this.evaluated = false;
    }

    /**
     * Creates a new lazy value from the given supplier.
     *
     * @param supplier the supplier to evaluate lazily
     * @param <T> the type of the value
     * @return a new Lazy instance
     * @throws NullPointerException if supplier is null
     */
    public static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Lazy<>(supplier);
    }

    /**
     * Creates a lazy value that is already evaluated.
     *
     * <p>This is useful for testing or when the value is already available.
     *
     * @param value the pre-computed value
     * @param <T> the type of the value
     * @return a Lazy instance that returns the given value
     */
    public static <T> Lazy<T> ofValue(T value) {
        Lazy<T> lazy = new Lazy<>(() -> value);
        lazy.value = value;
        lazy.evaluated = true;
        return lazy;
    }

    /**
     * Returns the lazily-computed value.
     *
     * <p>On first call, evaluates the supplier and caches the result.
     * On subsequent calls, returns the cached value.
     *
     * <p>If the supplier throws an exception, it is cached and re-thrown
     * on this and all subsequent calls.
     *
     * @return the computed value
     * @throws RuntimeException if the supplier threw an exception (wraps checked exceptions)
     */
    @Override
    public T get() {
        if (!evaluated) {
            try {
                value = supplier.get();
            } catch (Throwable t) {
                exception = t;
            }
            evaluated = true;
        }

        if (exception != null) {
            if (exception instanceof RuntimeException re) {
                throw re;
            }
            if (exception instanceof Error e) {
                throw e;
            }
            throw new RuntimeException(exception);
        }

        return value;
    }

    /**
     * Returns true if this lazy value has been evaluated.
     *
     * @return true if get() has been called at least once
     */
    public boolean isEvaluated() {
        return evaluated;
    }

    /**
     * Returns true if evaluation resulted in an exception.
     *
     * <p>Only meaningful if {@link #isEvaluated()} returns true.
     *
     * @return true if the supplier threw an exception
     */
    public boolean isErrored() {
        return evaluated && exception != null;
    }

    /**
     * Returns the cached exception, if any.
     *
     * @return the exception thrown by the supplier, or null if none
     */
    public Throwable getException() {
        return exception;
    }
}

