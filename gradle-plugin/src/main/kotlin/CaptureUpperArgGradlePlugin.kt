package ink.iowoi.kotlin.compiler.plugin.captureupperarg.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class CaptureUpperArgGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        // is necessary to load name from configuration?
        target.extensions.create("captureUpperArgv", CaptureUpperArgExtension::class.java)

        target.afterEvaluate {
            injectAnnotationDependency(target)
        }
    }

    private fun injectAnnotationDependency(project: Project) {
        val annotationArtifactId = GradleConfig.ANNOTATION_ARTIFACT_ID
        val annotationDependency = "${GradleConfig.GROUP_ID}:$annotationArtifactId:${GradleConfig.COMPILER_VERSION}"

        project.configurations.all {
            if (name == "implementation" || name == "compileOnly" || name == "api") {
                withDependencies {
                    val isAlreadyPresent = any { it.group == GradleConfig.GROUP_ID && it.name == annotationArtifactId }

                    if (!isAlreadyPresent) {
                        project.dependencies.add(name, annotationDependency)
                        project.logger.info("CaptureUpperArg: Added implicit dependency -> $annotationDependency")
                    }
                }
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = GradleConfig.COMPILER_PLUGIN_ID

    // location of compiler plugin, is necessary to fetch the latest available?
    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = GradleConfig.GROUP_ID,
        artifactId = GradleConfig.COMPILER_ARTIFACT_ID,
        version = GradleConfig.COMPILER_VERSION
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(CaptureUpperArgExtension::class.java)

        return project.provider {
            listOf(
                SubpluginOption(key = "enabled", value = extension.enabled.toString()),
            )
        }
    }
}