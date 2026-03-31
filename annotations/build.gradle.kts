
plugins {
    kotlin("jvm")
    `maven-publish`
    signing
}

group = providers.gradleProperty("root.group.id")
version = providers.gradleProperty("compiler.plugin.version").get()

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = providers.gradleProperty("compiler.plugin.annotation.artifact.id").get()
            version = providers.gradleProperty("compiler.plugin.version").get()

            pom {
                name.set(providers.gradleProperty("compiler.plugin.annotation.name").get())
                description.set(providers.gradleProperty("compiler.plugin.annotation.description").get())
            }
        }
    }
}