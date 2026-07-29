package org.mavai.punit.decl.internal.run;

import java.util.Map;
import org.mavai.punit.api.spec.FactorsStepper;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.StepperProvider;

/**
 * A test-scope built-in stepper, registered via the test classpath's
 * {@code META-INF/services} — exercises the ServiceLoader discovery
 * route punit-lm's {@code prompt-engineer} uses, a built-in's own
 * (optional-keyed) {@code stepper-config:} validation, and the
 * built-in-name shadowing refusal.
 */
public class FixedStepperProvider implements StepperProvider {

    @Override
    public String name() {
        return "fixed-step";
    }

    @Override
    public FactorsStepper<Map<String, Object>> create(Map<String, Object> stepperConfig) {
        for (String key : stepperConfig.keySet()) {
            if (!key.equals("steps")) {
                throw new ContractConfigurationException(
                        "stepper 'fixed-step': unknown `stepper-config:` key `" + key
                                + ":` — the schema is: steps (optional)");
            }
        }
        int steps = stepperConfig.get("steps") instanceof Integer declared ? declared : 0;
        return (current, history) -> history.size() > steps
                ? NextFactor.stop()
                : NextFactor.next(current);
    }
}
