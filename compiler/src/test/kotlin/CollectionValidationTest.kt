package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.KotlinCompilation
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.CaptureDiagnosticCode
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationName
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test

@OptIn(ExperimentalCompilerApi::class)
class CollectionValidationTest : BaseCapturePluginTest() {

    @Test
    fun `should aggregate multiple params into Map`() {
        val result = compile("""
            class Test {
                fun business(id: String, age: Int?) = log()
                fun log(@$annotationName data: Map<String, Any?> = emptyMap()) {
                    println(data.keys.sorted().joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "business", "A", 1).contains("age, id"))
    }

    @Test
    fun `should aggregate multiple params into MutableMap`() {
        val result = compile("""
            class Test {
                fun business(id: String, age: Int?) = log()
                fun log(@$annotationName data: MutableMap<String, Any?> = mutableMapOf()) {
                    println(data.keys.sorted().joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "business", "A", 1).contains("age, id"))
    }

    @Test
    fun `should catch zero params into MutableMap`() {
        val result = compile("""
            class Test {
                fun business(id: String, age: Int?) = log()
                fun log(@$annotationName data: MutableMap<String, Boolean> = placeholder) {
                    println(data.keys.sorted().joinToString()) }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "business", "A", 1).isBlank())
    }

    @Test
    fun `should catch none null int in map`() {
        val result = compile("""
            class Test {
                fun business(id: Int, age: Int?) = log()
                fun log(@$annotationName data: MutableMap<String, Int> = placeholder) {
                    println(data.keys.sorted().joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "business", 1, 4).contains("id"))
    }

    @Test
    fun `should catch both none null or nullable int in map`() {
        val result = compile("""
            class Test {
                fun business(id: Int, age: Int?) = log()
                fun log(@$annotationName data: MutableMap<String, Int?> = placeholder) {
                    println(data.keys.sorted().joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "business", 1, 4).contains("age, id"))
    }

    @Test
    fun `should not aggregate multiple params into CustomMap`() {
        val result = compile("""
            class CustomMap<K, V>: Map<K, V> by HashMap() 
            class Test {
                fun business(id: String, age: Int?) = log()
                fun log(@$annotationName data: CustomMap<String, Any?> = placeholder) {
                    println(data.keys.sorted().joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains(CaptureDiagnosticCode.E005.id))
    }

    @Test
    fun `should aggregate multiple params into List`() {
        val result = compile("""
            class Test {
                fun business(id: String, age: Int?) = log()
                fun log(@$annotationName  data: List<Any?> = listOf()) {
                    println(data.joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "business", "A", 1).contains("A, 1"))
    }

    @Test
    fun `should aggregate multiple params into Set`() {
        val result = compile("""
            class Test {
                fun business(age1: Int, age2: Int, age3: Int) = log()
                fun log(@$annotationName  data: Set<Int> = setOf()) {
                    println(data.sorted().joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        assert(invokeMethod(result, "Test", "business", 5, 4, 5).contains("4, 5"))
    }

    @Test
    fun `collection generic type erasure`() {
        val result = compile("""
            open class A; class B : A()
            
            class Test {
                fun hook() { 
                    business(
                        a = B(),
                        b = mapOf("b" to 1),
                        c = mapOf("c" to null),
                        d = mapOf("d" to 100L),
                        e = mapOf("e" to true),
                        f = mapOf(1 to mapOf(1 to "valF")), 
                        g = mapOf(2 to mapOf(2L to "valG")), 
                        h = mapOf(3 to mapOf(3L to null)), 
                        i = mapOf(4 to mapOf(4 to B())), 
                    )
                }

                fun business(
                    a: B,
                    b: Map<String, Int>,
                    c: Map<String, Int?>,
                    d: Map<String, Long>,
                    e: Map<String, Boolean?>,
                    f: Map<Int, Map<Int, String>>, 
                    g: Map<Int, Map<Long, String>>, 
                    h: Map<Int, Map<Long, String?>>, 
                    i: Map<Int, Map<Int, B>>, 
                ) = log()

                /**
                 * check point: 
                 * data1: matching Map<String, Any>
                 * - b (Int:Any) match
                 * - d (Long:Any) match
                 * - c (Int?) mismatch (it's Any not Any?)
                 * data2: matching Map<String, Any?>
                 * - b, c, d, e all matched
                 * data3: matching Map<String, Int?>
                 * - b (Int) match
                 * - c (Int?) match
                 * data4: matching Map<Int, Map<Int, String>>
                 * - f match
                 * - g mismatch (Long != Int)
                 * data5: matching Map<Int, Map<Int, A>>
                 * - f mismatch (String is not subclass of A)
                 * - i match (B is subclass of A)
                 */
                fun log(
                    @$annotationName data1: List<Map<String, Any>> = placeholder, 
                    @$annotationName data2: List<Map<String, Any?>> = placeholder, 
                    @$annotationName data3: List<Map<String, Int?>> = placeholder, 
                    @$annotationName data4: List<Map<Int, Map<Int, String>>> = placeholder, 
                    @$annotationName data5: List<Map<Int, Map<Int, A>>> = placeholder, 
                ) {
                    println("data1: " + data1.map { it.values.firstOrNull() })
                    println("data2: " + data2.map { it.values.firstOrNull() })
                    println("data3: " + data3.map { it.values.firstOrNull() })
                    println("data4: " + data4.map { it.values.firstOrNull() })
                    println("data5: " + data5.map { it.values.firstOrNull() })
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "hook")

        assert(output.contains("data1: [1, 100]")) // only none null derived subclass of Any
        assert(output.contains("data2: [1, null, 100, true]")) // contains all Map<String, *>
        assert(output.contains("data3: [1, null]")) // only Int and Int?
        assert(output.contains("data4: [{1=valF}]"))
        assert(output.contains("data5: [{4=B@"))
    }

    @Test
    fun `should collect only non-nullable params when Map value type is non-nullable Any`() {
        val result = compile("""
            class Test {
                fun business(id: String, age: Int, gender: String?) = log()

                fun log(@$annotationName data: Map<String, Any> = emptyMap()) {
                    println("Keys: " + data.keys.sorted().joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val output = invokeMethod(result, "Test", "business", "ID_01", 25, null)

        assert(output.contains("Keys: age, id"))
        assert(!output.contains("gender"))
    }

    @Test
    fun `error - should report E005 for unsupported collection type`() {
        val result = compile("""
            class Test {
                fun caller(s: String) = target()
                fun target(@$annotationName (collect = true) data: String = "") {}
            }
        """).assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains(CaptureDiagnosticCode.E005.id))
    }

    @Test
    fun `error - should report E005 for unsupported collection key type`() {
        val result = compile("""
            class Test {
                fun caller(s: Long) = target()
                fun target(@$annotationName data: Map<Int, Long> = mapOf()) {
                    val (k, v) = data.entries.first()
                    println(k::class.simpleName)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assert(result.messages.contains(CaptureDiagnosticCode.E006.id))
    }
}