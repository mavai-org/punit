package org.mavai.punit.api.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.mavai.punit.api.PostconditionResult;
import org.mavai.punit.api.ServiceContractOutcome;
import org.mavai.punit.api.criterion.Criterion;
import org.mavai.punit.api.criterion.CriterionSampleResult;

/**
 * The postcondition standings: per {@code (input, check)}, how many
 * trials passed, failed, or were skipped, with the observed pass
 * fraction — the per-check tally the evaluator already computes per
 * sample and previously discarded once the criterion's statistics were
 * taken.
 *
 * <p><strong>Descriptive only, by design.</strong> No interval, no
 * threshold, no per-check verdict: the run is sized to support the
 * criterion's claim, not one claim per check, and postconditions on one
 * response are correlated. The criterion remains the only judged unit;
 * the standings say <em>which</em> checks miss and how often. Each
 * check carries its required/optional standing and each criterion its
 * declared optional-slack budget verbatim, so a consumer can flag
 * partial credit from the stated data alone.
 *
 * <p>Derived post-hoc from a {@link SampleSummary}'s full trial
 * history — a pure aggregation of recorded per-check outcomes, which
 * stay true even when the acceptance predicate softened the trial.
 */
// mavai-ref: JVI-N9ZEY24 — do not remove (resolves in mavai-orchestrator)
public record PostconditionStandings(List<CriterionStandings> criteria) {

    public PostconditionStandings {
        criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
    }

    /** Whether any criterion states any rows. */
    public boolean isEmpty() {
        return criteria.stream().allMatch(criterion -> criterion.rows().isEmpty());
    }

    /**
     * One criterion's standings: its declared optional-slack budget —
     * verbatim, exactly as the author spelled it, absent iff
     * undeclared — and one row per {@code (input, check)}.
     */
    public record CriterionStandings(
            String criterionId, Optional<String> optionalSlack, List<Row> rows) {

        public CriterionStandings {
            Objects.requireNonNull(criterionId, "criterionId");
            Objects.requireNonNull(optionalSlack, "optionalSlack");
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    /**
     * One {@code (input, check)} tally. A skip records a trial the
     * check was applicable to but never judged — the criterion's chain
     * did not run (a transform failure, or no delivered value).
     */
    public record Row(
            int inputIndex, String check, boolean optional,
            int passed, int failed, int skipped) {

        /** Trials this check was applicable to. */
        public int trials() {
            return passed + failed + skipped;
        }

        /** The observed pass fraction over the check's applicable trials. */
        public double observedFraction() {
            int total = trials();
            return total == 0 ? 0.0 : (double) passed / total;
        }
    }

    /**
     * Derives the standings from a summary's trial history, grouped by
     * the given criteria (which carry each criterion's declared slack).
     * A criterion whose chain never ran on any trial states no rows —
     * there is nothing to stand.
     */
    public static PostconditionStandings from(
            SampleSummary<?> summary, List<? extends Criterion<?>> criteria) {
        Map<String, Map<RowKey, MutableTally>> tallies = new LinkedHashMap<>();
        Map<String, Set<CheckIdentity>> knownChecks = new LinkedHashMap<>();
        for (Criterion<?> criterion : criteria) {
            tallies.put(criterion.id(), new LinkedHashMap<>());
            knownChecks.put(criterion.id(), new LinkedHashSet<>());
        }
        // First pass: judged trials — true per-check outcomes, and the
        // check vocabulary each criterion actually exercises.
        for (Trial<?, ?> trial : summary.trials()) {
            for (CriterionSampleResult result : trial.outcome().criterionSampleResults()) {
                Map<RowKey, MutableTally> rows = tallies.get(result.criterionId());
                if (rows == null || result.reason().isPresent()) {
                    continue;
                }
                for (PostconditionResult check : result.postconditionResults()) {
                    knownChecks.get(result.criterionId())
                            .add(new CheckIdentity(check.description(), check.required()));
                    MutableTally tally = rows.computeIfAbsent(
                            new RowKey(trial.inputIndex(), check.description()),
                            key -> new MutableTally(!check.required()));
                    if (check.passed()) {
                        tally.passed++;
                    } else {
                        tally.failed++;
                    }
                }
            }
        }
        // Second pass: skipped trials — the chain never ran (transform
        // failure, or an apply-level failure that skipped every
        // criterion), so every known check of the criterion was
        // applicable but unjudged.
        for (Trial<?, ?> trial : summary.trials()) {
            skippedCriterionIds(trial.outcome(), tallies.keySet()).forEach(criterionId -> {
                Map<RowKey, MutableTally> rows = tallies.get(criterionId);
                for (CheckIdentity check : knownChecks.get(criterionId)) {
                    rows.computeIfAbsent(
                            new RowKey(trial.inputIndex(), check.description()),
                            key -> new MutableTally(!check.required())).skipped++;
                }
            });
        }
        List<CriterionStandings> standings = new ArrayList<>();
        for (Criterion<?> criterion : criteria) {
            List<Row> rows = new ArrayList<>();
            tallies.get(criterion.id()).forEach((key, tally) -> rows.add(new Row(
                    key.inputIndex(), key.check(), tally.optional,
                    tally.passed, tally.failed, tally.skipped)));
            rows.sort(java.util.Comparator
                    .comparingInt(Row::inputIndex)
                    .thenComparing(Row::check));
            standings.add(new CriterionStandings(
                    criterion.id(),
                    criterion.optionalSlack().map(slack -> slack.display()),
                    rows));
        }
        return new PostconditionStandings(standings);
    }

    private static Set<String> skippedCriterionIds(
            ServiceContractOutcome<?, ?> outcome, Set<String> allCriterionIds) {
        if (outcome.criterionSampleResults().isEmpty()) {
            // No per-criterion detail at all — an apply-level failure
            // (or a synthesised defect outcome): every criterion's
            // checks were applicable and unjudged.
            return allCriterionIds;
        }
        Set<String> skipped = new LinkedHashSet<>();
        for (CriterionSampleResult result : outcome.criterionSampleResults()) {
            if (result.reason().isPresent()) {
                skipped.add(result.criterionId());
            }
        }
        return skipped;
    }

    private record RowKey(int inputIndex, String check) {}

    private record CheckIdentity(String description, boolean required) {}

    private static final class MutableTally {
        final boolean optional;
        int passed;
        int failed;
        int skipped;

        MutableTally(boolean optional) {
            this.optional = optional;
        }
    }
}
