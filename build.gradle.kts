import java.util.Properties
import org.yaml.snakeyaml.Yaml
import java.util.Base64

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.yaml:snakeyaml:2.2")
    }
}

plugins {
    kotlin("jvm") version "2.2.20" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

val rootGroupId = providers.gradleProperty("root.group.id").get()
val rootVersion = providers.gradleProperty("root.version").get()
val repoUrl = providers.gradleProperty("repo.url").get()

val secretFileProvider: Provider<RegularFile> = provider {
    listOf("yml", "yaml", "properties")
        .map { layout.projectDirectory.file("secret.$it") }
        .firstOrNull { it.asFile.exists() }
}

val secretsMapProvider: Provider<Map<String, Any>> = secretFileProvider.map { regFile ->
    val file = regFile.asFile
    when (file.extension.lowercase()) {
        "yml", "yaml" -> {
            val loader = Yaml()
            file.inputStream().use { loader.load<Map<String, Any>>(it) } ?: emptyMap()
        }
        "properties" -> {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.entries.associate { it.key.toString() to it.value.toString() }
        }
        else -> emptyMap()
    }
}.orElse(provider { emptyMap() })
val isMavenProjectAutoPublish: Provider<Boolean> = providers.environmentVariable("AUTO_PUBLISH_MAVEN_REPO")
    .map { it.lowercase() in listOf("true", "1", "yes") }

group = rootGroupId
version = rootVersion
rootProject.extra["compiler-plugin-id-assign-for-gradle"] = "$rootGroupId.${providers.gradleProperty("compiler.plugin.artifact.id").get()}"

allprojects {
    repositories { mavenCentral() }

    extra["secretFileProvider"] = secretFileProvider
    extra["findSecret"] = { envName: String, propName: String? -> findSecret(envName, propName) }
}

nexusPublishing {
    repositories {
        sonatype {
            packageGroup.set(providers.gradleProperty("repo.namespace").get())
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

            username.set(findSecret("SONATYPE_USERNAME", "sonatype.username"))
            password.set(findSecret("SONATYPE_PASSWORD", "sonatype.password"))

            useStaging.set(isMavenProjectAutoPublish.getOrElse(false))
        }
    }
}

subprojects {
    pluginManager.withPlugin("maven-publish") {
        pluginManager.withPlugin("java") {
            configure<JavaPluginExtension> {
                withSourcesJar()
                withJavadocJar()
            }
        }

        tasks.register("publishToSonatypeAndCommit") {
            group = "publishing"
            description = "publish current project and release or not according to AUTO_PUBLISH_MAVEN_REPO"
            dependsOn(tasks.named("publishToSonatype"))
            finalizedBy(rootProject.tasks.named(getFinalizeTaskName()))
        }

        configure<PublishingExtension> {

            publications.withType<MavenPublication> {
                groupId = rootGroupId
                version = rootVersion

                pom {
                    url.set(repoUrl)
                    licenses {
                        license {
                            name.set("GNU General Public License v3.0")
                            url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set(providers.gradleProperty("repo.author.id").get())
                            url.set(providers.gradleProperty("repo.author.github-url").get())
                            name.set(providers.gradleProperty("repo.author.name").get())
                            email.set(providers.gradleProperty("repo.author.email").get())
                        }
                    }
                    scm {
                        connection.set("scm:git:${repoUrl.removePrefix("https://")}.git")
                        developerConnection.set("scm:git:ssh://${repoUrl.removePrefix("https://")}.git")
                        url.set(repoUrl)
                    }
                }
            }
        }

        pluginManager.withPlugin("signing") {
            val rawKey = findSecret("GPG_PRIVATE_KEY", "gpg.privateKey")
            val password = findSecret("GPG_PASSWORD", "gpg.password")

            if (rawKey.isPresent) {
                configure<SigningExtension> {
                    val key = rawKey.get()
                    if (key != null) {
                        val finalKey = if (key.trim().startsWith("-----BEGIN")) {
                            key
                        } else {
                            String(Base64.getDecoder().decode(key.trim()))
                        }

                        useInMemoryPgpKeys(finalKey, password.getOrNull())
                        val publishing = extensions.getByType<PublishingExtension>()
                        sign(publishing.publications)
                    }
                }
                tasks.withType<Sign>().configureEach {
                    onlyIf { rawKey.isPresent }
                }
            }
        }
    }
}

tasks.register("publishAllToSonatypeAndCommit") {
    group = "publishing"
    description = "publish all sub projects and release or not according to AUTO_PUBLISH_MAVEN_REPO"

    val publishTasks = subprojects.mapNotNull { sub ->
        if (sub.pluginManager.hasPlugin("maven-publish")) ":${sub.name}:publishToSonatype" else null
    }
    dependsOn(publishTasks)
    finalizedBy(tasks.named(getFinalizeTaskName()))
}

fun getFinalizeTaskName(): String {
    return if (isMavenProjectAutoPublish.getOrElse(false))
        "closeAndReleaseSonatypeStagingRepository"
    else
        "closeSonatypeStagingRepository"
}

private fun findDeepValue(map: Map<String, Any>, path: String): Any? {
    val keys = path.split(".")
    var current: Any? = map

    for (key in keys) {
        if (current is Map<*, *>) {
            current = current[key]
        } else {
            return null
        }
    }

    return current
}

@Suppress("UnstableApiUsage")
fun Project.findSecret(envName: String, propName: String? = null): Provider<String> {
    val effectivePropName = propName ?: envName
    val envProvider = providers.environmentVariable(envName)
    val fileProvider = secretsMapProvider.flatMap { map ->
        providers.provider {
            findDeepValue(map, effectivePropName)?.toString()?.trim()
        }
    }
    return envProvider
        .orElse(fileProvider)
        .filter { it.isNotBlank() }
}