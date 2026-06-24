plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

group = "org.mavai"
version = property("punitVersion") as String

// Target Java 21, matching the framework modules. Without this the plugin
// compiles against whatever JDK the release machine defaults to and stamps
// that version into the published Gradle Module Metadata (`org.gradle.jvm.version`),
// which makes the plugin unresolvable for Java 21 consumers.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

signing {
    useGpgCmd()
}

if (project.hasProperty("signing.skip")) {
    tasks.matching { it.name.startsWith("sign") }.configureEach {
        enabled = false
    }
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("punit") {
            id = "org.mavai.punit"
            implementationClass = "org.mavai.punit.gradle.PUnitPlugin"
            displayName = "PUnit Gradle Plugin"
            description = "Configures test and experiment tasks for PUnit probabilistic testing"
        }
    }
}

// Publish the plugin (and its auto-generated `org.mavai.punit.gradle.plugin`
// marker) to Maven Central, so a standalone consumer can resolve the plugin
// without a sibling `../punit` checkout. The `java-gradle-plugin` plugin
// creates the marker publication; vanniktech signs and uploads both.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("org.mavai", "punit-gradle-plugin", version.toString())

    pom {
        name.set("PUnit Gradle Plugin")
        description.set("Configures test and experiment tasks for PUnit probabilistic testing")
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

// Functional test source set
val functionalTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val functionalTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val functionalTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    functionalTestImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    functionalTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Generate a Kotlin source file with the PUnit version baked in at compile time.
// This avoids classloader/resource-loading issues with Gradle's pluginManagement { includeBuild }.
val punitVersion = property("punitVersion") as String
val generateVersionFile = tasks.register("generateVersionFile") {
    val outputDir = layout.buildDirectory.dir("generated/punit-version")
    outputs.dir(outputDir)
    inputs.property("punitVersion", punitVersion)
    doLast {
        val dir = outputDir.get().asFile.resolve("org/mavai/punit/gradle")
        dir.mkdirs()
        dir.resolve("PUnitVersion.kt").writeText(
            """
            package org.mavai.punit.gradle

            internal object PUnitVersion {
                const val VERSION = "$punitVersion"
            }
            """.trimIndent() + "\n"
        )
    }
}
sourceSets.main.get().kotlin.srcDir(generateVersionFile.map { layout.buildDirectory.dir("generated/punit-version").get() })
tasks.named("compileKotlin") { dependsOn(generateVersionFile) }

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the functional tests for the plugin"
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()

    // Pass the punit root directory so functional tests can set up composite builds
    systemProperty("punitRootDir", rootProject.projectDir.parentFile.absolutePath)
}

tasks.check {
    dependsOn(functionalTestTask)
}
