package ink.iowoi.kotlin.compiler.plugin.captureupperarg

import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotation.CaptureUpperArg
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.render

enum class ContainerType { Variable, Map, MutableMap, Set, MutableSet, List, MutableList }

data class AnnotationContext(
    val targetContainerName: String,
    val containerType: ContainerType,
    val annotations: List<IrType>,
    val type: IrType,
    val typeNullable: Boolean,
    val exclude: List<String>,
    val catchNearestCallTree: Boolean,
)

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun CaptureUpperArgTransformer.parseAnnotationContext(
    annotationCall: IrConstructorCall,
    targetParam: IrValueParameter,
    context: IrPluginContext,
): AnnotationContext {

    fun getArg(name: String): Any? {
        val index = annotationCall.symbol.owner.parameters.indexOfFirst { it.name.asString() == name }
        return (annotationCall.arguments.getOrNull(index) as? IrConst)?.value
    }

    fun getArrayArg(name: String): List<Any?> {
        val index = annotationCall.symbol.owner.parameters.indexOfFirst { it.name.asString() == name }
        val vararg = annotationCall.arguments.getOrNull(index) as? IrVararg ?: return emptyList()
        return vararg.elements.map { (it as? IrConst)?.value ?: (it as? IrClassReference)?.classType }
    }

    val isCollectMode = getArg(cuaaCollectField) as? Boolean ?: collectDefault
    val isCatchNearestCallTree: Boolean = getArg(cuaaCatchNearestCallTreeField) as? Boolean ?: catchNearestCallTreeDefault
    val targetIrType = targetParam.type

    if (targetParam.defaultValue == null) {
        reportWarning(
            buildW002WarningMessage(targetParam.name.asString()),
            targetParam
        )
    }

    val derivedContainerType = if (!isCollectMode) {
        ContainerType.Variable
    } else {
        when {
            targetIrType.isMapType() -> {
                val keyType = (targetIrType as? IrSimpleType)?.arguments?.firstOrNull()?.typeOrNull

                keyType?.takeIf { !it.isString() } ?.let {
                    reportError(buildW006ErrorMessage(targetParam.name.asString(), it.render()),
                        targetParam)
                    throw CaptureTerminationException()
                }

                ContainerType.Map
            }
            targetIrType.isListType() -> ContainerType.List
            targetIrType.isSetType() -> ContainerType.Set
            targetIrType.isMutableMapType() -> {

                val keyType = (targetIrType as? IrSimpleType)?.arguments?.firstOrNull()?.typeOrNull

                keyType?.takeIf { !it.isString() } ?.let {
                    reportError(buildW006ErrorMessage(targetParam.name.asString(), it.render()),
                        targetParam)
                    throw CaptureTerminationException()
                }

                ContainerType.MutableMap
            }
            targetIrType.isMutableListType() -> ContainerType.MutableList
            targetIrType.isMutableSetType() -> ContainerType.MutableSet
            else -> {
                reportError(buildW005ErrorMessage(targetParam.name.asString(), targetIrType.render()),
                    targetParam)
                throw CaptureTerminationException()
            }
        }
    }

    // extract type parameter
    val elementIrType = if (derivedContainerType != ContainerType.Variable && targetIrType is IrSimpleType) {
        // take the last as type
        (targetIrType.arguments.lastOrNull() as? IrTypeProjection)?.type ?: context.irBuiltIns.anyType
    } else {
        targetIrType
    }

    return AnnotationContext(
        targetContainerName = targetParam.name.asString(),
        containerType = derivedContainerType,
        annotations = getArrayArg(cuaaAnnotationsField).filterIsInstance<IrType>(),
        type = elementIrType,
        typeNullable = elementIrType.isNullable(),
        exclude = getArrayArg(cuaaExcludeField).filterIsInstance<String>(),
        catchNearestCallTree = isCatchNearestCallTree,
    )
}