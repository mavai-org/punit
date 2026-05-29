package org.mavai.punit.internal.engine.emit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.PostconditionResult;
import org.mavai.punit.api.spec.Trial;

/**
 * Shared helpers for the per-sample {@code resultProjection:} block
 * emitted by EXPLORE and OPTIMIZE artefacts. Used by both
 * {@link org.mavai.punit.internal.engine.explore.ExploreOutputWriter} and
 * {@link org.mavai.punit.internal.engine.optimize.OptimizeOutputWriter}.
 *
 * <p>Two responsibilities:
 *
 * <ol>
 *   <li>{@link #projectionFor(Trial)} renders one trial as the
 *       per-sample map (input / postconditions / executionTimeMs /
 *       content-or-failureDetail / optional tokensUsed).</li>
 *   <li>{@link #injectAnchorComments(String, List)} post-processes
 *       a snakeyaml-emitted YAML string to inject a deterministic
 *       diff anchor comment before every {@code sample[N]:} line —
 *       so a {@code diff} between two artefact files of the same
 *       configuration aligns at sample boundaries rather than
 *       merging adjacent samples' content.</li>
 * </ol>
 *
 * <p>Anchors are content-deterministic — two runs of the same
 * configuration produce the same anchor at the same sample position,
 * so the anchor line matches across the two files and acts as a
 * synchronisation point for the diff tool.
 */
// javai-ref: JVI-G0R8DT$ — do not remove (resolves in javai-orchestrator)
public final class ResultProjections {

    private ResultProjections() { }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * Render one trial as the per-sample projection map.
     *
     * @param trial one per-sample observation
     * @return ordered map carrying {@code input},
     *         {@code postconditions}, {@code executionTimeMs},
     *         {@code content} (on success) or {@code failureDetail}
     *         (on failure), and {@code tokensUsed} when token
     *         bookkeeping is active.
     */
    public static Map<String, Object> projectionFor(Trial<?, ?> trial) {
        Map<String, Object> entry = new LinkedHashMap<>();
        // Emit the input's position in the inputs list, not the
        // input's value. Punit's deterministic-inputs-list model
        // means the developer has the inputs in hand; rendering
        // arbitrary IT types as canonical strings is fragile
        // (Object.toString → "com.example.Foo@1a2b3c4d") and bulks
        // up artefacts without proportionate value.
        entry.put("inputIndex", trial.inputIndex());
        Map<String, Object> postconds = new LinkedHashMap<>();
        for (PostconditionResult pr : trial.outcome().postconditionResults()) {
            postconds.put(pr.description(), pr.passed() ? "passed" : "failed");
        }
        entry.put("postconditions", postconds);
        entry.put("executionTimeMs", trial.duration().toMillis());

        Outcome<?> result = trial.outcome().result();
        switch (result) {
            case Outcome.Ok<?> ok -> entry.put("content", String.valueOf(ok.value()));
            case Outcome.Fail<?> fail -> entry.put("failureDetail",
                    fail.failure().id().name() + ": " + fail.failure().message());
        }

        long tokens = trial.outcome().tokens();
        if (tokens > 0L) {
            entry.put("tokensUsed", tokens);
        }
        return entry;
    }

    /**
     * Build the {@code resultProjection:} block — a map keyed by
     * {@code sample[N]} carrying one {@link #projectionFor projection}
     * per trial in iteration order.
     */
    public static Map<String, Object> resultProjectionMap(List<? extends Trial<?, ?>> trials) {
        Map<String, Object> block = new LinkedHashMap<>();
        for (int idx = 0; idx < trials.size(); idx++) {
            block.put("sample[" + idx + "]", projectionFor(trials.get(idx)));
        }
        return block;
    }

    /**
     * Compute the per-trial diff anchors to interleave with a
     * {@code resultProjection:} block. Anchor for trial at index
     * {@code i} = first 8 hex chars of
     * {@code SHA-256(i + ":" + inputIndex)}. Same trial position +
     * same inputIndex → same anchor → diff aligns. The hash uses
     * the inputIndex rather than a canonical-string rendering of
     * the input value: the input value itself isn't persisted to
     * the artefact, and two runs of the same spec produce identical
     * inputIndex sequences by virtue of deterministic cycling.
     */
    public static List<String> anchorsFor(List<? extends Trial<?, ?>> trials) {
        List<String> anchors = new ArrayList<>(trials.size());
        for (int i = 0; i < trials.size(); i++) {
            anchors.add(anchorOf(i, trials.get(i).inputIndex()));
        }
        return anchors;
    }

    /** Anchor for one (sampleIndex, inputIndex) pair. */
    public static String anchorOf(int sampleIndex, int inputIndex) {
        String content = sampleIndex + ":" + inputIndex;
        return sha256HexPrefix(content, 8);
    }

    /**
     * Inject anchor comments before every {@code sample[N]:} line in
     * {@code yaml}, consuming anchors from {@code anchors} in the
     * order the sample lines appear. The comment indent matches the
     * indent of the {@code sample[N]:} line it annotates so the
     * resulting YAML stays well-formed.
     *
     * <p>If the YAML has more {@code sample[N]:} lines than the
     * caller supplied anchors, any surplus lines are left
     * un-annotated rather than annotated with placeholder strings —
     * a missing anchor is preferable to a misleading one.
     */
    public static String injectAnchorComments(String yaml, List<String> anchors) {
        Pattern p = Pattern.compile("^(\\s*)sample\\[(\\d+)\\]:", Pattern.MULTILINE);
        Matcher m = p.matcher(yaml);
        StringBuilder out = new StringBuilder(yaml.length() + anchors.size() * 40);
        int last = 0;
        int anchorIdx = 0;
        while (m.find()) {
            out.append(yaml, last, m.start());
            if (anchorIdx < anchors.size()) {
                String indent = m.group(1);
                out.append(indent)
                        .append("# ────── anchor:")
                        .append(anchors.get(anchorIdx))
                        .append(" ──────\n");
            }
            anchorIdx++;
            out.append(yaml, m.start(), m.end());
            last = m.end();
        }
        out.append(yaml, last, yaml.length());
        return out.toString();
    }

    private static String sha256HexPrefix(String input, int hexChars) {
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
