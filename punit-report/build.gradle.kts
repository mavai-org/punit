plugins {
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

signing {
    useGpgCmd()
}

dependencies {
    api(project(":punit-core"))

    testImplementation("org.xmlunit:xmlunit-core:2.11.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.4")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "PUnit Report",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "javai.org",
            "Automatic-Module-Name" to "org.javai.punit.report"
        )
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.javai", "punit-report", version.toString())

    pom {
        name.set("PUnit Report")
        description.set("XML report generation for PUnit probabilistic test verdicts")
        url.set("https://github.com/javai-org/punit")

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
            url.set("https://github.com/javai-org/punit")
            connection.set("scm:git:git://github.com/javai-org/punit.git")
            developerConnection.set("scm:git:ssh://github.com/javai-org/punit.git")
        }
    }
}
