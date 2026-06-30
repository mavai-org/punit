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

    testImplementation("org.xmlunit:xmlunit-core:2.12.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")
}

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
