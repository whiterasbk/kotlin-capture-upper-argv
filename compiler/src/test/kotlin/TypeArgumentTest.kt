package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.KotlinCompilation
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationName
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.cuaaCollectField
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test


@OptIn(ExperimentalCompilerApi::class)
class TypeArgumentTest : BaseCapturePluginTest() {

    @Test
    fun `should capture argument matching generic type parameter T`() {
        val result = compile("""
            class Test {
                fun <T> caller(value: T) = target<T>()
                fun <T> target(@$annotationName($cuaaCollectField = false) g: T = placeholder) {
                    println("Captured type: " + g?.javaClass?.simpleName)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val outputString = invokeMethod(result, "Test", "caller", "Hello")
        assert(outputString.contains("Captured type: String"))

        val outputInt = invokeMethod(result, "Test", "caller", 123)
        assert(outputInt.contains("Captured type: Integer"))
    }

    @Test
    fun `should support reified type parameters`() {
        val result = compile("""
            class Test {
                inline fun <reified T> caller(data: T) = target<T>()
                
                fun <T> target(@$annotationName($cuaaCollectField = false) item: T = placeholder) {
                    println("Reified capture: " + (item != null))
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)
        val output = invokeMethod(result, "Test", "caller", "Data")
        assert(output.contains("Reified capture: true"))
    }

    @Test
    fun `should respect generic constraints from upper bounds`() {
        val result = compile("""
            open class Base; class Derived : Base()
            
            class Test {
                fun <T : Base> caller(item: T) = target<T>()

                fun <T : Base> target(@$annotationName($cuaaCollectField = false) g: T = placeholder) {
                    println("Bounded capture: " + g.javaClass.simpleName)
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val loader = result.classLoader
        val derivedInstance = loader.loadClass("Derived").getDeclaredConstructor().newInstance()

        val output = invokeMethod(result, "Test", "caller", derivedInstance)
        assert(output.contains("Bounded capture: Derived"))
    }

    @Test
    fun `should capture from generic class context`() {
        val result = compile("""
            class Container<T>() {
                fun service(v: T): String = target()
                
                fun target(@$annotationName($cuaaCollectField = false) g: T = placeholder): String {
                    return "Class T capture: " + g.toString()
                }
            }
        """).assertCompilation(KotlinCompilation.ExitCode.OK)

        val loader = result.classLoader
        val containerClass = loader.loadClass("Container")

        val constructor = containerClass.getDeclaredConstructor()
        val instance = constructor.newInstance()

        val serviceMethod = containerClass.getDeclaredMethod("service", Any::class.java)

        val testMethodResult = serviceMethod.invoke(instance, "PLUGIN_VALUE") as String

        assert(testMethodResult.contains("Class T capture: PLUGIN_VALUE"))
    }

}