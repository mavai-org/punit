package org.mavai.punit.gradle

import java.io.File
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Locates the `mavai` renderer `punitReport` runs.
 *
 * PUnit renders no HTML; the family's shared `mavai` executable does, from
 * the artefacts a run emits. The renderer is published to Maven Central as
 * `org.mavai:mavai:<version>:<classifier>@exe`, one native executable per
 * platform, so a build resolves it the way it resolves any dependency and
 * the developer installs nothing. This object holds the two decisions that
 * resolution needs — which classifier the host is, and what the platform
 * calls an executable — and the precedence between the three places a
 * renderer can come from:
 *
 * 1. `MAVAI_BIN` in the environment names an executable to use instead of
 *    any other. The escape hatch for a local build of the renderer; a value
 *    that does not resolve is an error, not a fall-through, because the
 *    caller asked for a specific renderer and would otherwise get another.
 * 2. The artefact resolved for the host platform, copied under `build/mavai/`
 *    with the platform's executable name.
 * 3. `mavai` on `PATH`, the long-standing route for a platform no artefact
 *    is published for.
 *
 * Nothing here is fetched at report time: Gradle resolves the artefact into
 * its cache as it does every dependency, and the copy is a build output.
 */
object MavaiRenderer {

    const val GROUP = "org.mavai"
    const val ARTIFACT = "mavai"
    const val CONFIGURATION = "mavaiRenderer"
    const val OVERRIDE = "MAVAI_BIN"

    /**
     * The Maven classifier for the host, in the vocabulary `os-maven-plugin`
     * derives (`${os.detected.classifier}`), or `null` where no artefact is
     * published for the host. Mirrors the target set the renderer's release
     * workflow builds: Linux and macOS on x86-64 and arm64, Windows on x86-64.
     */
    fun classifier(
        osName: String = System.getProperty("os.name"),
        osArch: String = System.getProperty("os.arch")
    ): String? {
        val os = osName.lowercase(Locale.ROOT)
        val platform = when {
            os.contains("linux") -> "linux"
            os.contains("mac") || os.contains("darwin") -> "osx"
            os.contains("windows") -> "windows"
            else -> return null
        }
        val arch = when (osArch.lowercase(Locale.ROOT)) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch_64"
            else -> return null
        }
        if (platform == "windows" && arch != "x86_64") return null
        return "$platform-$arch"
    }

    /** What the platform calls the renderer once it is a file on disk. */
    fun executableName(osName: String = System.getProperty("os.name")): String =
        if (osName.lowercase(Locale.ROOT).contains("windows")) "$ARTIFACT.exe" else ARTIFACT

    /** The dependency notation for one version on the host's platform, or `null` off-platform. */
    fun notation(version: String, classifier: String? = classifier()): String? =
        classifier?.let { "$GROUP:$ARTIFACT:$version:$it@exe" }

    /**
     * Registers the configuration the renderer resolves through. The
     * dependency is added lazily from the extension's version so a build
     * that sets `punit { mavaiVersion }` is honoured, and only when the host
     * has a classifier: an unresolvable dependency on an unsupported platform
     * would fail every build that merely lists tasks.
     */
    fun registerConfiguration(project: Project, version: () -> String): Configuration =
        project.configurations.create(CONFIGURATION) {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            description = "The mavai renderer executable for the host platform"
            defaultDependencies {
                notation(version())?.let { add(project.dependencies.create(it)) }
            }
        }

    /** Where a resolved renderer is placed: `build/mavai/<executable name>`. */
    fun resolvedLocation(project: Project): File =
        project.layout.buildDirectory.dir("mavai").get().asFile.resolve(executableName())

    /**
     * The renderer to run, or `null` when there is none. See the class note
     * for the order. [configuration] is resolved here, not earlier, so a build
     * with no report to render never touches the network for one.
     */
    fun locate(
        project: Project,
        configuration: Configuration,
        environment: Map<String, String> = System.getenv()
    ): File? {
        environment[OVERRIDE]?.takeIf { it.isNotBlank() }?.let { override ->
            val file = File(override)
            require(file.isFile && file.canExecute()) {
                "$OVERRIDE names $override, which is not an executable file"
            }
            return file
        }
        resolved(project, configuration)?.let { return it }
        return onPath(environment["PATH"].orEmpty())
    }

    private fun resolved(project: Project, configuration: Configuration): File? {
        if (classifier() == null) return null
        // A resolution failure (offline, or a version Central does not carry)
        // is said and then stepped past to PATH: a developer with the renderer
        // installed should not lose the page to a network the build did not need.
        val artefact = try {
            configuration.resolve().firstOrNull()
        } catch (e: org.gradle.api.artifacts.ResolveException) {
            project.logger.warn("mavai: could not resolve ${configuration.dependencies.firstOrNull() ?: notation("?")}: ${e.message?.lineSequence()?.firstOrNull()}")
            null
        } ?: return null
        val target = resolvedLocation(project)
        if (!target.isFile || target.length() != artefact.length() || target.lastModified() < artefact.lastModified()) {
            target.parentFile.mkdirs()
            artefact.copyTo(target, overwrite = true)
            target.setExecutable(true, false)
        }
        return target
    }

    private fun onPath(path: String): File? {
        val name = executableName()
        return path.split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    /** The one lifecycle line `punitReport` prints when no renderer can be found. */
    fun missingMessage(): String {
        val platform = classifier()
            ?: "${System.getProperty("os.name")}/${System.getProperty("os.arch")}, for which no renderer is published"
        return "no mavai renderer for $platform: the report is not rendered. " +
            "Set $OVERRIDE to a mavai executable, or put mavai on PATH " +
            "(https://github.com/mavai-org/mavai/releases)."
    }
}
