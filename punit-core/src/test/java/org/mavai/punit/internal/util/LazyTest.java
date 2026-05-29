package org.mavai.punit.internal.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Lazy")
class LazyTest {

    @Nested
    @DisplayName("of()")
    class OfTests {

        @Test
        @DisplayName("should create a lazy value")
        void shouldCreateLazyValue() {
            Lazy<String> lazy = Lazy.of(() -> "hello");

            assertThat(lazy).isNotNull();
            assertThat(lazy.isEvaluated()).isFalse();
        }

        @Test
        @DisplayName("should reject null supplier")
        void shouldRejectNullSupplier() {
            assertThatThrownBy(() -> Lazy.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("supplier");
        }
    }

    @Nested
    @DisplayName("ofValue()")
    class OfValueTests {

        @Test
        @DisplayName("should create pre-evaluated lazy value")
        void shouldCreatePreEvaluatedLazyValue() {
            Lazy<String> lazy = Lazy.ofValue("pre-computed");

            assertThat(lazy.isEvaluated()).isTrue();
            assertThat(lazy.get()).isEqualTo("pre-computed");
        }

        @Test
        @DisplayName("should handle null value")
        void shouldHandleNullValue() {
            Lazy<String> lazy = Lazy.ofValue(null);

            assertThat(lazy.isEvaluated()).isTrue();
            assertThat(lazy.get()).isNull();
        }
    }

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("should evaluate supplier on first call")
        void shouldEvaluateSupplierOnFirstCall() {
            AtomicInteger callCount = new AtomicInteger(0);
            Lazy<String> lazy = Lazy.of(() -> {
                callCount.incrementAndGet();
                return "evaluated";
            });

            assertThat(callCount.get()).isZero();

            String result = lazy.get();

            assertThat(result).isEqualTo("evaluated");
            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return cached value on subsequent calls")
        void shouldReturnCachedValueOnSubsequentCalls() {
            AtomicInteger callCount = new AtomicInteger(0);
            Lazy<Integer> lazy = Lazy.of(() -> callCount.incrementAndGet());

            // First call evaluates
            Integer first = lazy.get();
            assertThat(first).isEqualTo(1);

            // Subsequent calls return cached value
            Integer second = lazy.get();
            Integer third = lazy.get();

            assertThat(second).isEqualTo(1);
            assertThat(third).isEqualTo(1);
            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should cache null values")
        void shouldCacheNullValues() {
            AtomicInteger callCount = new AtomicInteger(0);
            Lazy<String> lazy = Lazy.of(() -> {
                callCount.incrementAndGet();
                return null;
            });

            assertThat(lazy.get()).isNull();
            assertThat(lazy.get()).isNull();
            assertThat(callCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Exception handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("should cache and rethrow RuntimeException")
        void shouldCacheAndRethrowRuntimeException() {
            RuntimeException exception = new RuntimeException("test error");
            Lazy<String> lazy = Lazy.of(() -> { throw exception; });

            // First call throws
            assertThatThrownBy(lazy::get)
                    .isSameAs(exception);

            // Subsequent calls throw same exception
            assertThatThrownBy(lazy::get)
                    .isSameAs(exception);
        }

        @Test
        @DisplayName("should wrap checked exception in RuntimeException")
        void shouldWrapCheckedException() {
            IOException checkedException = new IOException("IO error");
            Lazy<String> lazy = Lazy.of(() -> { throw new RuntimeException(checkedException); });

            assertThatThrownBy(lazy::get)
                    .isInstanceOf(RuntimeException.class)
                    .hasCause(checkedException);
        }

        @Test
        @DisplayName("should propagate Error")
        void shouldPropagateError() {
            OutOfMemoryError error = new OutOfMemoryError("test");
            Lazy<String> lazy = Lazy.of(() -> { throw error; });

            assertThatThrownBy(lazy::get)
                    .isSameAs(error);
        }

        @Test
        @DisplayName("should mark as errored after exception")
        void shouldMarkAsErroredAfterException() {
            Lazy<String> lazy = Lazy.of(() -> { throw new RuntimeException("error"); });

            assertThat(lazy.isErrored()).isFalse();

            try {
                lazy.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(lazy.isErrored()).isTrue();
            assertThat(lazy.isEvaluated()).isTrue();
        }

        @Test
        @DisplayName("should return exception via getException()")
        void shouldReturnExceptionViaGetter() {
            RuntimeException exception = new RuntimeException("test");
            Lazy<String> lazy = Lazy.of(() -> { throw exception; });

            assertThat(lazy.getException()).isNull();

            try {
                lazy.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(lazy.getException()).isSameAs(exception);
        }
    }

    @Nested
    @DisplayName("isEvaluated()")
    class IsEvaluatedTests {

        @Test
        @DisplayName("should return false before evaluation")
        void shouldReturnFalseBeforeEvaluation() {
            Lazy<String> lazy = Lazy.of(() -> "value");

            assertThat(lazy.isEvaluated()).isFalse();
        }

        @Test
        @DisplayName("should return true after successful evaluation")
        void shouldReturnTrueAfterSuccessfulEvaluation() {
            Lazy<String> lazy = Lazy.of(() -> "value");

            lazy.get();

            assertThat(lazy.isEvaluated()).isTrue();
        }

        @Test
        @DisplayName("should return true after failed evaluation")
        void shouldReturnTrueAfterFailedEvaluation() {
            Lazy<String> lazy = Lazy.of(() -> { throw new RuntimeException(); });

            try {
                lazy.get();
            } catch (RuntimeException ignored) {
            }

            assertThat(lazy.isEvaluated()).isTrue();
        }
    }

    @Nested
    @DisplayName("Exception identity for cascade detection")
    class ExceptionIdentityTests {

        @Test
        @DisplayName("should return same exception instance on multiple calls")
        void shouldReturnSameExceptionInstance() {
            RuntimeException original = new RuntimeException("original");
            Lazy<String> lazy = Lazy.of(() -> { throw original; });

            RuntimeException caught1 = null;
            RuntimeException caught2 = null;

            try {
                lazy.get();
            } catch (RuntimeException e) {
                caught1 = e;
            }

            try {
                lazy.get();
            } catch (RuntimeException e) {
                caught2 = e;
            }

            assertThat(caught1).isSameAs(original);
            assertThat(caught2).isSameAs(original);
            assertThat(caught1).isSameAs(caught2);
        }
    }
}

