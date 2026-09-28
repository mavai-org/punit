package org.mavai.punit.statistics;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * A configuration refused before any sample runs (companion §5.7.1).
 *
 * <p>The constants are declared in the fixed order in which a refusal
 * reports them: a configuration with several invalid parts names every
 * applicable code, in this order, so the developer can correct them all
 * at once. A configuration with any invalid part is refused whole.
 */
public enum ConfigurationError {

    /**
     * A test whose planned sample size exceeds the sample size of the
     * baseline run it consumes. Judged once, on the two samplings, for
     * pass-rate and latency criteria alike and whatever the intent. It is
     * mavai's design policy — the baseline is at least as large as any
     * test that consumes it — not a statistical necessity.
     */
    TEST_LARGER_THAN_BASELINE,

    /**
     * A normative design (a pass-rate requirement or an explicit latency
     * requirement) too small for any outcome to demonstrate compliance,
     * refused under VERIFICATION intent only.
     */
    COMPLIANCE_INFEASIBLE;

    /** Every applicable code once, in the fixed reporting order. */
    public static List<ConfigurationError> ordered(Collection<ConfigurationError> codes) {
        Objects.requireNonNull(codes, "codes");
        return codes.isEmpty()
                ? List.of()
                : List.copyOf(EnumSet.copyOf(codes));
    }
}
