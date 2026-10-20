pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("build-logic")
}

rootProject.name = "backend"

include("shared-kernel")
include("platform:architecture-rules", "platform:messaging")
include("contexts:member:member-api", "contexts:member:member-core")
include("contexts:publishing:publishing-api", "contexts:publishing:publishing-core")
include("apps:api", "apps:batch", "apps:migrator")
