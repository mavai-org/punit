package org.mavai.punit.api;

/**
 * Empty factor record for service contracts whose behaviour does not depend
 * on factor values. Use as the {@code FT} type parameter in
 * {@link Sampling}, {@link ServiceContract}, and the spec builders when the
 * test or experiment exercises no factors.
 *
 * <p>Pair with the factor-less {@code PUnit.measuring(sampling)} /
 * {@code PUnit.testing(sampling)} overloads, which bind
 * {@link #INSTANCE} on the author's behalf:
 *
 * <pre>{@code
 * Sampling<NoFactors, String, String> sampling =
 *         Sampling.of(nf -> new MyServiceContract(), 1000, "input1");
 * PUnit.measuring(sampling).run();
 * }</pre>
 *
 * <p>Records have structural equality, so all instances are equal
 * and interchangeable. {@link #INSTANCE} exists for the common case
 * where allocating a fresh value adds no information.
 *
 * <p>{@link FactorBundle#of(Object) FactorBundle.of(NoFactors.INSTANCE)}
 * yields the empty bundle, which the baseline-filename serialiser
 * treats by omitting the {@code factorBundleHash} segment — the
 * file is named purely by service contract and inputs, exactly as a
 * factor-less run should be.
 */
public record NoFactors() {

    /**
     * Canonical instance. Equivalent to {@code new NoFactors()}; use
     * either interchangeably.
     */
    public static final NoFactors INSTANCE = new NoFactors();
}
