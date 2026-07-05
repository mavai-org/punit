package org.mavai.punit.api.spec;

import java.util.Objects;
import java.util.Optional;

import org.mavai.punit.statistics.NormativeJudgementEvaluator.Judgement;

/**
 * A measure run's judgement of one normative criterion against its
 * stipulated threshold — the experiment-time verdict for normative
 * criteria.
 *
 * <p>Pairs the statistical {@link Judgement} (state, stipulated
 * threshold, confidence, observed rate, lower bound, feasible
 * minimum) with the criterion's identity and its author-supplied
 * contract reference (the audit pointer to the stipulation's source
 * document), which is diagnostic context rather than statistics.
 *
 * <p>The baseline artefact records the judgement's state, stipulated
 * threshold, and confidence per criterion — a durable marker that a
 * later reader of the file sees not only what was measured but how
 * the measurement stood relative to a stipulation in force at
 * measure time. The marker is additive and purely documentary:
 * baseline resolution and threshold derivation ignore it.
 *
 * @param criterionId    the judged criterion's stable identifier
 * @param judgement      the statistical judgement
 * @param stipulationRef the author-supplied contract reference for
 *                       the stipulation, when declared
 */
public record NormativeJudgement(
        String criterionId,
        Judgement judgement,
        Optional<String> stipulationRef) {

    public NormativeJudgement {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(judgement, "judgement");
        Objects.requireNonNull(stipulationRef, "stipulationRef");
        if (criterionId.isBlank()) {
            throw new IllegalArgumentException("criterionId must not be blank");
        }
    }
}
