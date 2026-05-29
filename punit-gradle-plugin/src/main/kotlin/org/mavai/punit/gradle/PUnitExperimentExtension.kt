package org.mavai.punit.gradle

import org.gradle.api.provider.Property

/**
 * DSL extension for configuring PUnit experiment tasks.
 *
 * ```kotlin
 * punit {
 *     specsDir.set("src/test/resources/punit/specs")
 *     explorationsDir.set(layout.buildDirectory.dir("punit/explorations").map { it.asFile.absolutePath })
 *     optimizationsDir.set(layout.buildDirectory.dir("punit/optimizations").map { it.asFile.absolutePath })
 *     configureTestTask.set(true)
 *     excludeTestSubjects.set(true)
 * }
 * ```
 *
 * The three default locations differ deliberately: MEASURE specs are inputs
 * to subsequent `@ProbabilisticTest` runs (kept under source, committed),
 * while EXPLORE and OPTIMIZE outputs are human-review artefacts (kept under
 * `build/`, not committed). Override any default as shown above.
 */
abstract class PUnitExperimentExtension {

    /** Output directory for MEASURE specs. Default: `src/test/resources/punit/specs` (tracked in source). */
    abstract val specsDir: Property<String>

    /** Output directory for EXPLORE results. Default: `<buildDir>/punit/explorations` (transient, not tracked). */
    abstract val explorationsDir: Property<String>

    /** Output directory for OPTIMIZE results. Default: `<buildDir>/punit/optimizations` (transient, not tracked). */
    abstract val optimizationsDir: Property<String>

    /** Whether to configure the `test` task (exclude experiment tags, forward properties, etc.). Default: `true` */
    abstract val configureTestTask: Property<Boolean>

    /** Whether to exclude testsubjects directories from test and experiment tasks. Default: `true` */
    abstract val excludeTestSubjects: Property<Boolean>
}
