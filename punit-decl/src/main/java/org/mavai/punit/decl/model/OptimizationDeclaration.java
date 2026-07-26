package org.mavai.punit.decl.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One parsed {@code optimizations:} entry: which stepper proposes
 * configurations, which scorer judges an iteration, the objective
 * direction, the termination controls, and the iteration-0 overlay.
 * Stepper and scorer names are resolved against the registries at
 * instantiation, not here.
 *
 * @param id the entry's identity — the run's and its artefact's name;
 *     defaults to the service name for a sole entry
 * @param stepper the registered stepper name
 * @param stepperConfig the stepper factory's configuration mapping
 * @param scorer the registered scorer name
 * @param objective the objective direction
 * @param maxIterations the hard cap on iteration count
 * @param noImprovementWindow the plateau window, or {@code null} when
 *     plateau detection is off
 * @param initial iteration 0's configuration replacements over the
 *     baseline; empty when iteration 0 is the baseline itself
 */
public record OptimizationDeclaration(
        String id,
        String stepper,
        Map<String, Object> stepperConfig,
        String scorer,
        Objective objective,
        int maxIterations,
        Integer noImprovementWindow,
        Map<String, Object> initial) {

    /** The objective direction of an optimize run. */
    public enum Objective {
        MAXIMIZE("maximize"),
        MINIMIZE("minimize");

        private final String key;

        Objective(String key) {
            this.key = key;
        }

        /** The objective as written in the services file. */
        public String key() {
            return key;
        }

        /** The objective for a file value, or {@code null} when unknown. */
        public static Objective forKey(String key) {
            for (Objective objective : values()) {
                if (objective.key.equals(key)) {
                    return objective;
                }
            }
            return null;
        }
    }

    public OptimizationDeclaration {
        stepperConfig = new LinkedHashMap<>(stepperConfig);
        initial = new LinkedHashMap<>(initial);
    }

    @Override
    public Map<String, Object> stepperConfig() {
        return Map.copyOf(stepperConfig);
    }

    @Override
    public Map<String, Object> initial() {
        return Map.copyOf(initial);
    }
}
