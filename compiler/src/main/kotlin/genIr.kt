package ink.iowoi.kotlin.compiler.plugin.captureupperarg

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.IdentityHashMap

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class IrPluginCache(val context: IrPluginContext) {
    val stringType by lazy { context.irBuiltIns.stringType }
    val anyNullableType by lazy { context.irBuiltIns.anyNType }

    val mapOfFunction: IrSimpleFunctionSymbol by lazy {
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("mapOf"))
        ).firstOrNull { symbol ->
            val regularParams = symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            regularParams.size == 1 && regularParams[0].isVararg
        } ?: error("mapOfFunction not found")
    }

    val mutableMapOfFunction: IrSimpleFunctionSymbol by lazy {
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("mutableMapOf"))
        ).firstOrNull { symbol ->
            val regularParams = symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            regularParams.size == 1 && regularParams[0].isVararg
        } ?: error("mutableMapOfFunction not found")
    }

    val listOfFunction: IrSimpleFunctionSymbol by lazy {
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("listOf"))
        ).first { it.owner.parameters.firstOrNull()?.isVararg == true }
    }

    val mutableListOfFunction: IrSimpleFunctionSymbol by lazy {
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("mutableListOf"))
        ).first { it.owner.parameters.firstOrNull()?.isVararg == true }
    }

    val setOfFunction: IrSimpleFunctionSymbol by lazy {
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("setOf"))
        ).first { it.owner.parameters.firstOrNull()?.isVararg == true }
    }

    val mutableSetOfFunction: IrSimpleFunctionSymbol by lazy {
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("mutableSetOf"))
        ).first { it.owner.parameters.firstOrNull()?.isVararg == true }
    }

    val pairConstructor: IrConstructorSymbol by lazy {
        context.referenceConstructors(
            ClassId.topLevel(FqName("kotlin.Pair"))
        ).firstOrNull { symbol ->
            symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }.size == 2
        } ?: error("pair constructor not found")
    }
}

private val globalPluginCache = IdentityHashMap<IrPluginContext, IrPluginCache>()

private val IrPluginContext.cache: IrPluginCache
    get() = globalPluginCache.getOrPut(this) { IrPluginCache(this) }

val IrPluginContext.stringType: IrType get() = cache.stringType

val IrPluginContext.anyNullableType: IrType get() = cache.anyNullableType

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrPluginContext.mapOfFunction: IrSimpleFunctionSymbol get() = cache.mapOfFunction

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrPluginContext.listOfFunction: IrSimpleFunctionSymbol get() = cache.listOfFunction

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrPluginContext.setOfFunction: IrSimpleFunctionSymbol get() = cache.setOfFunction

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrPluginContext.pairConstructor: IrConstructorSymbol get() = cache.pairConstructor

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrPluginContext.mutableMapOfFunction: IrSimpleFunctionSymbol get() = cache.mutableMapOfFunction

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrPluginContext.mutableListOfFunction: IrSimpleFunctionSymbol get() = cache.mutableListOfFunction

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrPluginContext.mutableSetOfFunction: IrSimpleFunctionSymbol get() = cache.mutableSetOfFunction


/**
 * generate mapOf("a" to a, "b" to b)
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun DeclarationIrBuilder.generateMapOfExpression(
    parameters: List<IrValueParameter>,
    mutable: Boolean,
    pluginContext: IrPluginContext,
    context: AnnotationContext,
): IrExpression {

    val elementType = if (context.typeNullable) {
        context.type.makeNullable()
    } else {
        context.type // retain original (might none-nullable or nullable at definition)
    }

    val pairExpressions = parameters.map { param ->
        irCall(pluginContext.pairConstructor).apply {
            typeArguments[0] = pluginContext.stringType
            typeArguments[1] = elementType
            arguments[0] = irString(param.name.asString())
            arguments[1] = irImplicitCast(irGet(param), elementType)
        }
    }

    return irCall(if (mutable) pluginContext.mutableMapOfFunction else pluginContext.mapOfFunction).apply {
        typeArguments[0] = pluginContext.stringType
        typeArguments[1] = elementType
        arguments[0] = irVararg(pluginContext.pairConstructor.owner.returnType, pairExpressions)
    }
}

/**
 * generate listOf(a, b, c) or setOf(a, b, c)
 */
fun DeclarationIrBuilder.generateCollectionOfExpression(
    parameters: List<IrValueParameter>,
    funcSymbol: IrSimpleFunctionSymbol,
    context: AnnotationContext,
): IrExpression {

    val elementType = if (context.typeNullable) {
        context.type.makeNullable()
    } else {
        context.type
    }

    return irCall(funcSymbol).apply {
        typeArguments[0] = elementType

        arguments[0] = irVararg(elementType, parameters.map { param ->
            // handle boxing
            irImplicitCast(irGet(param), elementType)
        })
    }
}
