package ink.iowoi.kotlin.compiler.plugin.captureupperarg.gradle.test

import ink.iowoi.kotlin.compiler.plugin.captureupperarg.gradle.CaptureUpperArgExtension
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.gradle.CaptureUpperArgGradlePlugin
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class CaptureUpperArgGradlePluginTest {
    @Test
    fun `plugin registers extension correctly`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CaptureUpperArgGradlePlugin::class.java)

        val extension = project.extensions.findByType(CaptureUpperArgExtension::class.java)
        assertNotNull(extension)
        assertEquals(true, extension.enabled)
    }
}