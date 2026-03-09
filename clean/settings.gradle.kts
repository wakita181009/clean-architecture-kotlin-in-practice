pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "clean"

include(
    "domain",
    "application",
    "infrastructure",
    "presentation",
    "framework",
    "detekt-rules",
)
