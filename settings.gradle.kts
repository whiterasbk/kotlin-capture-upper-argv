
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

rootProject.name = "capture-upper-argument"

include(
    "annotations",
    "compiler",
    "gradle-plugin",
    "idea-plugin",
)