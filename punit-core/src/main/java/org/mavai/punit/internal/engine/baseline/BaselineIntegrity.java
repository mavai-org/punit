package org.mavai.punit.internal.engine.baseline;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mavai.punit.internal.util.HashUtils;

/**
 * Baseline-integrity helper. Computes and verifies the
 * {@code contentFingerprint:} field appended as the last line of
 * every measure baseline.
 *
 * <p>The fingerprint is the lowercase-hex SHA-256 digest of the YAML
 * content preceding the {@code contentFingerprint:} line. On read,
 * the framework recomputes the digest over the same prefix and
 * compares; a mismatch indicates the spec has been edited since the
 * measure produced it. The check is a soft warning on the verdict —
 * not a hard abort. The live test data is independently informative;
 * a verdict warning is loud, durable, and routes through the same
 * observability channel as every other test signal.
 *
 * <p>Three states:
 *
 * <ol>
 *   <li><b>OK</b> — line present, computed digest matches stored
 *       value. {@link #verify} returns {@link Optional#empty()}.</li>
 *   <li><b>MISSING</b> — line absent (typically a baseline on disk
 *       that predates the integrity-verification feature). Returns
 *       the softer "predates integrity verification" wording.</li>
 *   <li><b>MISMATCH</b> — line present, computed digest disagrees.
 *       Returns the louder "modified since generation" wording with
 *       expected and computed digests in the diagnostic.</li>
 * </ol>
 *
 * <p>Match rule for finding the fingerprint line: the regex is
 * anchored at the start of a line (column 0) so a {@code contentFingerprint:}
 * embedded inside a quoted value or as a child key cannot be confused
 * with the integrity field. The body for hashing is the substring up
 * to (but excluding) the matched line — which is what the writer
 * hashes when it builds the file.
 */
// mavai-ref: JVI-CNDHE1$ — do not remove (resolves in mavai-orchestrator)
final class BaselineIntegrity {

    private BaselineIntegrity() { }

    static final String FINGERPRINT_KEY = "contentFingerprint";

    private static final Pattern LINE = Pattern.compile(
            "(?m)^" + FINGERPRINT_KEY + ":\\s*([0-9a-fA-F]+)\\s*$");

    /**
     * Append a {@code contentFingerprint:} line to {@code body},
     * computing the SHA-256 over {@code body} as-is. The body is
     * normalised to end with a newline so the appended line stays
     * on its own line regardless of whether the dumper produced a
     * trailing newline.
     */
    static String appendFingerprint(String body) {
        String normalised = body.endsWith("\n") ? body : body + "\n";
        String digest = HashUtils.sha256(normalised);
        return normalised + FINGERPRINT_KEY + ": " + digest + "\n";
    }

    /**
     * Verify the integrity of a loaded baseline.
     *
     * @param yaml the full file contents (including any trailing
     *             {@code contentFingerprint:} line)
     * @param file the file path — included in the warning message
     *             so the operator can locate the offending baseline
     * @return {@link Optional#empty()} when the fingerprint matches;
     *         otherwise a one-line warning describing the integrity
     *         failure (mismatch or missing) for inclusion in the
     *         verdict's warnings list
     */
    static Optional<String> verify(String yaml, Path file) {
        Matcher m = LINE.matcher(yaml);
        if (!m.find()) {
            return Optional.of(missingWarning(file));
        }
        String stored = m.group(1).toLowerCase();
        String body = yaml.substring(0, m.start());
        String computed = HashUtils.sha256(body);
        if (computed.equals(stored)) {
            return Optional.empty();
        }
        return Optional.of(mismatchWarning(file, stored, computed));
    }

    private static String missingWarning(Path file) {
        return "baseline predates integrity verification; spec was not produced "
                + "with a content fingerprint, so post-generation modifications "
                + "cannot be detected (file: " + file + ").";
    }

    private static String mismatchWarning(Path file, String expected, String computed) {
        return "baseline integrity check failed — the spec file has been modified "
                + "since generation; verdict may not reflect a valid empirical "
                + "comparison (file: " + file + ", expected: " + expected
                + ", got: " + computed + ").";
    }
}
