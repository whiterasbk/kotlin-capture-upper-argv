package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.KotlinCompilation
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationName
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaCollectField
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.fail

@OptIn(ExperimentalCompilerApi::class)
class TypeMatchingTest : BaseCapturePluginTest() {

    @Test
    fun `should match subtypes but ignore unrelated types`() {
        val result = compile("""
            open class Base; class Derived : Base()
            class Test {
                fun call(d: Derived) = target()
                fun target(@$annotationName($cuaaCollectField = false) item: Base? = null) = println("Type: " + item?.javaClass?.simpleName)
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val loader = result.classLoader
        val derivedClass = loader.loadClass("Derived")
        val derivedInstance = derivedClass.getDeclaredConstructor().newInstance()

        val output = invokeMethod(result, "Test", "call", derivedInstance)

        assert(output.contains("Type: Derived"))
    }

    @Test
    fun `error - should pass null when type is nullable`() {
        val result = compile("""
            class Test {
                fun caller(i: Int) = target()
                fun target(@$annotationName($cuaaCollectField = false) s: String? = placeholder) {}
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        try {
            invokeMethod(result, "Test", "caller", 123)
            // if code reached here, that proves target$default is not invoked
        } catch (e: Exception) {
            if (e.cause is NotImplementedError) {
                fail("Plugin failed: placeholder was not replaced, target\$default was called instead.")
            } else {
                throw e
            }
        }
    }

    @Test
    fun `should collect into a Map with complex generic values`() {
        val result = compile("""
            class Test {
                fun service(results: Map<String, Int>, config: Map<String, String>) {
                    logAll()
                }

                fun logAll(@$annotationName data: Map<String, Map<String, *>> = emptyMap()) {
                    println("Keys: " + data.keys.sorted().joinToString())
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "service", mapOf("a" to 1), mapOf("b" to "c"))

        assert(output.contains("Keys: config, results"))
    }

    @Test
    fun `should use default value when generic types mismatch`() {
        val result = compile("""
            class Test {
                fun caller(data: List<Int>) = target()

                fun target(@$annotationName($cuaaCollectField = false) s: List<String> = emptyList()) { println(s) }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "caller", listOf(1, 2, 3))
        assert(output.contains("[]"))
    }

    @Test
    fun `should capture arguments with exact generic types`() {
        val result = compile("""
            class Test {
                fun business(list: List<String>, numbers: List<Int>) {
                    // required type is List<String>, should not capture type List<Int>
                    process()
                }

                fun process(@$annotationName($cuaaCollectField = false) data: List<String> = emptyList()) {
                    println("Size: " + data.size)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "business", listOf("A", "B"), listOf(1, 2, 3))

        assert(output.contains("Size: 2"))
    }

    @Test
    fun `should support covariance in generic arguments`() {
        val result = compile("""
            open class Animal
            class Cat : Animal()

            class Test {
                fun business(cats: List<Cat>) {
                    // List<Cat> should be a subclass of List<Animal>
                    handleAnimals()
                }

                fun handleAnimals(@$annotationName($cuaaCollectField = false) items: List<Animal> = emptyList()) {
                    println("Count: " + items.size)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val loader = result.classLoader
        val catClass = loader.loadClass("Cat")
        val catInstance1 = catClass.getDeclaredConstructor().newInstance()
        val catInstance2 = catClass.getDeclaredConstructor().newInstance()

        val catsList = listOf(catInstance1, catInstance2)

        val output = invokeMethod(result, "Test", "business", catsList)

        assert(output.contains("Count: 2"))
    }
}