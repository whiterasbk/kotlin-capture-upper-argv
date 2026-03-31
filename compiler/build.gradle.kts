plugins {
    kotlin("jvm")
    `maven-publish`
}

val rootGroupId = providers.gradleProperty("root.group.id").get()

group = rootGroupId
version = providers.gradleProperty("compiler.plugin.version").get()

dependencies {
    implementation(kotlin("compiler-embeddable"))
    implementation(project(":annotations"))
    testImplementation("dev.zacsweers.kctfork:core:0.12.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = providers.gradleProperty("compiler.plugin.artifact.id").get()
            version = providers.gradleProperty("compiler.plugin.version").get()

            pom {
                 name.set(providers.gradleProperty("compiler.plugin.name").get())
                 description.set(providers.gradleProperty("compiler.plugin.description").get())
            }
        }
    }
}

val compilerPackageSuffix = ".compiler.plugin.captureupperarg"

val generateCompilerConfig = tasks.register("generateCompilerConfig") {
    val outputDir = layout.buildDirectory.dir("generated/sources/compiler-config/kotlin/main")
    outputs.dir(outputDir)

    doLast {
        val outputFile = file("${outputDir.get()}/CompilerConfig.kt")
        outputFile.parentFile.mkdirs()

        outputFile.writeText("""
            package $rootGroupId$compilerPackageSuffix

            /**
             * AUTO GENERATED, DO NOT EDIT
             */
            internal object CompilerConfig {
                const val COMPILER_PLUGIN_ID = "${rootProject.extra["compiler-plugin-id-assign-for-gradle"]}"
            }
        """.trimIndent())
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateCompilerConfig)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        dependsOn(generateCompilerConfig)
    }

    test {
        // set to 2G or 4G, or might cause oom
        maxHeapSize = providers.gradleProperty("task.test.max-heap-size").get()
        maxParallelForks = providers.gradleProperty("task.test.max-parallel-forks").get().toInt()
    }
}