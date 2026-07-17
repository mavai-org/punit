package org.mavai.punit.internal.engine.emit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Emitted-key bound — artefact key discipline")
class EmittedKeysTest {

    @Test
    @DisplayName("keys within the bound pass through unchanged")
    void withinBoundUnchanged() {
        String key = "a".repeat(EmittedKeys.MAX_KEY_LENGTH);
        assertThat(EmittedKeys.bound(key)).isSameAs(key);
        assertThat(EmittedKeys.bound("plain-condition")).isEqualTo("plain-condition");
    }

    @Test
    @DisplayName("over-long keys truncate to exactly the bound: prefix, separator, content hash")
    void overLongKeysTruncateToBound() {
        String key = "k".repeat(EmittedKeys.MAX_KEY_LENGTH + 1);
        String bounded = EmittedKeys.bound(key);
        assertThat(bounded).hasSize(EmittedKeys.MAX_KEY_LENGTH);
        assertThat(bounded).startsWith("k".repeat(247));
        assertThat(bounded.charAt(247)).isEqualTo('-');
        assertThat(bounded.substring(248)).matches("[0-9a-f]{8}");
    }

    @Test
    @DisplayName("distinct keys sharing the truncated prefix stay distinct after truncation")
    void truncatedKeysStayDistinct() {
        String shared = "s".repeat(300);
        String boundedA = EmittedKeys.bound(shared + "-variant-a");
        String boundedB = EmittedKeys.bound(shared + "-variant-b");
        assertThat(boundedA).isNotEqualTo(boundedB);
        assertThat(boundedA.substring(0, 247)).isEqualTo(boundedB.substring(0, 247));
    }

    @Test
    @DisplayName("truncation is deterministic — same key, same bounded form")
    void truncationIsDeterministic() {
        String key = "d".repeat(400);
        assertThat(EmittedKeys.bound(key)).isEqualTo(EmittedKeys.bound(key));
    }
}
