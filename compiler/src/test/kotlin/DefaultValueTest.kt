package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.KotlinCompilation
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.CaptureDiagnosticCode
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationName
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaCollectField
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test


@OptIn(ExperimentalCompilerApi::class)
class DefaultValueTest : BaseCapturePluginTest() {

    @Test
    fun `should fallback to normal default value when no matching variable found`() {
        val result = compile("""
            class Test {
                fun business(id: Int) {
                    target() 
                }
                fun target(@$annotationName($cuaaCollectField = false) token: String = "FALLBACK_VALUE") {
                    println(token)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "business", 123)
        assert(output.contains("FALLBACK_VALUE"))
    }

    @Test
    fun `error - E004 should be reported when using placeholder but capture fails`() {
        val result = compile("""
            class Test {
                fun business(id: Int) {
                    // no String in argument list, and use `placeholder` meanwhile 
                    target() 
                }
                fun target(@$annotationName($cuaaCollectField = false) token: String = placeholder) {
                    println(token)
                }
            }
        """)
        
        result.assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains(CaptureDiagnosticCode.E004.id))
    }

    @Test
    fun `error - E004 should be reported when no matching variable and no default value`() {
        val result = compile("""
            class Test {
                fun business() {
                    target() 
                }
                // nither delault value provided nor available String type to capture
                fun target(@$annotationName($cuaaCollectField = false) token: String) {
                    println(token)
                }
            }
        """)

        result.assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains("No value passed for parameter 'token'."))
    }

    @Test
    fun `should capture successfully even if placeholder is present`() {
        val result = compile("""
            class Test {
                fun business(token: String) {
                    target() 
                }
                fun target(@$annotationName($cuaaCollectField = false) token: String = placeholder) {
                    println("Captured: " + token)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "business", "SUCCESS_TOKEN")
        
        // `placeholder` should be replaced with captured variable
        assert(output.contains("Captured: SUCCESS_TOKEN"))
    }

    @Test
    fun `should capture null even if placeholder is present`() {
        val result = compile("""
            class Test {
                fun business(token: Int) {
                    target() 
                }
                fun target(@$annotationName($cuaaCollectField = false) token: String? = placeholder) {
                    println("Captured: " + token)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "business", 114514)

        assert(output.contains("Captured: null"))
    }
}