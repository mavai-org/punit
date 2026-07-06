package org.mavai.punit.api.spec;

import java.util.List;
import java.util.Objects;

/**
 * The emission summary of a completed measure run — what the
 * framework's baseline emission hands back to the experiment
 * terminals once the baseline artefact has been written.
 *
 * <p>Carries the artefact's identity (service contract, sample
 * count, filename) plus the run's normative judgements: one
 * {@link NormativeJudgement} per normative criterion the contract
 * declared, empty for a purely empirical contract. The terminals
 * render and — under the gating terminal — assert on the judgements
 * strictly after the artefact is on disk.
 *
 * @param serviceContractId   the measured service contract's identifier
 * @param sampleCount         the run's total sample count
 * @param filename            the baseline artefact's canonical filename
 * @param normativeJudgements the run's normative judgements, in
 *                            contract declaration order; empty when the
 *                            contract declares no normative criteria
 * @param judgementRendering  the judgements' console rendering — the
 *                            characterisation-plus-judgement block the
 *                            experiment prints; empty exactly when
 *                            {@code normativeJudgements} is empty
 */
public record MeasuredBaseline(
        String serviceContractId,
        int sampleCount,
        String filename,
        List<NormativeJudgement> normativeJudgements,
        String judgementRendering) {

    public MeasuredBaseline {
        Objects.requireNonNull(serviceContractId, "serviceContractId");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(normativeJudgements, "normativeJudgements");
        Objects.requireNonNull(judgementRendering, "judgementRendering");
        normativeJudgements = List.copyOf(normativeJudgements);
    }
}
