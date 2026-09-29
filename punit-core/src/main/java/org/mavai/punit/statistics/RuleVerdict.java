package org.mavai.punit.statistics;

/**
 * The outcome a decision rule reaches on one decision — kept here so the
 * statistics package states its results without depending on the
 * framework's verdict types.
 */
public enum RuleVerdict {
    PASS,
    FAIL,
    INCONCLUSIVE
}
