package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.KotlinCompilation
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationName
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaAnnotationsField
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaCollectField
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test

@OptIn(ExperimentalCompilerApi::class)
class FeatureCaptureTest : BaseCapturePluginTest() {

    @Test
    fun `should capture basic arguments`() {
        val result = compile("""
            class Test {
                fun caller(token: String) = target()
                fun target(@$annotationName($cuaaCollectField = false) token: String = "") = println(token)
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "caller", "HELLO").contains("HELLO"))
    }

    @Test
    fun `should capture multiple parameters by matching their types`() {
        val result = compile("""
            class Test {
                fun process(userId: String, retryCount: Int, isPremium: Boolean) {
                    // should skip Boolean
                    handleRequest()
                }
        
                fun handleRequest(
                    @$annotationName($cuaaCollectField = false) id: String = "",
                    @$annotationName($cuaaCollectField = false) attempts: Int = 0
                ) {
                    println("ID: " + id + ", Attempts: " + attempts)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "process", "USER_123", 3, true)
        assert(output.contains("ID: USER_123, Attempts: 3"))
    }

    @Test
    fun `should only capture parameters with specific annotations`() {
        val result = compile("""
            annotation class Sensitive
            annotation class Wired

            class Test {
                fun transfer(normalInfo: String, @Sensitive token: String, @Wired attempts: String, @Sensitive @Wired bvc: String) {
                    secureCall()
                }
        
                fun secureCall(
                    @$annotationName($cuaaAnnotationsField = [Sensitive::class], $cuaaCollectField = false) secret: String = placeholder,
                    @$annotationName($cuaaAnnotationsField = [Wired::class]) op: List<String> = placeholder,
                    @$annotationName($cuaaAnnotationsField = [Wired::class, Sensitive::class]) ap: List<String> = placeholder,
                ) {
                    println("Secret: " + secret)
                    println("op: " + op)
                    println("ap: " + ap)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val output = invokeMethod(result, "Test", "transfer", "INFO_999", "KEY_888", "Mi", "Brief")
        assert(output.contains("Secret: KEY_888"))
        assert(output.contains("op: [Mi, Brief]"))
        assert(output.contains("ap: [Brief]"))
    }

    @Test
    fun `should only capture parameters target, if giving list container`() {
        val result = compile("""
            class Test {
                fun business(a: Int, b: List<Int>, c: List<String>) = log()
                fun log(@$annotationName($cuaaCollectField = false) data: List<Int> = placeholder) = println(data)
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "business", 2, listOf(9), listOf("hachimi"))
        assert(output.replace(" ", "").contains("[9]"))
    }

    @Test
    fun `should handle mixed arguments correctly`() {
        val result = compile("""
            class Test {
                fun complexFlow(traceId: String) {
                    // 1. pass `appId` manually
                    // 2. `traceId` waiting to inject
                    // 3. `msg` use defalut value
                    doSomething(appId = "MANUAL_APP")
                }
        
                fun doSomething(
                    appId: String, 
                    @$annotationName($cuaaCollectField = false) traceId: String = "none",
                    msg: String = "default_msg"
                ) {
                    println("App: " + appId + ", Trace: " + traceId + ", Msg: " + msg)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val output = invokeMethod(result, "Test", "complexFlow", "TRACE_777")
        assert(output.contains("App: MANUAL_APP"))
        assert(output.contains("Trace: TRACE_777"))
        assert(output.contains("Msg: default_msg"))
    }

}