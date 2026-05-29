pluginManagement {
    includeBuild("punit-gradle-plugin")
}

rootProject.name = "punit"

include("punit-core", "punit-sentinel", "punit-report")

// RELOCATION-BRANCH ONLY: the sibling ../outcome has moved to org.mavai, so the
// local composite would break compilation of this legacy org.javai source tree.
// Resolve org.javai:outcome:0.3.0 from Maven Central instead (immutable, still present).
// Do NOT carry this change to main.
