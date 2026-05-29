package org.mavai.punit.internal.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility methods for SHA-256 hashing and hash truncation.
 *
 * <p>Used throughout PUnit for computing stable content hashes
 * (footprints, covariate profiles, covariate declarations).
 */
public final class HashUtils {

    private HashUtils() {
        // Utility class
    }

    /**
     * Computes a SHA-256 hash of the input string, returning the full hex-encoded digest.
     *
     * @param input the string to hash
     * @return 64-character lowercase hex string
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Truncates a hash string to the specified length.
     *
     * @param hash the hash to truncate
     * @param length the desired length
     * @return the truncated hash
     */
    public static String truncateHash(String hash, int length) {
        return hash.substring(0, Math.min(length, hash.length()));
    }
}
