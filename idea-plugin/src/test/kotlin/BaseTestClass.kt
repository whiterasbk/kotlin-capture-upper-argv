package ink.iowoi.kotlin.idea.plugin.captureupperarg

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotation.CaptureUpperArg
import org.intellij.lang.annotations.Language

abstract class BaseTestClass : DeclarativeInlayHintsProviderTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return object : LightProjectDescriptor() {
            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)

                val libraryJarPath = System.getProperty("annotation.jar.path")
                val libraryTable = model.moduleLibraryTable
                val library = libraryTable.createLibrary("CaptureUpperArg-Annotation")
                val libModel = library.modifiableModel
                val jarUrl = VirtualFileManager.constructUrl(StandardFileSystems.JAR_PROTOCOL, libraryJarPath) + JarFileSystem.JAR_SEPARATOR
                val root = VirtualFileManager.getInstance().findFileByUrl(jarUrl)

                if (root != null) {
                    libModel.addRoot(root, OrderRootType.CLASSES)
                }
                libModel.commit()

            }
        }
    }

    protected fun doHighlightCheckTest(@Language("kotlin") code: String) {
        val fullCode = """
            import $annotationFqName
            import $placeholderFqName
            
            $code
        """.trimIndent()

        myFixture.configureByText("Test.kt", fullCode)
        myFixture.checkHighlighting(true, true, false)
    }

    protected fun doNormalQuickFixTest(@Language("kotlin") beforeCode: String, @Language("kotlin") afterCode: String, fixText: String) {
        val header = """
            import $annotationFqName
            import $placeholderFqName
        """.trimIndent()

        myFixture.configureByText("Test.kt", "$header\n\n$beforeCode")

        val availableFixes = myFixture.getAllQuickFixes()

        val action = availableFixes.find { it.text == fixText }
            ?: error("can not find quickfix displaying [$fixText], current quickfixes: ${availableFixes.map { it.text }}")

        myFixture.launchAction(action)

        myFixture.checkResult("$header\n\n$afterCode")
    }

    protected fun testInlayHint(@Language("kotlin") code: String, verifyHintsPresence: Boolean = true) = doTestProvider(
        "Test.kt",
        """
            import $annotationFqName
            import $placeholderFqName
            
            $code
        """.trimIndent(),
        InlayHintsProvider(),
        emptyMap(),
        verifyHintsPresence = verifyHintsPresence,
        testMode = ProviderTestMode.SIMPLE
    )
}