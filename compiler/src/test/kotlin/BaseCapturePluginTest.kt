package ink.iowoi.kotlin.compiler.plugin.captureupperarg.test

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.CaptureUpperArgPluginRegistrar
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotationFqName
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.placeholderFqName
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
abstract class BaseCapturePluginTest {

    protected fun JvmCompilationResult.assertCompilation(expected: KotlinCompilation.ExitCode): JvmCompilationResult {
        assertEquals(expected, this.exitCode)
        return this
    }

    protected fun compile(@Language("kotlin") source: String, importAnnotation: Boolean = true): JvmCompilationResult {
        val source = SourceFile.kotlin("Test.kt",
            (if (!importAnnotation) "" else "import $annotationFqName\nimport $placeholderFqName\n") +
                    source.trimIndent()
        )
        return compile(source)
    }

    protected fun compile(source: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            sources = listOf(source)
            compilerPluginRegistrars = listOf(CaptureUpperArgPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()
    }

    protected fun invokeMethod(result: JvmCompilationResult, className: String, methodName: String, vararg args: Any?): String {
        val loader = result.classLoader
        val clazz = loader.loadClass(className)
        val instance = clazz.getDeclaredConstructor().newInstance()
        val method = clazz.declaredMethods.find { it.name == methodName }
            ?: throw NoSuchMethodException(methodName)

        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        val ps = PrintStream(baos)
        System.setOut(ps)
        try {
            method.invoke(instance, *args)
        } finally {
            System.out.flush()
            System.setOut(originalOut)
        }
        return baos.toString()
    }
}