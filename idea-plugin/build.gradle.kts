import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("java")
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

val rootGroupId = providers.gradleProperty("root.group.id").get()
val inlayHintProviderId = providers.gradleProperty("idea.plugin.inlay-hint-provider-id").get()

group = rootGroupId
version = providers.gradleProperty("idea.plugin.version").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":annotations"))

    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("idea.plugin.platform-version").get())
        bundledPlugins("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("idea.plugin.id").get()
        name = providers.gradleProperty("idea.plugin.name").get()
        version = providers.gradleProperty("idea.plugin.version").get()
        description = providers.gradleProperty("idea.plugin.description").get()
        changeNotes = providers.gradleProperty("idea.plugin.change-notes").get()

        vendor {
            name = providers.gradleProperty("repo.author.name").get()
            email = providers.gradleProperty("repo.author.email").get()
            url = providers.gradleProperty("repo.author.github-url").get()
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("idea.plugin.since-build").get()
            untilBuild = providers.gradleProperty("idea.plugin.until-build").get()
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    publishing {
        token = findSecret("JB_MARKETPLACE_TOKEN", "jetbrains.marketplace-token")
        channels = providers.gradleProperty("idea.plugin.publish.channels")
            .get()
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
    }
}


tasks {
    withType<org.jetbrains.intellij.platform.gradle.tasks.InstrumentCodeTask> {
        enabled = false
    }

    processResources {
        filesMatching("plugin.xml") {
            expand("inlay_provider_id" to inlayHintProviderId)
        }
    }

    test {
        val annotationJarTask = project(":annotations").tasks.named<Jar>("jar")
        dependsOn(annotationJarTask)
        val jarFile = annotationJarTask.get().archiveFile.get().asFile
        systemProperty("annotation.jar.path", jarFile.absolutePath)
        doFirst { println("Using annotation JAR for tests: ${jarFile.absolutePath}") }
    }

    signPlugin {
        certificateChain.set(findSecret("JB_CERTIFICATE_CHAIN", "jetbrains.certificate-chain"))
        privateKey.set(findSecret("JB_PRIVATE_KEY", "jetbrains.private-key"))
        password.set(findSecret("JB_PASSWORD", "jetbrains.private-key-password"))
    }

    publishPlugin {
        doFirst {
            val isTokenPresent = intellijPlatform.publishing.token.isPresent
            val isSignPresent = signPlugin.get().privateKey.isPresent && signPlugin.get().certificateChain.isPresent
            @Suppress("unchecked_cast")
            val secretFileProvider = rootProject.extra["secretFileProvider"] as Provider<RegularFile>

            if (!isTokenPresent || !isSignPresent) {
                throw GradleException("""
                    ❌ publish abort: missing publish configuration
                    please check env or token file ${secretFileProvider.get().asFile.absolutePath}：
                    - marketplace (token)
                    - certificate chain (certificate)
                    - private key (private key)
                """.trimIndent())
            }
        }
    }
}

val generateIdeConfig = tasks.register("generateIdePluginConfig") {
    val outputDir = layout.buildDirectory.dir("generated/sources/ide-config/kotlin/main")
    outputs.dir(outputDir)
    doLast {
        val outputFile = file("${outputDir.get()}/IdePluginConfig.kt")
        outputFile.parentFile.mkdirs()
        outputFile.writeText("""
            package $rootGroupId.idea.plugin.captureupperarg

            /**
             * AUTO GENERATED, DO NOT EDIT 
             */
            internal object IdePluginConfig {
                const val INLAY_PROVIDER_ID = "$inlayHintProviderId"
                const val INLAY_PROVIDER_NAME = "${providers.gradleProperty("idea.plugin.inlay-hint-provider-name").get()}"
                const val INLAY_PROVIDER_TOOLTIPS = "${providers.gradleProperty("idea.plugin.inlay-hint-provider-tooltips").get()}"
            }
        """.trimIndent())
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateIdeConfig)
}

@Suppress("unchecked_cast")
fun findSecret(env: String, prop: String?): Provider<String> {
    val f = rootProject.extra["findSecret"]
            as (String, String?) -> Provider<String>
    return f(env, prop)
}