package ink.iowoi.kotlin.compiler.plugin.captureupperarg

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid


@OptIn(UnsafeDuringIrConstructionAPI::class)
class CaptureUpperArgExtension(private val configuration: CompilerConfiguration) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(CaptureUpperArgTransformer(pluginContext, configuration))
    }
}