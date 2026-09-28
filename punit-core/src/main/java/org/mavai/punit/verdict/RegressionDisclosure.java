package org.mavai.punit.verdict;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What a {@code regression/fisher} report discloses about its design
 * (Statistical Companion §3.4, §5.3, §5.6). All informational.
 *
 * @param sizeAtAssumedCommonRate      the procedure's false-degradation-signal
 *                                     probability were the unknown common rate
 *                                     the baseline's observed rate; not a
 *                                     property of the run
 * @param designAlternativeRate        the true rate at which the test is to
 *                                     reach its target power, when declared
 * @param designPower                  the power at that rate with the baseline
 *                                     and the test both yet to be drawn
 * @param resolvedTestPower            the power at that rate of this test,
 *                                     whose cutoff the observed baseline fixed
 * @param minimumDetectableDegradation the drop detected with 80% design
 *                                     power, when no alternative is declared;
 *                                     it inverts the design power
 */
public record RegressionDisclosure(
        OptionalDouble sizeAtAssumedCommonRate,
        OptionalDouble designAlternativeRate,
        OptionalDouble designPower,
        OptionalDouble resolvedTestPower,
        OptionalDouble minimumDetectableDegradation) {

    public RegressionDisclosure {
        Objects.requireNonNull(sizeAtAssumedCommonRate, "sizeAtAssumedCommonRate");
        Objects.requireNonNull(designAlternativeRate, "designAlternativeRate");
        Objects.requireNonNull(designPower, "designPower");
        Objects.requireNonNull(resolvedTestPower, "resolvedTestPower");
        Objects.requireNonNull(minimumDetectableDegradation, "minimumDetectableDegradation");
    }
}
