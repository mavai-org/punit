plugins {
    id("java-library")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.37.0"
    id("jacoco")
    id("org.mavai.punit")
    idea
}

// Configure IDEA to download sources and javadoc
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

signing {
    useGpgCmd()
}

allprojects {
    if (project.hasProperty("signing.skip")) {
        tasks.matching { it.name.startsWith("sign") }.configureEach {
            enabled = false
        }
    }
}

group = "org.mavai"
version = property("punitVersion") as String

// ═══════════════════════════════════════════════════════════════════════════
// Shared configuration for all subprojects
// ═══════════════════════════════════════════════════════════════════════════

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Compile with -parameters flag to preserve method parameter names at runtime
    // This is required for use case argument injection
    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
    // JUnit 5 Jupiter API - needed at compile time for the extension
    // Using 'api' so consumers get transitive access to JUnit types
    // Version 5.13.3 includes failureThreshold for @RepeatedTest
    api(platform("org.junit:junit-bom:5.14.4"))
    api("org.junit.jupiter:junit-jupiter-api")

    // Apache Commons Statistics - for statistical calculations (confidence intervals, distributions)
    implementation("org.apache.commons:commons-statistics-distribution:1.3")

    // SnakeYAML - for YAML serialization in spec generation
    implementation("org.yaml:snakeyaml:2.7")

    // Jackson - for JSON/CSV parsing in @InputSource
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.22.2")

    // Outcome - result types for contract postconditions
    // Resolved locally via composite build (settings.gradle.kts), or from Maven Central on CI
    api("org.mavai:outcome:1.0.0-alpha1")

    implementation("org.apache.logging.log4j:log4j-api:2.26.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.26.1")
    // Bridge SLF4J to Log4j2 (some dependencies use SLF4J)
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-testkit")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.apache.logging.log4j:log4j-core:2.26.1")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }

    tasks.javadoc {
        options {
            (this as StandardJavadocDocletOptions).apply {
                encoding = "UTF-8"
                charSet = "UTF-8"
                addStringOption("Xdoclint:none", "-quiet")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Root meta-artifact: depends on punit-core + punit-junit5 transitively
// ═══════════════════════════════════════════════════════════════════════════

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    api(project(":punit-core"))
    api(project(":punit-report"))
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// Disable punitVerify for the framework project itself — test subjects
// intentionally produce failing verdicts as part of the TestKit test suite
allprojects {
    tasks.matching { it.name == "punitVerify" }.configureEach {
        enabled = false
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUNIT - Probabilistic Unit Testing Framework",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "mavai.org",
            "Automatic-Module-Name" to "org.mavai.punit"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.mavai", "punit", version.toString())

    pom {
        name.set("PUnit")
        description.set("Probabilistic Unit Testing Framework for JUnit 5 - Test non-deterministic systems with statistical pass/fail thresholds")
        url.set("https://github.com/mavai-org/punit")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("mikemannion")
                name.set("Michael Franz Mannion")
                email.set("michaelmannion@me.com")
            }
        }

        scm {
            url.set("https://github.com/mavai-org/punit")
            connection.set("scm:git:git://github.com/mavai-org/punit.git")
            developerConnection.set("scm:git:ssh://github.com/mavai-org/punit.git")
        }
    }
}

// Convenience task to build and publish locally
tasks.register("publishLocal") {
    description = "Publishes to the local Maven repository"
    group = "publishing"
    dependsOn(tasks.publishToMavenLocal)
}


// ========== Release Lifecycle ==========

fun runCommand(vararg args: String) {
    val process = ProcessBuilder(*args)
        .directory(projectDir)
        .inheritIO()
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("Command failed (exit $exitCode): ${args.joinToString(" ")}")
    }
}

fun runCommandAndCapture(vararg args: String): String {
    val process = ProcessBuilder(*args)
        .directory(projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output.trim()
}

// Extracts the CHANGELOG section for a version: everything between the
// "## [$ver]" heading and the next "## [" heading, trimmed. Empty if absent.
fun extractChangelogSection(changelogText: String, ver: String): String {
    val lines = changelogText.lines()
    val startIdx = lines.indexOfFirst { it.startsWith("## [$ver]") }
    if (startIdx < 0) return ""
    val rest = lines.drop(startIdx + 1)
    val endRel = rest.indexOfFirst { it.startsWith("## [") }
    val body = if (endRel < 0) rest else rest.take(endRel)
    return body.joinToString("\n").trim()
}

// Derives the next SNAPSHOT version after a release.
// - "0.6.0"        -> "0.6.1-SNAPSHOT"
// - "0.7.0-alpha"  -> "0.7.0-alpha2-SNAPSHOT"
// - "0.7.0-alpha2" -> "0.7.0-alpha3-SNAPSHOT"
// - "0.7.0-rc1"    -> "0.7.0-rc2-SNAPSHOT"
fun nextSnapshotVersion(ver: String): String {
    val parts = ver.split(".")
    if (parts.size != 3) {
        throw GradleException("Cannot derive next SNAPSHOT from $ver: expected MAJOR.MINOR.PATCH form")
    }
    val major = parts[0]
    val minor = parts[1]
    val patchComponent = parts[2]

    if (patchComponent.all { it.isDigit() }) {
        val nextPatch = patchComponent.toInt() + 1
        return "$major.$minor.$nextPatch-SNAPSHOT"
    }

    val dashIdx = patchComponent.indexOf('-')
    if (dashIdx < 0) {
        throw GradleException("Cannot derive next SNAPSHOT from $ver: unsupported patch component '$patchComponent'")
    }
    val patchNumber = patchComponent.substring(0, dashIdx)
    val qualifier = patchComponent.substring(dashIdx + 1)
    val qualifierName = qualifier.takeWhile { it.isLetter() }
    val qualifierNum = qualifier.drop(qualifierName.length)
    if (qualifierName.isEmpty()) {
        throw GradleException("Cannot derive next SNAPSHOT from $ver: missing qualifier name in '$patchComponent'")
    }
    val nextQualifierNum = if (qualifierNum.isEmpty()) 2 else qualifierNum.toInt() + 1
    return "$major.$minor.$patchNumber-$qualifierName$nextQualifierNum-SNAPSHOT"
}

tasks.register("release") {
    description = "Validates, publishes to Maven Central, tags the release, creates the GitHub release, and bumps to next SNAPSHOT"
    group = "publishing"

    doLast {
        val ver = project.property("punitVersion") as String

        // 1. Validate not a SNAPSHOT
        if (ver.endsWith("-SNAPSHOT")) {
            throw GradleException(
                "Cannot release a SNAPSHOT version ($ver). " +
                "Set the release version in gradle.properties first, e.g. punitVersion=0.6.0"
            )
        }

        // 2. Validate CHANGELOG.md has an entry for this version
        val changelog = file("CHANGELOG.md")
        if (!changelog.exists()) {
            throw GradleException("CHANGELOG.md not found. Create it before releasing.")
        }
        val changelogText = changelog.readText()
        if (!changelogText.contains("## [$ver]")) {
            throw GradleException(
                "CHANGELOG.md has no entry for version $ver. " +
                "Add a '## [$ver]' section before releasing."
            )
        }

        // 3. Validate clean git state
        val statusOutput = runCommandAndCapture("git", "status", "--porcelain")
        if (statusOutput.isNotEmpty()) {
            throw GradleException(
                "Cannot release with uncommitted changes. Commit or stash them first.\n$statusOutput"
            )
        }

        // 4. Create annotated tag locally (before publish, so a successful publish always has a tag)
        val tag = "v$ver"
        logger.lifecycle("Creating tag $tag...")
        runCommand("git", "tag", "-a", tag, "-m", "Release $ver")

        // 5. Publish to Maven Central (delete local tag if this fails)
        logger.lifecycle("Publishing $ver to Maven Central...")
        try {
            runCommand("./gradlew", "publishAndReleaseToMavenCentral")
            // The Gradle plugin lives in a standalone includeBuild, so the root
            // publish above never reaches it. Publish it explicitly so the
            // org.mavai.punit plugin and its marker ship with every release —
            // otherwise a standalone consumer cannot resolve the plugin.
            logger.lifecycle("Publishing punit-gradle-plugin $ver to Maven Central...")
            runCommand("./gradlew", ":punit-gradle-plugin:publishAndReleaseToMavenCentral")
        } catch (e: Exception) {
            logger.lifecycle("Publishing failed — removing local tag $tag")
            runCommand("git", "tag", "-d", tag)
            throw e
        }

        // 6. Push tag (artifact is published, so the tag must reach the remote)
        logger.lifecycle("Pushing tag $tag to origin...")
        runCommand("git", "push", "origin", tag)

        // 6b. Create the GitHub release for the tag, with notes from CHANGELOG.
        // Soft-fails: the artifact is already published and the tag pushed, so a
        // gh hiccup must not strand the release before the SNAPSHOT bump — warn
        // with the manual command and continue.
        val isPrerelease = ver.contains("-")
        val notesFile = layout.buildDirectory.file("release-notes-$ver.md").get().asFile
        notesFile.parentFile.mkdirs()
        notesFile.writeText(extractChangelogSection(changelogText, ver))
        val ghArgs = mutableListOf(
            "gh", "release", "create", tag,
            "--title", tag,
            "--notes-file", notesFile.absolutePath,
            "--verify-tag"
        )
        ghArgs += if (isPrerelease) "--prerelease" else "--latest"
        logger.lifecycle("Creating GitHub release $tag...")
        try {
            runCommand(*ghArgs.toTypedArray())
        } catch (e: Exception) {
            logger.warn(
                "GitHub release creation failed for $tag — create it manually:\n" +
                "  " + ghArgs.joinToString(" ") + "\n" +
                "Continuing with the version bump."
            )
        }

        // 7. Bump to next SNAPSHOT
        val nextVersion = nextSnapshotVersion(ver)
        logger.lifecycle("Bumping version to $nextVersion...")

        val rootProps = file("gradle.properties")
        rootProps.writeText(rootProps.readText().replace("punitVersion=$ver", "punitVersion=$nextVersion"))

        val pluginProps = file("punit-gradle-plugin/gradle.properties")
        pluginProps.writeText(pluginProps.readText().replace("punitVersion=$ver", "punitVersion=$nextVersion"))

        runCommand("git", "add", "gradle.properties", "punit-gradle-plugin/gradle.properties")
        runCommand("git", "commit", "-m", "Bump version to $nextVersion")
        runCommand("git", "push")

        logger.lifecycle("Release $ver complete. Version bumped to $nextVersion.")
    }
}

tasks.register("tagRelease") {
    description = "Creates and pushes a release tag for a given version (e.g. -PreleaseVersion=0.1.0)"
    group = "publishing"

    doLast {
        val ver = project.findProperty("releaseVersion") as String?
            ?: throw GradleException("Specify -PreleaseVersion=<version>, e.g. ./gradlew tagRelease -PreleaseVersion=0.1.0")

        val tag = "v$ver"
        val commitish = (project.findProperty("commitish") as String?) ?: "HEAD"

        logger.lifecycle("Creating tag $tag at $commitish...")
        runCommand("git", "tag", "-a", tag, commitish, "-m", "Release $ver")

        logger.lifecycle("Pushing tag $tag to origin...")
        runCommand("git", "push", "origin", tag)

        logger.lifecycle("Tag $tag created and pushed.")
    }
}

// ========== Code Coverage (JaCoCo) ==========

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
    }
}
