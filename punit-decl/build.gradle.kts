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
    implementation("org.snakeyaml:snakeyaml-engine:2.10")
}

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
