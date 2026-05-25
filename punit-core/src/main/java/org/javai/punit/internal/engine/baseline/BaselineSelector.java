package org.javai.punit.internal.engine.baseline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.covariate.CovariateCategory;
import org.javai.punit.api.covariate.Covariate;
import org.javai.punit.api.covariate.CovariateProfile;

/**
 * Covariate-aware best-match selection over a set of candidate
 * baselines.
 *
 * <h2>Algorithm</h2>
 *
 * <ol>
 *   <li><b>Hard CONFIGURATION gate.</b> Any candidate that disagrees
 *       with the current profile on a {@link CovariateCategory#CONFIGURATION}
 *       covariate is rejected outright. Configuration mismatches
 *       represent deliberate setup differences (model version,
 *       feature flag, A/B group) — applying a baseline measured
 *       under one configuration to a test running under a different
 *       configuration would invalidate the statistical claim.</li>
 *   <li><b>Score = number of matching covariates.</b> Among
 *       remaining candidates, count how many of the service contract's
 *       declared covariates match between the current profile and
 *       the candidate's profile.</li>
 *   <li><b>Highest score wins.</b> Exact match (all covariates
 *       agree) trivially scores highest; partial matches are picked
 *       in proportion to their fit.</li>
 *   <li><b>Ties broken by category priority.</b> A candidate matching
 *       a higher-priority category beats one matching a lower-priority
 *       category at the same score. {@code TEMPORAL > INFRASTRUCTURE >
 *       CONFIGURATION > OPERATIONAL > EXTERNAL_DEPENDENCY > DATA_STATE}.
 *       The comparison is over the set of categories each candidate
 *       matched on, smallest-priority-value wins.</li>
 *   <li><b>Default fallback.</b> When a candidate's profile is empty
 *       (covariate-insensitive baseline) and no covariate-tagged
 *       candidate scored positively, the empty-profile candidate is
 *       selected as the default.</li>
 * </ol>
 *
 * <h2>Covariate-insensitive service contracts</h2>
 *
 * <p>When the service contract declares no covariates ({@code declarations}
 * is empty), only candidates whose own profile is empty are
 * considered — covariate-insensitive baselines on disk continue to
 * resolve byte-identically.
 *
 * <p>The category enforcement requires the categories of the
 * declared covariates; the candidate file alone (which only carries
 * key/value pairs in the {@code covariates:} block) is not enough.
 */
// javai-ref: JVI-YN3BJ6U — do not remove (resolves in javai-orchestrator)
final class BaselineSelector {

    /**
     * Category priority ordering. Lower index = higher precedence.
     */
    private static final List<CovariateCategory> PRIORITY = List.of(
            CovariateCategory.TEMPORAL,
            CovariateCategory.INFRASTRUCTURE,
            CovariateCategory.CONFIGURATION,
            CovariateCategory.OPERATIONAL,
            CovariateCategory.EXTERNAL_DEPENDENCY,
            CovariateCategory.DATA_STATE);

    private BaselineSelector() { }

    /**
     * @param candidates  baselines whose {@code (serviceContractId,
     *                    factorsFingerprint)} already match the lookup
     * @param currentProfile  the profile resolved for the current run
     * @param declarations    the service contract's covariate declarations,
     *                        in declaration order
     * @return the best-matching baseline, or empty when no candidate
     *         is selectable (CONFIGURATION mismatch on every
     *         covariate-tagged candidate <i>and</i> no empty-profile
     *         fallback)
     */
    static Optional<BaselineRecord> select(
            List<BaselineRecord> candidates,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        return selectWithReport(candidates, currentProfile, declarations).selected;
    }

    /**
     * Selects the best-matching baseline and returns the decision
     * <em>plus</em> a list of human-readable notes capturing each
     * candidate that was rejected and why. The notes are surfaced
     * upstream through {@link BaselineLookup#notes()} so that an
     * INCONCLUSIVE verdict explains the misalignment.
     *
     * <p>Notes are only emitted on the covariate-aware path
     * ({@code declarations} non-empty); the no-declarations path
     * returns an empty notes list.
     */
    static SelectionReport selectWithReport(
            List<BaselineRecord> candidates,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(currentProfile, "currentProfile");
        Objects.requireNonNull(declarations, "declarations");

        if (candidates.isEmpty()) {
            return SelectionReport.NONE;
        }
        // No covariate declarations → only match unstamped baselines.
        // Covariate-insensitive service contracts resolve byte-identically.
        if (declarations.isEmpty()) {
            return new SelectionReport(
                    candidates.stream()
                            .filter(c -> c.covariateProfile().isEmpty())
                            .findFirst(),
                    List.of());
        }

        Map<String, CovariateCategory> categoriesByName = categoryIndex(declarations);

        List<Scored> scored = new ArrayList<>(candidates.size());
        List<String> notes = new ArrayList<>();
        for (BaselineRecord c : candidates) {
            ScoreResult result = scoreWithReason(c, currentProfile, categoriesByName);
            if (result.scored != null) {
                Scored s = result.scored;
                // A covariate-tagged candidate that matches zero
                // declared covariates is the "wrong" baseline by
                // identity — it was measured under environmental
                // conditions disjoint from the current run. Reject it;
                // the empty-profile baseline (default fallback) is the
                // only candidate allowed at score 0.
                if (s.matches == 0 && !c.covariateProfile().isEmpty()) {
                    notes.add(rejectedNote(c.filename(),
                            "no overlap with the current covariate profile"));
                    continue;
                }
                scored.add(s);
            } else {
                notes.add(rejectedNote(c.filename(), result.rejectionReason));
            }
        }

        if (scored.isEmpty()) {
            return new SelectionReport(Optional.empty(), List.copyOf(notes));
        }
        scored.sort(BEST_FIRST);
        Scored chosen = scored.get(0);
        // Note partial matches and default-fallback selections so the
        // verdict report can flag them — full-match selections are
        // expected and not noisy enough to warrant a note.
        if (chosen.matches < declarations.size()) {
            if (chosen.record.covariateProfile().isEmpty()) {
                notes.add("falling back to default (covariate-insensitive) baseline "
                        + chosen.record.filename()
                        + " — no covariate-tagged candidate matched");
            } else {
                notes.add("partial match: " + chosen.record.filename()
                        + " — matched " + chosen.matches + " of "
                        + declarations.size() + " declared covariates");
            }
        }
        return new SelectionReport(Optional.of(chosen.record), List.copyOf(notes));
    }

    private static String rejectedNote(String filename, String reason) {
        return "rejected " + filename + " — " + reason;
    }

    /** Selection result with notes for verdict-level surfacing. */
    record SelectionReport(Optional<BaselineRecord> selected, List<String> notes) {
        static final SelectionReport NONE =
                new SelectionReport(Optional.empty(), List.of());
    }

    private static Map<String, CovariateCategory> categoryIndex(
            List<Covariate> declarations) {
        Map<String, CovariateCategory> out =
                new java.util.LinkedHashMap<>(declarations.size());
        for (Covariate c : declarations) {
            out.put(c.name(), c.category());
        }
        return out;
    }

    /**
     * @return either a populated {@link ScoreResult#scored} (the
     *         candidate is a viable match) or a populated
     *         {@link ScoreResult#rejectionReason} explaining the
     *         CONFIGURATION-category mismatch that rejected it.
     *         The empty-profile candidate scores 0 with no matched
     *         categories — it loses any tie-breaker to a candidate
     *         that matched something, but wins as a fallback when no
     *         scoring candidate beats it.
     */
    private static ScoreResult scoreWithReason(
            BaselineRecord candidate,
            CovariateProfile current,
            Map<String, CovariateCategory> categoriesByName) {
        int matches = 0;
        List<CovariateCategory> matchedCategories = new ArrayList<>();
        CovariateProfile baselineProfile = candidate.covariateProfile();

        for (Map.Entry<String, CovariateCategory> declared : categoriesByName.entrySet()) {
            String name = declared.getKey();
            CovariateCategory category = declared.getValue();
            String currentValue = current.get(name).orElse(null);
            String baselineValue = baselineProfile.get(name).orElse(null);

            boolean baselineDeclares = baselineValue != null;
            boolean valuesAgree = baselineDeclares
                    && baselineValue.equals(currentValue);

            // Hard gate: a CONFIGURATION mismatch rejects the
            // candidate entirely. A baseline that omits a declared
            // CONFIGURATION covariate is treated as a mismatch — it
            // was measured before that covariate was declared, and
            // we cannot prove it was equivalent.
            if (category.isHardGate() && !valuesAgree && baselineDeclares) {
                return ScoreResult.rejected(String.format(
                        "%s mismatch on %s (current=%s, baseline=%s)",
                        category.name(), name,
                        currentValue == null ? "<unset>" : currentValue,
                        baselineValue));
            }
            if (valuesAgree) {
                matches++;
                matchedCategories.add(category);
            }
        }
        return ScoreResult.matched(new Scored(candidate, matches, matchedCategories));
    }

    private record Scored(
            BaselineRecord record,
            int matches,
            List<CovariateCategory> matchedCategories) { }

    /**
     * Outcome of evaluating one candidate: either a viable match
     * (with score) or an outright rejection (with reason text).
     */
    private record ScoreResult(Scored scored, String rejectionReason) {
        static ScoreResult matched(Scored scored) {
            return new ScoreResult(scored, null);
        }
        static ScoreResult rejected(String reason) {
            return new ScoreResult(null, reason);
        }
    }

    /**
     * Higher score first; on tie, candidate whose best matched
     * category has the lowest priority index. On further tie, the
     * candidate with the lexicographically-smaller filename wins so
     * the selection is deterministic.
     */
    private static final Comparator<Scored> BEST_FIRST =
            Comparator
                    .comparingInt((Scored s) -> -s.matches)
                    .thenComparingInt(BaselineSelector::bestCategoryPriority)
                    .thenComparing(s -> s.record.filename());

    private static int bestCategoryPriority(Scored s) {
        // Lower priority value means higher precedence. A candidate
        // matching nothing returns Integer.MAX_VALUE, sorting after
        // any candidate that matched something at the same score —
        // but at score 0 every candidate is in this state, so the
        // comparison degrades to the filename tiebreaker, which
        // happens to leave the empty-profile baseline (the shortest
        // filename for a given (ucid, ff)) at the front. That is the
        // "default baseline" fallback expressed via lexical order.
        int best = Integer.MAX_VALUE;
        for (CovariateCategory cat : s.matchedCategories) {
            int idx = PRIORITY.indexOf(cat);
            if (idx < best) {
                best = idx;
            }
        }
        return best;
    }
}
