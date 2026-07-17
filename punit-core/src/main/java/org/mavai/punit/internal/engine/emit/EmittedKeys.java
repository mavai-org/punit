package org.mavai.punit.internal.engine.emit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Artefact key discipline for every YAML mapping key punit emits.
 *
 * <p>The mavai interchange formats bound every emitted mapping key at
 * {@value #MAX_KEY_LENGTH} characters — comfortably under YAML's
 * 1,024-character implicit-key limit — and forbid keys whose content
 * or length depends on input or response content. Free-text identity
 * strings that reach a mapping-key position (postcondition
 * descriptions, criterion names, factor names) therefore pass through
 * {@link #bound(String)} on their way into an emitted document.
 *
 * <p>An over-long key is truncated to a bounded prefix plus a short
 * SHA-256 content hash, so two distinct keys stay distinct after
 * truncation. Keys within the bound pass through unchanged.
 */
public final class EmittedKeys {

    /** Maximum length of any emitted mapping key, in characters. */
    public static final int MAX_KEY_LENGTH = 256;

    /** Hex characters of content hash appended to a truncated key. */
    private static final int HASH_LENGTH = 8;

    /** Prefix retained when truncating: bound − hash − one separator. */
    private static final int PREFIX_LENGTH = MAX_KEY_LENGTH - HASH_LENGTH - 1;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private EmittedKeys() { }

    /**
     * Bound {@code key} to {@link #MAX_KEY_LENGTH} characters. Keys
     * within the bound are returned unchanged; longer keys become
     * {@code {prefix}-{hash}} where the prefix is the key's first
     * {@value #PREFIX_LENGTH} characters and the hash is the first
     * {@value #HASH_LENGTH} hex characters of the full key's SHA-256
     * digest — distinct inputs stay distinct after truncation.
     */
    public static String bound(String key) {
        if (key.length() <= MAX_KEY_LENGTH) {
            return key;
        }
        return key.substring(0, PREFIX_LENGTH) + "-" + sha256HexPrefix(key, HASH_LENGTH);
    }

    /**
     * First {@code hexChars} hex characters of {@code input}'s SHA-256
     * digest. Shared by the key-truncation rule and the
     * {@linkplain ResultProjections#anchorOf diff anchors}.
     */
    static String sha256HexPrefix(String input, int hexChars) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be supported by every JRE", e);
        }
        StringBuilder hex = new StringBuilder(hexChars);
        for (int i = 0; hex.length() < hexChars && i < digest.length; i++) {
            int b = digest[i] & 0xff;
            hex.append(HEX[b >>> 4]);
            if (hex.length() < hexChars) {
                hex.append(HEX[b & 0x0f]);
            }
        }
        return hex.toString();
    }
}
