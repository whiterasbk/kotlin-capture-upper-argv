plugins {
    `kotlin-dsl`
    signing
    id("com.gradle.plugin-publish") version "2.1.1"
}

val rootGroupId = providers.gradleProperty("root.group.id").get()
val gradlePluginVersion = providers.gradleProperty("gradle.plugin.version").get()
val repoUrl = providers.gradleProperty("repo.url")
val compilerPluginAnnotationArtifactId = providers.gradleProperty("compiler.plugin.annotation.artifact.id").get()
val compilerPluginVersion = providers.gradleProperty("compiler.plugin.version").get()

System.setProperty("gradle.publish.key", findSecret("GRADLE_PUBLISH_KEY", "gradle.publish.key").get())
System.setProperty("gradle.publish.secret", findSecret("GRADLE_PUBLISH_KEY", "gradle.publish.secret").get())

group = rootGroupId
version = gradlePluginVersion
val packageSuffix = ".compiler.plugin.captureupperarg.gradle"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    compileOnly(project(":compiler"))
    implementation(project(":annotations"))
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("gradle-plugin-api"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("gradle-plugin"))
    testImplementation(gradleApi())
}

val generatePluginConfig = tasks.register("generatePluginConfig") {
    val outputDir = layout.buildDirectory.dir("generated/sources/plugin-config/kotlin/main")

    outputs.dir(outputDir)

    doLast {
        val outputFile = file("${outputDir.get()}/GradleConfig.kt")
        outputFile.parentFile.mkdirs()

        outputFile.writeText(
            """
            package $rootGroupId$packageSuffix

            /**
             * AUTO GENERATED, DO NOT EDIT 
             */
            internal object GradleConfig {
                const val GROUP_ID = "$rootGroupId"
                const val COMPILER_ARTIFACT_ID = "${providers.gradleProperty("compiler.plugin.artifact.id").get()}"
                const val ANNOTATION_ARTIFACT_ID = "$compilerPluginAnnotationArtifactId"
                const val COMPILER_VERSION = "$compilerPluginVersion"
                const val COMPILER_PLUGIN_ID = "${rootProject.extra["compiler-plugin-id-assign-for-gradle"]}" // MAYBE JUST FOR GRADLE
            }
        """.trimIndent()
        )
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generatePluginConfig)
}

tasks {
    val publishAnnotationsToLocal = project(":annotations").tasks.named("publishToMavenLocal")
    val publishCompilerToLocal = project(":compiler").tasks.named("publishToMavenLocal")

    test {
        dependsOn(publishAnnotationsToLocal)
        dependsOn(publishCompilerToLocal)

        systemProperty("gradlePluginId", providers.gradleProperty("gradle.plugin.id").get())
        systemProperty("gradlePluginVersion", gradlePluginVersion)
        systemProperty("extensionName", "captureUpperArgv")
        systemProperty("annotationDependency", "$rootGroupId:$compilerPluginAnnotationArtifactId:$compilerPluginVersion")

        useJUnitPlatform()
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        dependsOn(generatePluginConfig)
    }
}

gradlePlugin {
    website = repoUrl
    vcsUrl = repoUrl

    plugins {
        create(providers.gradleProperty("gradle.plugin.name").get()) {
            id = providers.gradleProperty("gradle.plugin.id").get()
            displayName = providers.gradleProperty("gradle.plugin.display.name").get()
            description = providers.gradleProperty("gradle.plugin.description").get()
            tags = providers.gradleProperty("gradle.plugin.tags").getOrElse("").split(",").filter { it.isNotBlank() }
            implementationClass = "$rootGroupId$packageSuffix.CaptureUpperArgGradlePlugin"
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            artifactId = providers.gradleProperty("gradle.plugin.artifact.id").get()
        }
    }
}

@Suppress("unchecked_cast")
fun findSecret(env: String, prop: String?): Provider<String> {
    val f = rootProject.extra["findSecret"]
            as (String, String?) -> Provider<String>
    return f(env, prop)
}