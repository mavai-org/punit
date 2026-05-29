package org.mavai.punit.internal.util;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HashUtils}.
 */
@DisplayName("HashUtils")
class HashUtilsTest {

    @Nested
    @DisplayName("sha256()")
    class Sha256Tests {

        @Test
        @DisplayName("returns 64-character hex string")
        void returns64CharHexString() {
            String hash = HashUtils.sha256("hello");
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("produces known SHA-256 digest")
        void producesKnownDigest() {
            // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
            String hash = HashUtils.sha256("hello");
            assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        }

        @Test
        @DisplayName("different inputs produce different hashes")
        void differentInputsProduceDifferentHashes() {
            String hash1 = HashUtils.sha256("input1");
            String hash2 = HashUtils.sha256("input2");
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("same input produces same hash (deterministic)")
        void sameInputProducesSameHash() {
            String hash1 = HashUtils.sha256("deterministic");
            String hash2 = HashUtils.sha256("deterministic");
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmptyString() {
            String hash = HashUtils.sha256("");
            assertThat(hash).hasSize(64);
            // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
            assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        }

        @Test
        @DisplayName("handles unicode input")
        void handlesUnicodeInput() {
            String hash = HashUtils.sha256("日本語テスト");
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]{64}");
        }
    }

    @Nested
    @DisplayName("truncateHash()")
    class TruncateHashTests {

        @Test
        @DisplayName("truncates to specified length")
        void truncatesToSpecifiedLength() {
            String hash = "abcdef1234567890";
            assertThat(HashUtils.truncateHash(hash, 8)).isEqualTo("abcdef12");
        }

        @Test
        @DisplayName("truncates to 4 characters")
        void truncatesTo4() {
            String hash = "abcdef1234567890";
            assertThat(HashUtils.truncateHash(hash, 4)).isEqualTo("abcd");
        }

        @Test
        @DisplayName("returns full string when length exceeds input")
        void returnsFullStringWhenLengthExceedsInput() {
            String hash = "abc";
            assertThat(HashUtils.truncateHash(hash, 8)).isEqualTo("abc");
        }

        @Test
        @DisplayName("returns full string when length equals input")
        void returnsFullStringWhenLengthEqualsInput() {
            String hash = "abcdefgh";
            assertThat(HashUtils.truncateHash(hash, 8)).isEqualTo("abcdefgh");
        }

        @Test
        @DisplayName("works with real SHA-256 output")
        void worksWithRealSha256Output() {
            String fullHash = HashUtils.sha256("test");
            String truncated = HashUtils.truncateHash(fullHash, 8);
            assertThat(truncated).hasSize(8);
            assertThat(fullHash).startsWith(truncated);
        }
    }
}
