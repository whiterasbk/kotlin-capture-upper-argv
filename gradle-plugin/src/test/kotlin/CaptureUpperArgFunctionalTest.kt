package ink.iowoi.kotlin.compiler.plugin.captureupperarg.gradle.test

import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotation.CaptureUpperArg
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CaptureUpperArgFunctionalTest {

    lateinit var testProjectDir: File

    private val pluginId = System.getProperty("gradlePluginId")
    private val pluginVersion = System.getProperty("gradlePluginVersion")
    private val extensionName = System.getProperty("extensionName")
    private val annotationDependency = System.getProperty("annotationDependency")

    @BeforeTest
    fun setup() {
        testProjectDir = Files.createTempDirectory("gradle-test").toFile()
    }

    @AfterTest
    fun teardown() {
         testProjectDir.deleteRecursively()
    }

    @Test
    fun `can compile project with plugin applied`() = baseTestFormCode(true)

    @Test
    fun `can compile project with plugin applied without passing annotation dependency`() = baseTestFormCode(false)

    fun baseTestFormCode(passingDependency: Boolean) {
        println("using test dir at: $testProjectDir")

        testProjectDir.resolve("settings.gradle.kts").writeText("""
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "test-functional"
        """.trimIndent())

        testProjectDir.resolve("build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm") version "2.2.20"
                id("$pluginId") version "$pluginVersion"
                application
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }
            
            ${
                if (passingDependency)
                    "dependencies {\n" + 
                            "    implementation(\"$annotationDependency\")" + "\n}"
                else 
                    ""
            }
            
            application {
                mainClass.set("MainKt")
            }

            $extensionName {
                enabled = true
            }
        """.trimIndent())

        // you might need this
        // testProjectDir.resolve("gradle.properties").writeText("""
        //     systemProp.http.proxyHost=127.0.0.1
        //     systemProp.http.proxyPort=7897
        //     systemProp.https.proxyHost=127.0.0.1
        //     systemProp.https.proxyPort=7897
        //     systemProp.http.nonProxyHosts=localhost|127.0.0.1
        // """.trimIndent())

        val srcDir = testProjectDir.resolve("src/main/kotlin")
        srcDir.mkdirs()
        srcDir.resolve("Main.kt").writeText("""
            import ${CaptureUpperArg::class.qualifiedName}
            
            fun main() {
                service("Jimmy", 15, 114514L)
            }
            
            fun service(name: String, age: Int, id: Long) {
                capture()
            }
            
            fun capture(@${CaptureUpperArg::class.simpleName} argv: Map<String, Any> = emptyMap()) {
                println(argv.keys.sorted().joinToString())
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("run", "--stacktrace", )
            .withPluginClasspath()
            .withGradleVersion("8.10")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(result.output.contains("age, id, name"))
    }
}