package org.mavai.punit.statistics.conformance;

/**
 * Receives the {@code (suite, case, binding-field)} triple of every
 * oracle assertion a conformance check makes. The coverage check
 * ({@code ConformanceCoverageTest}) re-runs the catalog's checks with a
 * collecting recorder and diffs the recorded set against the manifest's
 * obligations; the per-suite display tests run the same checks with
 * {@link #NO_OP}.
 */
@FunctionalInterface
interface ConformanceRecorder {

    void record(String suite, String caseName, String field);

    ConformanceRecorder NO_OP = (suite, caseName, field) -> { };
}
