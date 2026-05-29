package org.mavai.punit.internal.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;

import org.mavai.punit.internal.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BaselineIntegrity — fingerprint emission and verification")
class BaselineIntegrityTest {

    private static final Path FAKE_FILE = Path.of("/baselines/ShoppingBasket.measure-a1b2c3d4.yaml");

    @Test
    @DisplayName("appendFingerprint adds a final contentFingerprint: line whose digest is "
            + "SHA-256 of the body that precedes it")
    void appendsFingerprintMatchingBodyDigest() {
        String body = "schemaVersion: punit-baseline-3\nuseCaseId: X\n";
        String fingerprinted = BaselineIntegrity.appendFingerprint(body);

        assertThat(fingerprinted)
                .startsWith(body)
                .contains("\ncontentFingerprint: ")
                .endsWith("\n");

        String expectedDigest = HashUtils.sha256(body);
        assertThat(fingerprinted).contains("contentFingerprint: " + expectedDigest);
    }

    @Test
    @DisplayName("appendFingerprint normalises a body without trailing newline so the "
            + "fingerprint line stays on its own line")
    void appendsTrailingNewlineWhenAbsent() {
        String body = "schemaVersion: punit-baseline-3"; // no trailing newline
        String fingerprinted = BaselineIntegrity.appendFingerprint(body);

        assertThat(fingerprinted.lines().toList())
                .containsExactly(
                        "schemaVersion: punit-baseline-3",
                        "contentFingerprint: " + HashUtils.sha256(body + "\n"));
    }

    @Test
    @DisplayName("verify returns empty when stored digest matches recomputed body digest")
    void verifyOkOnMatchingDigest() {
        String body = "schemaVersion: punit-baseline-3\n";
        String yaml = BaselineIntegrity.appendFingerprint(body);

        Optional<String> warning = BaselineIntegrity.verify(yaml, FAKE_FILE);

        assertThat(warning).as("a freshly-fingerprinted body must verify cleanly").isEmpty();
    }

    @Test
    @DisplayName("verify returns the louder mismatch warning — naming the expected and "
            + "computed digests — when the body has been edited after the fingerprint "
            + "was appended")
    void verifyMismatchOnEditedBody() {
        String body = "schemaVersion: punit-baseline-3\nminPassRate: 0.95\n";
        String yaml = BaselineIntegrity.appendFingerprint(body);
        // Tamper: lower the threshold without re-fingerprinting.
        String tampered = yaml.replace("minPassRate: 0.95", "minPassRate: 0.50");

        Optional<String> warning = BaselineIntegrity.verify(tampered, FAKE_FILE);

        assertThat(warning).isPresent();
        assertThat(warning.get())
                .contains("integrity check failed")
                .contains("modified since generation")
                .contains(FAKE_FILE.toString())
                .contains("expected:")
                .contains("got:");
    }

    @Test
    @DisplayName("verify returns the softer missing warning when no contentFingerprint: "
            + "field is present (legacy baseline pre-dating the integrity feature)")
    void verifyMissingOnLegacyBaseline() {
        String legacy = "schemaVersion: punit-baseline-3\nuseCaseId: X\n";

        Optional<String> warning = BaselineIntegrity.verify(legacy, FAKE_FILE);

        assertThat(warning).isPresent();
        assertThat(warning.get())
                .contains("predates integrity verification")
                .contains("post-generation modifications cannot be detected")
                .contains(FAKE_FILE.toString());
    }
}
