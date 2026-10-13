package org.mavai.punit.gradle

import org.gradle.api.provider.Property

/**
 * DSL extension for configuring PUnit experiment tasks.
 *
 * ```kotlin
 * punit {
 *     baselineDir.set("src/test/resources/punit/baselines")
 *     explorationsDir.set(layout.buildDirectory.dir("punit/explorations").map { it.asFile.absolutePath })
 *     optimizationsDir.set(layout.buildDirectory.dir("punit/optimizations").map { it.asFile.absolutePath })
 *     configureTestTask.set(true)
 *     excludeTestSubjects.set(true)
 *     mavaiVersion.set("0.23.0")
 * }
 * ```
 *
 * The locations differ deliberately: MEASURE baselines are inputs to
 * subsequent `@ProbabilisticTest` runs (kept under source, committed),
 * while EXPLORE and OPTIMIZE outputs are human-review artefacts (kept under
 * `build/`, not committed). Override any default as shown above.
 */
abstract class PUnitExperimentExtension {

    /**
     * Directory MEASURE writes baselines to and tests read them from, passed
     * to the `test` and `exp` tasks as `punit.baseline.dir`. Unset by default:
     * punit-core then resolves `PUNIT_BASELINE_DIR`, else
     * `src/test/resources/punit/baselines`. A `punit.baseline.dir` the build
     * sets on a task itself takes precedence.
     */
    abstract val baselineDir: Property<String>

    /**
     * Removed: punit-core never read the directory this named. Setting it
     * fails the build with a pointer to [baselineDir].
     */
    @Deprecated("punit-core never read this directory; use baselineDir", ReplaceWith("baselineDir"))
    abstract val specsDir: Property<String>

    /** Output directory for EXPLORE results. Default: `<buildDir>/punit/explorations` (transient, not tracked). */
    abstract val explorationsDir: Property<String>

    /** Output directory for OPTIMIZE results. Default: `<buildDir>/punit/optimizations` (transient, not tracked). */
    abstract val optimizationsDir: Property<String>

    /** Whether to configure the `test` task (exclude experiment tags, forward properties, etc.). Default: `true` */
    abstract val configureTestTask: Property<Boolean>

    /** Whether to exclude testsubjects directories from test and experiment tasks. Default: `true` */
    abstract val excludeTestSubjects: Property<Boolean>

    /**
     * Version of the `mavai` renderer (`org.mavai:mavai` on Maven Central) the
     * `punitReport` task resolves and runs.
     * Default: the version this plugin was built against. The `MAVAI_BIN`
     * environment variable names an executable to use instead of any resolved one.
     */
    abstract val mavaiVersion: Property<String>
}
