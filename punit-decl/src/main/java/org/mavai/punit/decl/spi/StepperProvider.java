package org.mavai.punit.decl.spi;

import java.util.Map;
import org.mavai.punit.api.spec.FactorsStepper;

/**
 * A built-in optimize stepper: the extension seam through which a
 * module registers what an optimization entry's {@code stepper:} key
 * can name without a bindings-class registration — discovered via
 * {@link java.util.ServiceLoader}, the {@link ServiceType} precedent
 * (punit-lm provides {@code prompt-engineer}). User steppers register
 * through the bindings class instead; a built-in's name cannot be
 * shadowed.
 *
 * <p>A built-in validates its own {@code stepper-config:} record
 * against its documented schema — its keys may be optional, unlike a
 * user factory, whose parameter list is the schema and whose keys are
 * all required. A misfit throws (an {@link IllegalStateException}
 * subtype) at load, before any sample.
 */
public interface StepperProvider {

    /** The stepper name an optimization entry's {@code stepper:} key resolves to. */
    String name();

    /**
     * Validates the entry's {@code stepper-config:} record and returns
     * the stepper. Runs at contract-load time.
     *
     * @param stepperConfig the entry's configuration record, empty when absent
     */
    FactorsStepper<Map<String, Object>> create(Map<String, Object> stepperConfig);
}
