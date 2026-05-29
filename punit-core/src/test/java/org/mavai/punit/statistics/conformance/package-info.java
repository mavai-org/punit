/**
 * Conformance tests validating punit's statistics engine against canonical
 * reference data published by javai-R.
 *
 * <p>javai-R generates expected outputs using R's well-vetted statistical
 * functions. The tests in this package ensure that punit's Java implementations
 * produce identical results within stated tolerances, guaranteeing statistical
 * equivalence across all javai framework implementations (punit, feotest,
 * and future frameworks).
 *
 * <p>Each JSON suite declares its own tolerance. Tests use the declared
 * tolerance — not a hardcoded value — so that tolerance decisions are owned
 * by javai-R, not by individual frameworks.
 *
 * <p>This package is the foundation of javai's statistical integrity. If a
 * conformance test fails, the framework's statistical guarantees are in question
 * and the failure must be resolved before release.
 *
 * @see <a href="https://github.com/javai-org/javai-R">javai-R — the statistical oracle</a>
 */
package org.mavai.punit.statistics.conformance;
