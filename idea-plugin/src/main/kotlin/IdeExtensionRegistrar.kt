package ink.iowoi.kotlin.idea.plugin.captureupperarg

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class IdeExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::HighlightChecker
    }
}
