package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.KotlinCompilation
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.CaptureDiagnosticCode
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationName
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaCollectField
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test

@OptIn(ExperimentalCompilerApi::class)
class DiagnosticValidationTest : BaseCapturePluginTest() {

    @Test
    fun `error - should report E001 in init block`() {
        val result = compile("""
            class Host {
                init { target() }
                fun target(@$annotationName($cuaaCollectField = false) t: String = "") {}
            }
        """).assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains(CaptureDiagnosticCode.E001.id))
    }

    @Test
    fun `error - should report E002 when called in top-level property initializer`() {
        val result = compile("""
            val globalToken = targetService()
            fun targetService(@$annotationName($cuaaCollectField = false) token: String = "none"): String = token
        """).assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains(CaptureDiagnosticCode.E002.id))
    }

    @Test
    fun `error - should report E002 when called in class property initializer`() {
        val result = compile("""
            class Host {
                val someProperty = targetService()
                fun targetService(@$annotationName($cuaaCollectField = false) token: String = "none") = token
            }
        """).assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains(CaptureDiagnosticCode.E002.id))
    }

    @Test
    fun `warn - should report W002 when default value is missing`() {
        val result = compile("""
            class Test {
                fun target(@$annotationName($cuaaCollectField = false) token: String) {}
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(result.messages.contains(CaptureDiagnosticCode.W002.id))
    }

    @Test
    fun `warn - should report W001 in local function`() {
        val result = compile("""
            fun outer() {
                fun inner() { target() }
            }
            fun target(@$annotationName($cuaaCollectField = false) t: String = "") {}
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(result.messages.contains(CaptureDiagnosticCode.W001.id))
    }

    @Test
    fun `error - should report E005 even if the function is never called`() {
        val result = compile("""
            class Test {
                fun invalidTarget(@$annotationName($cuaaCollectField = true) data: String) {
                    println(data)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains(CaptureDiagnosticCode.E005.id))
    }
}