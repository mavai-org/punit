package org.javai.punit.api.criterion;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Contract;
import org.javai.punit.api.PostconditionResult;
import org.javai.punit.api.ServiceContractOutcome;
import org.javai.punit.api.TokenTracker;
import org.junit.jupiter.api.Test;

/**
 * Step 2's aggregate-mapping default: an INCONCLUSIVE per-sample
 * criterion result is folded into the verdict path as a single
 * synthetic failed {@link PostconditionResult} preserving the
 * reason's name and message. Verifies this end-to-end through the
 * engine's per-sample apply path.
 */
class InconclusiveFoldsIntoFailTest {

    static final class TransformingContract implements Contract<String, String> {
        @Override
        public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }

        @Override
        public Criteria<String> criteria() {
            return Criteria.meeting().<String>zeroFailures()
                    .transforming(s -> {
                        try {
                            return Outcome.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.fail("parse-error", "not a number: " + s);
                        }
                    })
                    .satisfies("positive", (Integer n) -> n > 0
                            ? Outcome.ok()
                            : Outcome.fail("non-positive", "n=" + n))
                    .name("parsed-positive");
        }
    }

    @Test
    void inconclusiveFromTransformFailureSurfacesAsSingleSyntheticFailedResult() {
        var contract = new TransformingContract();
        TokenTracker tracker = TokenTracker.create();

        ServiceContractOutcome<String, String> outcome =
                contract.apply("not-a-number", tracker);

        // The engine folds INCONCLUSIVE into a single failed
        // PostconditionResult identified by the criterion's id and
        // carrying the reason's name and message.
        assertThat(outcome.postconditionResults()).hasSize(1);
        PostconditionResult r = outcome.postconditionResults().get(0);
        assertThat(r.failed()).isTrue();
        assertThat(r.description()).contains("parsed-positive");
        assertThat(r.failureName()).contains("parse-error");
        assertThat(r.failureReason()).contains("not a number: not-a-number");
    }

    @Test
    void transformSucceedsAndPostconditionFailsSurfacesPostconditionResult() {
        var contract = new TransformingContract();
        TokenTracker tracker = TokenTracker.create();

        ServiceContractOutcome<String, String> outcome =
                contract.apply("-3", tracker);

        // Transform succeeded (-3 parses), postcondition failed
        // (-3 is not positive). The verdict path sees one
        // PostconditionResult — the failed postcondition — not the
        // synthetic transform-failure entry.
        assertThat(outcome.postconditionResults()).hasSize(1);
        PostconditionResult r = outcome.postconditionResults().get(0);
        assertThat(r.failed()).isTrue();
        assertThat(r.description()).isEqualTo("positive");
        assertThat(r.failureName()).contains("non-positive");
    }

    @Test
    void transformAndPostconditionBothSucceedSurfacesPassingResult() {
        var contract = new TransformingContract();
        TokenTracker tracker = TokenTracker.create();

        ServiceContractOutcome<String, String> outcome =
                contract.apply("7", tracker);

        assertThat(outcome.postconditionResults()).hasSize(1);
        assertThat(outcome.postconditionResults().get(0).passed()).isTrue();
    }
}
