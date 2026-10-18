pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("build-logic")
}

rootProject.name = "backend"

include("shared-kernel")
include("contexts:member:member-api", "contexts:member:member-core")
include("apps:api", "apps:batch")
