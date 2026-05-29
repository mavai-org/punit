package org.mavai.punit.internal.engine;

import org.mavai.punit.api.PostconditionResult;
import org.mavai.punit.api.spec.SampleClassification;

/**
 * Emits one line to {@code System.err} for every failed contract
 * evaluation observed by the engine. The lines are written
 * synchronously as each sample is recorded, so an operator watching
 * the build console sees failure detail streaming in step with the
 * progress counter rather than buffered to a post-run flush.
 *
 * <p>Each line carries the {@link #MARKER} token, the sample index
 * (zero-based, monotonic within the run), the failure kind, the
 * clause name, and a single-line reason — for example:
 *
 * <pre>{@code
 * [PUNIT-FAIL] sample 17 postcondition: "valid JSON": Parse error at line 5
 * [PUNIT-FAIL] sample 18 apply: timeout: upstream did not respond within 5s
 * [PUNIT-FAIL] sample 19 match: translation equals reference: differs at index 7
 * [PUNIT-FAIL] sample 20 defect: NullPointerException: cart is null
 * }</pre>
 *
 * <p>A sample with {@code N} failed postconditions emits {@code N}
 * lines; an apply-level failure, a match mismatch, or a defect emits
 * exactly one. Embedded newlines and carriage returns in the clause
 * or reason are replaced with a single space so the line stays
 * single-line for downstream tooling.
 *
 * <h2>Channel choice</h2>
 *
 * <p>Stderr was chosen deliberately. Stdout carries the
 * {@code [PUNIT-PROGRESS] m/n} counter that the punit-gradle-plugin
 * reformats into in-place build-terminal updates; mixing failure
 * detail onto that channel would interleave with the counter and
 * corrupt the rendering. Stderr is not intercepted by the progress
 * bridge and passes through Gradle's {@code STANDARD_ERROR}
 * decoration unchanged.
 */
final class FailureLineEmitter {

    /**
     * Token prefixed to every failure-line emission. Used by log
     * collectors and downstream tooling to recognise failure-detail
     * lines and split them by the {@code kind} token that follows.
     */
    static final String MARKER = "[PUNIT-FAIL]";

    /** Sentinel apply-failure name engine-synthesised for thrown defects. */
    private static final String DEFECT_FAILURE_NAME = "defect";

    private FailureLineEmitter() { }

    /**
     * Emit one line per failed contract evaluation observed in
     * {@code classification}. Apply-level failures (including
     * synthesised defects) and per-clause postcondition failures are
     * all routed through the same line shape; the {@code kind} token
     * discriminates them.
     */
    static void emit(int sampleIndex, SampleClassification classification) {
        if (classification.applyFailed()) {
            String name = classification.applyFailureName().orElse("");
            String message = classification.applyFailureMessage().orElse("");
            String kind = DEFECT_FAILURE_NAME.equals(name) ? "defect" : "apply";
            emitLine(sampleIndex, kind, name, message);
            return;
        }
        for (PostconditionResult result : classification.postconditionResults()) {
            if (!result.failed()) {
                continue;
            }
            String reason = result.failureReason().orElse(result.description());
            emitLine(sampleIndex, "postcondition", result.description(), reason);
        }
    }

    private static void emitLine(int sampleIndex, String kind, String clause, String reason) {
        System.err.println(MARKER
                + " sample " + sampleIndex
                + " " + kind + ": " + oneLine(clause) + ": " + oneLine(reason));
        System.err.flush();
    }

    private static String oneLine(String s) {
        // Collapse embedded line breaks so downstream tooling that
        // splits on newline sees one failure as exactly one line.
        return s.replace('\r', ' ').replace('\n', ' ');
    }
}
