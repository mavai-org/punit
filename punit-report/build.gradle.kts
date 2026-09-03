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

    // Exploration comparison report reads punit-spec-1 YAML. punit-core
    // depends on snakeyaml as `implementation`, so it is not exposed here.
    implementation("org.yaml:snakeyaml:2.6")

    testImplementation("org.xmlunit:xmlunit-core:2.13.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
}

// --- published verdict interchange schemas ---------------------------------------
// The verdict XSDs embedded in this module's resources are vendored snapshots
// of the published family schemas (mavai-R's `schema/verdict-*.xsd`, shipped
// in the `interchange-<tag>.zip` release asset), synced per release. This task
// fetches the latest published set (same latest-release resolution and cache
// as punit-core's conformance-data fetch) onto the test classpath so the
// snapshot-sync test can assert the embedded copies are byte-identical to the
// published ones — a drifted snapshot fails the build instead of shipping.

val publishedInterchangeDir = layout.buildDirectory.dir("generated/interchange")

val fetchPublishedInterchangeSchemas by tasks.registering {
    description = "Fetches the latest published mavai-R interchange schemas"
    group = "verification"

    outputs.dir(publishedInterchangeDir)
    outputs.upToDateWhen { false }

    // Local-directory override: -PinterchangeSchemaDir=/path/to/mavai-R/schema
    // sources the published schemas from a local checkout instead of the
    // latest GitHub release (for working against merged-but-untagged content).
    val interchangeSchemaDirOverride = providers.gradleProperty("interchangeSchemaDir")

    doLast {
        val destDir = publishedInterchangeDir.get().asFile.resolve("published-interchange")
        val overrideDir = interchangeSchemaDirOverride.orNull
        if (overrideDir != null) {
            val srcDir = file(overrideDir)
            require(srcDir.isDirectory) {
                "interchangeSchemaDir does not resolve to a directory: $srcDir"
            }
            if (destDir.exists()) destDir.deleteRecursively()
            destDir.mkdirs()
            copy {
                from(srcDir) { include("verdict-*.xsd") }
                into(destDir)
            }
            logger.lifecycle("Using local published interchange schemas: $srcDir")
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
            .file("interchange-cache/interchange-$tag.zip").get().asFile
        if (!cacheZip.exists()) {
            cacheZip.parentFile.mkdirs()
            val assetUrl = URI(
                "https://github.com/mavai-org/mavai-R/releases/download/$tag/interchange-$tag.zip"
            ).toURL()
            assetUrl.openStream().use { input ->
                cacheZip.outputStream().use { output -> input.copyTo(output) }
            }
        }

        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()
        copy {
            from(zipTree(cacheZip)) { include("verdict-*.xsd") }
            into(destDir)
        }
        logger.lifecycle("Fetched published interchange schemas: $tag")
    }
}

sourceSets.test {
    resources.srcDir(publishedInterchangeDir)
}

tasks.named<ProcessResources>("processTestResources") {
    dependsOn(fetchPublishedInterchangeSchemas)
}

// ---------------------------------------------------------------------------------

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit Report",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "mavai.org",
            "Automatic-Module-Name" to "org.mavai.punit.report"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.mavai", "punit-report", version.toString())

    pom {
        name.set("PUnit Report")
        description.set("XML report generation for PUnit probabilistic test verdicts")
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
