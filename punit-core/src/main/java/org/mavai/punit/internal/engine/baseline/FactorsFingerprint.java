package org.mavai.punit.internal.engine.baseline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import org.mavai.punit.api.FactorBundle;

/**
 * Computes the {@code factorsFingerprint} field carried by every
 * {@link BaselineRecord} and used by the resolver as a lookup key.
 *
 * <p>The fingerprint is the first eight hex characters of
 * {@code SHA-256(bundle.canonicalJson())}. {@link FactorBundle}'s
 * canonical-JSON form is the framework's stable per-factors-instance
 * representation — keys sorted, types lifted via {@code FactorValue},
 * stable across runs and processes by construction. Both writer-side
 * (during MEASURE) and reader-side (during PROBABILISTIC_TEST) hash
 * the same canonical form, so the lookup key matches without the two
 * sides having to coordinate beyond agreeing on this utility.
 *
 * <p>{@code FactorBundle.empty()} (no factors) hashes to a stable
 * fingerprint of its own, distinct from any non-empty factors
 * fingerprint.
 *
 * <p>See {@code docs/DES-BASELINE-YAML-SCHEMA.md} §"factorsFingerprint"
 * for the design rationale.
 */
public final class FactorsFingerprint {

    private static final int HEX_PREFIX_LENGTH = 8;

    private FactorsFingerprint() { }

    /**
     * @return the 8-hex-character fingerprint of {@code bundle}'s
     *         canonical JSON form
     */
    public static String of(FactorBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        byte[] hash = sha256(bundle.canonicalJson());
        return HexFormat.of().formatHex(hash).substring(0, HEX_PREFIX_LENGTH);
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK 8+; this branch is unreachable.
            throw new AssertionError("SHA-256 unavailable on this JVM", e);
        }
    }
}
