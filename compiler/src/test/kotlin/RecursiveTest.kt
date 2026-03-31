package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.KotlinCompilation
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationName
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaCollectField
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test

@OptIn(ExperimentalCompilerApi::class)
class RecursiveTest : BaseCapturePluginTest() {

    @Test
    fun `should prevent self capture in simple recursion`() {
        val result = compile("""
            class Test {
                fun countdown(count: Int, @$annotationName($cuaaCollectField = false) msg: String = "START") {
                    println(msg)
                    if (count > 0) {
                        // recursive call: the msg argument of the current function should not be captured to fill the next level
                        // expected result: each level should use the default value "START" if no msg is explicitly passed
                        countdown(count - 1) 
                    }
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val output = invokeMethod(result, "Test", "countdown", 2, "ROOT")

        // 1st layer: ROOT
        // 2nd layer: START
        // 3rd layer: START
        val lines = output.lines().filter { it.isNotBlank() }
        assert(lines[0].contains("ROOT"))
        assert(lines[1].contains("START"))
        assert(lines[2].contains("START"))
    }

    @Test
    fun `should allow capture in indirect recursion A to B to A`() {
        val result = compile("""
            class Test {
                fun funcA(token: String) {
                    println("A: " + token)
                    funcB()
                }

                fun funcB(@$annotationName($cuaaCollectField = false) token: String = "") {
                    println("B: " + token)
                    if (token == "STOP") return
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "funcA", "HELLO")

        assert(output.contains("A: HELLO"))
        assert(output.contains("B: HELLO"))
    }

    @Test
    fun `should support manual override even in recursion`() {
        val result = compile("""
            class Test {
                fun recurse(n: Int, @$annotationName($cuaaCollectField = false) tag: String = "DEF") {
                    println(tag)
                    if (n > 0) {
                        recurse(n - 1, "MANUAL_" + n)
                    }
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "recurse", 1, "ROOT")

        assert(output.contains("ROOT"))
        assert(output.contains("MANUAL_1"))
    }

    @Test
    fun `should exclude the collection parameter itself during recursive collection`() {
        val result = compile("""
            class Test {
                fun start(id: String, age: Int) = run(id, age)

                fun run(
                    id: String, 
                    age: Int, 
                    @$annotationName($cuaaCollectField = true) ctx: Map<String, Any> = emptyMap()
                ) {
                    println("Keys: " + ctx.keys.sorted().joinToString())
                    if (id != "STOP") {
                        run("STOP", 0) 
                    }
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "start", "FIRST", 20)

        /*
         1st layer (start -> run):
            should collect id and age
         2nd layer (run -> run):
            should collect upper level id ("FIRST") and age (20)
            but should never capture upper level ctx, or struct of Map will be complex and conflict
         */
        val lines = output.lines().filter { it.contains("Keys:") }

        assert(lines[0].contains("age") && lines[0].contains("id"))
        assert(!lines[1].contains("ctx"))
    }
}