import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.37.0"
}

signing {
    useGpgCmd()
}

// Targeted `exports ... to org.mavai.punit.report / .sentinel` in
// module-info.java reference sibling modules that depend on this one,
// so they are not on the module path when punit-core compiles. javac
// emits a benign "module not found" warning per target. Suppress the
// `module` lint category for punit-core to keep the build output clean;
// downstream modules still resolve the exports at their own compile time.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-module")
}

tasks.test {
    // Test subjects under testsubjects/ are JUnit-driven inputs to the
    // TestKit-based integration tests; running them directly would trip
    // intentional sample-level failures.
    exclude("**/testsubjects/**")

    // MEASURE specs and baselines in punit-core's own tests are
    // re-generated as TestKit byproducts. Redirect both to build/ so the
    // source tree's committed src/test/resources/punit/specs/*.yaml
    // fixtures are not overwritten and emitted baselines do not pollute
    // src/test/resources/punit/baselines/.
    systemProperty(
        "punit.specs.outputDir",
        layout.buildDirectory.dir("punit/specs").get().asFile.absolutePath
    )
    systemProperty(
        "punit.baseline.dir",
        layout.buildDirectory.dir("punit/baselines").get().asFile.absolutePath
    )
}

dependencies {
    // JUnit Jupiter API — compileOnly because annotations reference JUnit
    // meta-annotations but punit-core does not transitively require JUnit
    // at runtime.
    compileOnly("org.junit.jupiter:junit-jupiter-api")

    // opentest4j — the de-facto contract for non-JUnit test-failure
    // signalling. PUnit.assertPasses() throws AssertionFailedError /
    // TestAbortedException to translate FAIL / INCONCLUSIVE verdicts;
    // opentest4j has no JUnit Platform engine dependency, so a sentinel-
    // deployed class can drive PUnit without dragging in JUnit Jupiter
    // or Platform.
    api("org.opentest4j:opentest4j:1.3.0")

    // Apache Commons Statistics — for statistical calculations (confidence intervals, distributions)
    implementation("org.apache.commons:commons-statistics-distribution:1.3")

    // SnakeYAML — for YAML serialization in spec generation
    implementation("org.yaml:snakeyaml:2.7")

    // Jackson — for JSON/CSV parsing in @InputSource
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.22.2")

    // Outcome — result types for contract postconditions
    api("org.mavai:outcome:1.0.0-alpha1")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:2.26.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.26.1")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    // JSON Schema validation (draft 2020-12) for the interchange
    // emitter conformance tests — test-only, never shipped.
    testImplementation("com.networknt:json-schema-validator:3.0.7")
    // networknt 3.x validates against Jackson 3 (the tools.jackson line),
    // distinct from the com.fasterxml Jackson 2 the statistics conformance
    // tests use above; both are test-only and coexist on the test classpath.
    testImplementation("tools.jackson.core:jackson-databind:3.2.2")
    testImplementation("org.apache.logging.log4j:log4j-core:2.26.1")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")
    // punit-report provides the default VerdictSink (XML) via ServiceLoader;
    // emission tests assert on the XML output reaching disk.
    testImplementation(project(":punit-report"))
}

// --- mavai-R conformance reference data ----------------------------------------
// Fetches the latest mavai-R release (resolved via the GitHub /releases/latest
// redirect, which costs no API-rate-limit quota), downloads its cases-<tag>.zip
// asset, caches it keyed by tag, and extracts into a directory on the test
// classpath so that /conformance/*.json resolves.

val conformanceResourcesDir = layout.buildDirectory.dir("generated/conformance")

val fetchConformanceData by tasks.registering {
    description = "Fetches the latest mavai-R conformance reference data release"
    group = "verification"

    outputs.dir(conformanceResourcesDir)
    outputs.upToDateWhen { false }

    // Local-directory override: -PconformanceCasesDir=/path/to/mavai-R/inst/cases
    // sources the fixture JSON from a local checkout instead of the latest
    // GitHub release. Intended for working against fixture content that is
    // merged upstream but not yet tagged/released; the default fetch-latest
    // behaviour is unchanged when the property is absent.
    val conformanceCasesDirOverride = providers.gradleProperty("conformanceCasesDir")

    doLast {
        val overrideDir = conformanceCasesDirOverride.orNull
        if (overrideDir != null) {
            val srcDir = file(overrideDir)
            require(srcDir.isDirectory) {
                "conformanceCasesDir does not resolve to a directory: $srcDir"
            }
            val destDir = conformanceResourcesDir.get().asFile.resolve("conformance")
            if (destDir.exists()) destDir.deleteRecursively()
            destDir.mkdirs()
            copy {
                from(srcDir) { include("*.json") }
                into(destDir)
            }
            logger.lifecycle("Using local mavai-R conformance fixtures: $srcDir")
            return@doLast
        }
        val latestUrl = URI(
            "https://github.com/mavai-org/mavai-R/releases/latest"
        ).toURL()
        val conn = latestUrl.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "HEAD"
        val status = conn.responseCode
        val location = conn.getHeaderField("Location")
        conn.disconnect()
        require(status in 301..308 && location != null) {
            "Expected redirect from $latestUrl, got $status (location=$location)"
        }
        val tag = location.substringAfterLast("/tag/")
        require(tag.matches(Regex("^v\\d+\\.\\d+\\.\\d+$"))) {
            "Unexpected tag format '$tag' in $location"
        }

        val cacheZip = layout.buildDirectory
            .file("conformance-cache/cases-$tag.zip").get().asFile
        if (!cacheZip.exists()) {
            cacheZip.parentFile.mkdirs()
            val assetUrl = URI(
                "https://github.com/mavai-org/mavai-R/releases/download/$tag/cases-$tag.zip"
            ).toURL()
            assetUrl.openStream().use { input ->
                cacheZip.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val destDir = conformanceResourcesDir.get().asFile.resolve("conformance")
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()
        copy {
            from(zipTree(cacheZip))
            into(destDir)
        }
        logger.lifecycle("Fetched mavai-R conformance fixtures: $tag")
    }
}

sourceSets.test {
    resources.srcDir(conformanceResourcesDir)
}

tasks.named<ProcessResources>("processTestResources") {
    dependsOn(fetchConformanceData)
}

// ---------------------------------------------------------------------------------

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "mavai.org",
            "Automatic-Module-Name" to "org.mavai.punit.core"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.mavai", "punit-core", version.toString())

    pom {
        name.set("PUnit Core")
        description.set("PUnit probabilistic testing — author-facing API (UseCase, Contract, Sampling, criteria), execution engine, statistics, baselines, runtime entry point. JUnit-free; sentinel-deployable directly.")
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
