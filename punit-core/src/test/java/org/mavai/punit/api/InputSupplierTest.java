package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InputSupplier.from — content-hash identity")
class InputSupplierTest {

    @Test
    @DisplayName("identity is a SHA-256 content hash of the materialised list")
    void identityIsContentHash() {
        InputSupplier<String> s = InputSupplier.from(() -> List.of("a", "b", "c"));

        assertThat(s.identity()).startsWith("sha256:");
    }

    @Test
    @DisplayName("identity is deterministic for the same content")
    void identityDeterministic() {
        InputSupplier<String> a = InputSupplier.from(() -> List.of("a", "b", "c"));
        InputSupplier<String> b = InputSupplier.from(() -> List.of("a", "b", "c"));

        assertThat(a.identity()).isEqualTo(b.identity());
    }

    @Test
    @DisplayName("identity differs when content differs")
    void identityContentSensitivity() {
        InputSupplier<String> a = InputSupplier.from(() -> List.of("a", "b", "c"));
        InputSupplier<String> b = InputSupplier.from(() -> List.of("a", "b", "d"));

        assertThat(a.identity()).isNotEqualTo(b.identity());
    }

    @Test
    @DisplayName("identity differs when ordering differs")
    void identityOrderingSensitivity() {
        InputSupplier<String> a = InputSupplier.from(() -> List.of("a", "b", "c"));
        InputSupplier<String> b = InputSupplier.from(() -> List.of("c", "b", "a"));

        assertThat(a.identity()).isNotEqualTo(b.identity());
    }

    @Test
    @DisplayName("materialises supplier lazily and memoises")
    void lazyAndMemoised() {
        AtomicInteger calls = new AtomicInteger();
        InputSupplier<String> s = InputSupplier.from(() -> {
            calls.incrementAndGet();
            return List.of("a", "b");
        });

        assertThat(calls.get()).isZero();   // not yet materialised
        s.identity();
        assertThat(calls.get()).isEqualTo(1);
        s.all();
        s.identity();
        s.all();
        assertThat(calls.get()).isEqualTo(1);   // memoised
    }

    @Test
    @DisplayName("rejects null supplier at construction")
    void rejectsNullSupplier() {
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>from(null));
    }

    @Test
    @DisplayName("supplier returning null fails with diagnostic on materialisation")
    void supplierReturningNull() {
        InputSupplier<String> s = InputSupplier.from(() -> null);

        assertThatNullPointerException()
                .isThrownBy(s::identity)
                .withMessageContaining("returned null");
    }

    @Test
    @DisplayName("supplier returning empty list throws on materialisation")
    void supplierReturningEmpty() {
        InputSupplier<String> s = InputSupplier.from(List::of);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(s::identity)
                .withMessageContaining("empty");
    }

    @Test
    @DisplayName("all() returns the supplied values in canonical order")
    void allReturnsValues() {
        InputSupplier<String> s = InputSupplier.from(() -> List.of("a", "b", "c"));

        assertThat(s.all()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("Java records produce stable identity (record toString is specified)")
    void recordsHaveStableIdentity() {
        record Item(String name, int value) {}

        InputSupplier<Item> a = InputSupplier.from(() -> List.of(new Item("x", 1), new Item("y", 2)));
        InputSupplier<Item> b = InputSupplier.from(() -> List.of(new Item("x", 1), new Item("y", 2)));

        assertThat(a.identity()).isEqualTo(b.identity());
    }
}
