package org.mavai.punit.api.covariate;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a group of region codes forming a single partition for the region covariate.
 *
 * <p>Regions within a group are considered statistically equivalent.
 * Region codes must be valid ISO 3166-1 alpha-2 country codes.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Two groups: {FR, DE} and {UK, IR}. Three partitions (including remainder).
 * covariateRegion = { @RegionGroup({"FR", "DE"}), @RegionGroup({"UK", "IR"}) }
 *
 * // Single-element group (braces optional for single element)
 * covariateRegion = { @RegionGroup("US") }
 * }</pre>
 *
 * @see ServiceContract#covariateRegion()
 */
@Documented
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface RegionGroup {

    /**
     * The region codes in this group.
     *
     * <p>Must be valid ISO 3166-1 alpha-2 country codes.
     *
     * @return one or more region codes
     */
    String[] value();

    /**
     * Optional label for this group.
     *
     * <p>If empty, the framework auto-derives a label by joining the
     * region codes (e.g. "FR_DE").
     *
     * @return the group label, or empty for auto-derivation
     */
    String label() default "";
}
