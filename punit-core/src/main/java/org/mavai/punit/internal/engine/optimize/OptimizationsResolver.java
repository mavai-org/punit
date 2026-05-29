package org.mavai.punit.internal.engine.optimize;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the directory under which OPTIMIZE artefacts are written.
 *
 * <p>Precedence:
 *
 * <ol>
 *   <li>System property {@value #OPTIMIZATIONS_DIR_PROPERTY} — explicit
 *       per-run override (set by the punit Gradle plugin's
 *       {@code exp} task, or by ad-hoc invocations).</li>
 *   <li>Default {@value #DEFAULT_DIR} — the convention path
 *       (gitignored by virtue of {@code build/} being ignored).</li>
 * </ol>
 *
 * <p>The directory is not required to exist — the artefact emitter
 * creates it on first write.
 *
 * <p>Mirror of {@link org.mavai.punit.internal.engine.baseline.BaselineResolver}
 * for optimize output. Explore output has its own
 * {@link org.mavai.punit.internal.engine.explore.ExplorationsResolver}.
 */
public final class OptimizationsResolver {

    /** System-property key for an explicit optimizations directory. */
    public static final String OPTIMIZATIONS_DIR_PROPERTY = "punit.optimizations.outputDir";

    /** Default optimizations directory under the build output tree. */
    public static final String DEFAULT_DIR = "build/punit/optimizations";

    private OptimizationsResolver() { }

    /**
     * @return the configured optimizations directory.
     */
    public static Path resolveDir() {
        String override = System.getProperty(OPTIMIZATIONS_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(DEFAULT_DIR);
    }
}
