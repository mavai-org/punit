package org.mavai.punit.api;

/**
 * Defines the consumption model for a factor source.
 *
 * <p>The factor source type determines how factors are consumed during
 * experiment or test execution, and how the source hash is computed.
 *
 * <h2>Two Models</h2>
 * <table>
 *   <tr>
 *     <th>Type</th>
 *     <th>Consumption</th>
 *     <th>Hashing</th>
 *     <th>Memory</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #LIST_CYCLING}</td>
 *     <td>Cycle through list entries</td>
 *     <td>Content hash (factor values)</td>
 *     <td>Materialized in memory</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #STREAM_SEQUENTIAL}</td>
 *     <td>Consume stream elements sequentially</td>
 *     <td>Path hash (source identity)</td>
 *     <td>Constant (no materialization)</td>
 *   </tr>
 * </table>
 *
 * @see HashableFactorSource
 */
public enum FactorSourceType {

    /**
     * List-based factor source with cycling consumption.
     *
     * <p>Factors are materialized into a list. Samples cycle through the list:
     * {@code factors.get(sampleIndex % factors.size())}.
     *
     * <p><b>Hashing</b>: Content hash (SHA-256 of factor values).
     *
     * <p><b>Best for</b>: Representative inputs, API testing, bounded factor sets.
     */
    LIST_CYCLING,

    /**
     * Stream-based factor source with sequential consumption.
     *
     * <p>Each sample consumes the next element from the stream. No cycling—
     * the stream must provide at least as many elements as the sample count.
     *
     * <p><b>Hashing</b>: Path hash (source identity: class + method + attributes).
     *
     * <p><b>Best for</b>: Generated inputs, infinite streams, probabilistic algorithms.
     */
    STREAM_SEQUENTIAL
}

