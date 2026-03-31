package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.KotlinCompilation
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationName
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaCatchNearestCallTreeField
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaCollectField
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class CaptureScopeTest : BaseCapturePluginTest() {

    @Test
    fun `should penetrate lambdas to capture caller arguments`() {
        val result = compile("""
            class Test {
                fun service(requestId: String) {
                    listOf(1).forEach { target() } 
                }
                fun target(@$annotationName($cuaaCollectField = false) id: String = "") = println(id)
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "service", "REQ_123").contains("REQ_123"))
    }

    @Test
    fun `should capture caller params even when called inside a lambda`() {
        val result = compile("""
            class Test {
                fun service(requestId: String) {
                    val list = listOf(1)
                    list.forEach { item ->
                        // call inside lambda, should capture `requestId` of service successfully
                        // rather than `item` of lambda
                        logRequest()
                    }
                }
        
                fun logRequest(@$annotationName($cuaaCollectField = false) id: String = "") {
                    println("LogID: " + id)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val output = invokeMethod(result, "Test", "service", "REQ_456")
        assert(output.contains("LogID: REQ_456"))
    }

    @Test
    fun `test non-local scopes and lambdas`() {
        val result = compile("""
            class Test {
                fun outer(x: Int) {
                    fun local(z: Int) = base("local")
                    val lambda = { j: Int ->
                        val y = 20
                        base("lambda")
                        local(y)
                    }
                    lambda(114)
                }
        
                fun base(
                    name: String,
                    @$annotationName($cuaaCatchNearestCallTreeField = true) a: List<Int> = placeholder, 
                    @$annotationName($cuaaCatchNearestCallTreeField = false) b: List<Int> = placeholder,
                ) {
                    println(name + ": a = " + a)
                    println(name + ": b = " + b)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val output = invokeMethod(result, "Test", "outer", 514)

        assert(output.contains("lambda: a = [114]"))
        assert(output.contains("lambda: b = [514]"))
        assert(output.contains("local: a = [20]"))
        assert(output.contains("local: b = [514]"))
    }

    @Test
    fun `should penetrate multiple lambdas but stop at the nearest named none local function`() {
        val result = compile("""
            class Test {
                fun outerService(outerToken: String) {
                    fun innerLocal(innerToken: String) {
                        val lambda1 = {
                            val lambda2 = {
                                targetFunc()
                            }
                            lambda2()
                        }
                        lambda1()
                    }
                    innerLocal("INNER_VALUE")
                }
        
                fun targetFunc(@$annotationName($cuaaCatchNearestCallTreeField = false, $cuaaCollectField = false) token: String = "none") {
                    println("Final Captured: " + token)
                }
            }
        """)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val output = invokeMethod(result, "Test", "outerService", "OUTER_VALUE")

        assert(output.contains("Final Captured: OUTER_VALUE"))
        assert(!output.contains("INNER_VALUE"))
    }
}