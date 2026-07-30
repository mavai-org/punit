import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.37.0"
}

signing {
    useGpgCmd()
}

dependencies {
    api(project(":punit-core"))

    // The declarative reader's own YAML parser: snakeyaml-engine implements
    // YAML 1.2 (Core Schema), which the format family pins for the yaml
    // transform's projection — distinct from the snakeyaml 1.1 punit-core
    // uses for spec serialisation. Per the optional-module rule, this
    // dependency lives here and never in punit-core.
    implementation("org.snakeyaml:snakeyaml-engine:3.0.1")

    // The format-conformance gate drives the FULL services format, whose
    // `language-model` built-in type ships in punit-lm. Runtime-only and
    // test-only: punit-lm's main source depends on punit-decl's, so the
    // task graph stays acyclic — the gate simply runs with the family's
    // service types on the classpath, exactly as a consumer would.
    testRuntimeOnly(project(":punit-lm"))
}

// ---------------------------------------------------------------------------------
// Published declarative-format corpus (mavai-R releases): the conformance
// oracle for the contract and services formats. Fetched from the latest
// release on every build, mirroring punit-report's published-schema fetch;
// -PformatsDir=/path/to/mavai-R/inst/formats sources a local checkout
// instead (for working against merged-but-untagged corpus content).

val publishedFormatsDir = layout.buildDirectory.dir("published-formats")

val fetchPublishedFormats by tasks.registering {
    description = "Fetches the latest published mavai-R declarative-format corpus"
    group = "verification"

    outputs.dir(publishedFormatsDir)
    outputs.upToDateWhen { false }

    val formatsDirOverride = providers.gradleProperty("formatsDir")

    doLast {
        val destDir = publishedFormatsDir.get().asFile.resolve("formats")
        val overrideDir = formatsDirOverride.orNull
        if (overrideDir != null) {
            val srcDir = file(overrideDir)
            require(srcDir.resolve("manifest.yaml").isFile) {
                "formatsDir does not resolve to a formats bundle (no manifest.yaml): $srcDir"
            }
            if (destDir.exists()) destDir.deleteRecursively()
            destDir.mkdirs()
            copy {
                from(srcDir)
                into(destDir)
            }
            logger.lifecycle("Using local declarative-format corpus: $srcDir")
            return@doLast
        }
        val latestUrl = URI("https://github.com/mavai-org/mavai-R/releases/latest").toURL()
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
            .file("formats-cache/formats-$tag.zip").get().asFile
        if (!cacheZip.exists()) {
            cacheZip.parentFile.mkdirs()
            val assetUrl = URI(
                "https://github.com/mavai-org/mavai-R/releases/download/$tag/formats-$tag.zip"
            ).toURL()
            assetUrl.openStream().use { input ->
                cacheZip.outputStream().use { output -> input.copyTo(output) }
            }
        }

        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()
        copy {
            from(zipTree(cacheZip))
            into(destDir)
        }
        logger.lifecycle("Fetched published declarative-format corpus: $tag")
    }
}

tasks.test {
    dependsOn(fetchPublishedFormats)
    // The corpus binds format-layer outcomes under an empty user
    // environment plus each case's requires: registrations. punit's
    // endpoint-less-provider veto is an environment fail-fast, not a
    // format refusal, so the harness provisions a placeholder endpoint
    // exactly as it provisions the registrations; nothing connects.
    environment("MAVAI_LLM_ENDPOINT", "https://conformance.invalid/v1")
    environment("MAVAI_LLM_MODEL", "conformance-model")
    environment("MAVAI_LLM_API_KEY", "conformance-placeholder")
    systemProperty(
        "punit.formats.dir",
        publishedFormatsDir.get().asFile.resolve("formats").absolutePath
    )
}

// ---------------------------------------------------------------------------------

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit Decl",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "mavai.org",
            "Automatic-Module-Name" to "org.mavai.punit.decl"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.mavai", "punit-decl", version.toString())

    pom {
        name.set("PUnit Decl")
        description.set("Declarative authoring front-end for PUnit — the mavai contract and service-definition file reader")
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
