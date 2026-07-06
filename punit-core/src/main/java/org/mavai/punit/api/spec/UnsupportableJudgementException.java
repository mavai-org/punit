package org.mavai.punit.api.spec;

import org.opentest4j.TestAbortedException;

/**
 * Thrown by the measure builder's gating terminal when a normative
 * judgement is unsupportable at the run's sample size — the sample
 * count cannot support the stipulated threshold at the criterion's
 * confidence, even with a perfect observation, so no met/failed
 * judgement can be rendered.
 *
 * <p>A subtype of {@link TestAbortedException}: the host harness
 * treats the run as aborted, not failed — an underpowered run is a
 * configuration inadequacy, not evidence against the service. The
 * type identifies the cause for listeners and report tooling; the
 * message states the feasible minimum sample count at which the
 * judgement becomes supportable.
 *
 * <p>Thrown only after the baseline artefact is on disk — the
 * gating terminal's persistence-before-assertion obligation applies
 * to the unsupportable case exactly as to a failed judgement, and
 * the artefact records the criterion's marker with its
 * unsupportable state.
 */
public class UnsupportableJudgementException extends TestAbortedException {

    private static final long serialVersionUID = 1L;

    public UnsupportableJudgementException(String message) {
        super(message);
    }
}
