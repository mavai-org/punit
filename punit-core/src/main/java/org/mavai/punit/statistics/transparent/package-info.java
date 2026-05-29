/**
 * Transparent Statistics Mode configuration and vocabulary.
 *
 * <p>This package provides configuration and symbol constants for transparent
 * statistics output. Rendering is handled by
 * {@link org.mavai.punit.internal.reporting.VerdictTextRenderer}.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.mavai.punit.statistics.transparent.TransparentStatsConfig} -
 *       Configuration with precedence: annotation &gt; system property &gt; env var &gt; default</li>
 *   <li>{@link org.mavai.punit.statistics.transparent.StatisticalVocabulary} -
 *       Mathematical symbols with Unicode/ASCII fallback</li>
 *   <li>{@link org.mavai.punit.statistics.transparent.BaselineData} -
 *       Baseline data transfer object</li>
 * </ul>
 *
 * <h2>Enabling Transparent Mode</h2>
 * <pre>
 * # Via system property
 * ./gradlew test -Dpunit.stats.transparent=true
 *
 * # Via environment variable
 * PUNIT_STATS_TRANSPARENT=true ./gradlew test
 *
 * # Via annotation
 * {@literal @}ProbabilisticTest(samples = 100, transparentStats = true)
 * void myTest() { ... }
 * </pre>
 *
 * @see org.mavai.punit.statistics.transparent.TransparentStatsConfig
 */
package org.mavai.punit.statistics.transparent;
