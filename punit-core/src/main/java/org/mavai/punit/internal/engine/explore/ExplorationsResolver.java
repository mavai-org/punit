package org.mavai.punit.internal.engine.explore;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the directory under which EXPLORE artefacts are written.
 *
 * <p>Precedence:
 *
 * <ol>
 *   <li>System property {@value #EXPLORATIONS_DIR_PROPERTY} — explicit
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
 * for explore output. Optimize output has its own
 * {@link org.mavai.punit.internal.engine.optimize.OptimizationsResolver}.
 */
public final class ExplorationsResolver {

    /** System-property key for an explicit explorations directory. */
    public static final String EXPLORATIONS_DIR_PROPERTY = "punit.explorations.outputDir";

    /** Default explorations directory under the build output tree. */
    public static final String DEFAULT_DIR = "build/punit/explorations";

    private ExplorationsResolver() { }

    /**
     * @return the configured explorations directory.
     */
    public static Path resolveDir() {
        String override = System.getProperty(EXPLORATIONS_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(DEFAULT_DIR);
    }
}
