package org.mavai.punit.api;

import java.util.stream.Stream;

/**
 * A factor source that provides both factors and a stable hash identifier.
 *
 * <p>The hash identifies the factor source, enabling consistency verification
 * between experiments and probabilistic tests. If two runs use the same
 * {@code HashableFactorSource} (same hash), they will consume identical factors
 * when using first-N prefix selection.
 *
 * <h2>Key Properties</h2>
 * <ul>
 *   <li><b>Source-owned hash</b>: The hash is a property of the source itself,
 *       not computed from factors consumed during a run</li>
 *   <li><b>Always available</b>: The hash can be queried at any time,
 *       independent of whether factors have been consumed</li>
 *   <li><b>Sample count irrelevant</b>: Hash comparison works regardless of
 *       how many factors each run consumed</li>
 * </ul>
 *
 * <h2>Implementation Options</h2>
 * <ol>
 *   <li><b>Materialized factors</b>: Compute hash from all factor values (recommended)</li>
 *   <li><b>Configuration identity</b>: Hash based on source configuration</li>
 *   <li><b>Developer-provided</b>: Explicit version identifier</li>
 * </ol>
 *
 * @see FactorSource
 * @see FactorArguments
 */
public interface HashableFactorSource {

    /**
     * Returns a stream of factor arguments.
     *
     * <p>Each call returns a fresh stream starting from the first factor.
     * For first-N prefix selection, consumers take factors from the beginning
     * of this stream.
     *
     * @return a stream of factor arguments
     */
    Stream<FactorArguments> factors();

    /**
     * Returns a hash identifying this factor source.
     *
     * <p>The hash should be stable: the same source should always return
     * the same hash. Different sources (different factors or ordering)
     * should return different hashes.
     *
     * <p>This method may trigger lazy computation (e.g., materializing
     * factors to compute their hash), but the result should be cached
     * for subsequent calls.
     *
     * @return a hex-encoded hash string identifying this source
     */
    String getSourceHash();

    /**
     * Returns the name of this factor source.
     *
     * <p>This is typically the method name or a developer-provided identifier,
     * used for logging and spec storage.
     *
     * @return the source name
     */
    String getSourceName();

    /**
     * Returns the type of this factor source, which determines consumption behavior.
     *
     * <ul>
     *   <li>{@link FactorSourceType#LIST_CYCLING}: Factors cycle through a materialized list</li>
     *   <li>{@link FactorSourceType#STREAM_SEQUENTIAL}: Factors consumed sequentially from stream</li>
     * </ul>
     *
     * @return the factor source type
     */
    FactorSourceType getSourceType();
}

