package ink.iowoi.kotlin.compiler.plugin.captureupperarg

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.render

@OptIn(UnsafeDuringIrConstructionAPI::class)
class CaptureUpperArgTransformer(
    private val pluginContext: IrPluginContext,
    private val configuration: CompilerConfiguration
) : IrElementTransformerVoidWithContext() {

    private fun report(
        message: String,
        element: IrElement,
        severity: CompilerMessageSeverity = CompilerMessageSeverity.WARNING
    ) {
        val fileEntry = currentFile.fileEntry

        val line = fileEntry.getLineNumber(element.startOffset) + 1
        val column = fileEntry.getColumnNumber(element.startOffset) + 1

        val location = CompilerMessageLocation.create(
            path = fileEntry.name,
            line = line,
            column = column,
            lineContent = null
        )

        configuration.messageCollector.report(
            severity,
            "[$annotationName] $message",
            location
        )
    }

    fun reportError(message: String, element: IrElement) =
        report(message, element, CompilerMessageSeverity.ERROR)

    fun reportWarning(message: String, element: IrElement) =
        report(message, element, CompilerMessageSeverity.WARNING)

    private val currentCaller get() = currentFunction?.irElement as? IrFunction

    private val injectionCache = mutableMapOf<IrFunction, List<Pair<IrValueParameter, AnnotationContext>>>()

    override fun visitCall(expression: IrCall): IrExpression {
        return try {
            val callee = expression.symbol.owner

            val injectionTasks = getOrComputeInjectionTasks(callee)

            if (injectionTasks.isEmpty()) return super.visitCall(expression)

            validateContextAndReport(expression)

            performInjection(expression, injectionTasks)

        } catch (_: CaptureTerminationException) {
            expression
        } catch (e: Exception) {
            reportError(e.message ?: "Annotation analysis failed", expression)
            expression
        }
    }

    override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
        // check if match function declaration
        if (declaration is IrFunction) {
            try {
                getOrComputeInjectionTasks(declaration)
            } catch (e: Exception) {
                if (e is CaptureTerminationException) {
                    // do nothing here
                } else {
                    throw e
                }
            }
        }
        return super.visitDeclaration(declaration)
    }

    private fun performInjection(expression: IrCall, injectionTasks: List<Pair<IrValueParameter, AnnotationContext>>): IrExpression {
        val call = super.visitCall(expression) as IrCall
        val callee = call.symbol.owner

        if (injectionTasks.isEmpty()) return call

        val caller = currentCaller
        val isRecursive = caller?.symbol == callee.symbol

        val builder = DeclarationIrBuilder(pluginContext, call.symbol, call.startOffset, call.endOffset)

        for ((parameter, annContext) in injectionTasks) {
            // skip if caller passing arguments manually
            if (call.arguments[parameter.indexInParameters] != null) continue

            val matchedArgs = filterCaptureableArgs(annContext, callee, expression)

            // recursive case: passing directly, if match condition (name type, ...)
            val forwardParam = if (isRecursive) {
                matchedArgs.find { arg ->
                    arg.name == parameter.name &&
                            arg.type.isSubTypeOf(parameter.type, pluginContext)
                }
            } else null

            val injectedExpr: IrExpression? = if (forwardParam != null) {
                builder.irGet(forwardParam)
            } else {
                when (annContext.containerType) {
                    ContainerType.Variable -> {
                        val firstMatchedArg = matchedArgs.firstOrNull()

                        if (firstMatchedArg != null) {
                            builder.irImplicitCast(builder.irGet(firstMatchedArg), pluginContext.anyNullableType)
                        } else when {
                            // not captures found in scope
                            // case A: passing '= placeholder' explicitly and variable definition is none nullable
                            parameter.defaultValue?.expression.isPlaceholderCall() -> {
                                if (annContext.typeNullable) { // user type is nullable
                                    builder.irNull(parameter.type)
                                } else {
                                    reportError(
                                        "${CaptureDiagnosticCode.E004} Cannot find a matching variable for none-null parameter '${parameter.name}'. " +
                                                "The explicit 'placeholder' requires a valid none-null context variable to be captured.",
                                        call
                                    )
                                    throw CaptureTerminationException()
                                }
                            }

                            // case B: did not provide default value, but this case seems to handled by kotlin official compiler first
                            parameter.defaultValue == null -> {
                                if (annContext.typeNullable) {
                                    builder.irNull(parameter.type)
                                } else {
                                    reportError(
                                        "${CaptureDiagnosticCode.E004} Cannot find a matching variable for non-nullable parameter '${parameter.name}'. " +
                                                "Expected type: ${annContext.type.render()}",
                                        call
                                    )
                                    throw CaptureTerminationException()
                                }
                            }

                            // case C: provided default value but plugin did not find any matched.
                            else -> {
                                // return null not invoke with
                                null
                            }
                        }
                    }

                    ContainerType.Map -> builder.generateMapOfExpression(matchedArgs, false, pluginContext, annContext)
                    ContainerType.MutableMap -> builder.generateMapOfExpression(matchedArgs, false, pluginContext, annContext)
                    ContainerType.List -> builder.generateCollectionOfExpression(matchedArgs,  pluginContext.listOfFunction, annContext)
                    ContainerType.MutableList -> builder.generateCollectionOfExpression(matchedArgs,  pluginContext.mutableListOfFunction, annContext)
                    ContainerType.Set -> builder.generateCollectionOfExpression(matchedArgs,  pluginContext.setOfFunction, annContext)
                    ContainerType.MutableSet -> builder.generateCollectionOfExpression(matchedArgs, pluginContext.mutableSetOfFunction, annContext)
                }
            }

            injectedExpr?.let {
                call.arguments[parameter.indexInParameters] = it
            }

        }

        return call
    }

    private fun filterCaptureableArgs(context: AnnotationContext, callee: IrFunction, expression: IrCall): List<IrValueParameter> {
        val caller = findEffectiveCaller(context) ?: return emptyList()

        val targetType = resolveTargetType(context, callee, expression)
        val isRecursive = caller.symbol == callee.symbol

        return caller.parameters.filter { param ->
            isParamValidForCapture(param, context, targetType, isRecursive)
        }
    }

    private fun findEffectiveCaller(context: AnnotationContext): IrFunction? {
        if (context.catchNearestCallTree) return currentCaller

        return allScopes
            .mapNotNull { it.irElement as? IrFunction }
            .reversed()
            .firstOrNull { func ->
                val name = func.name.asString()
                val isAnonymous = name == "<anonymous>" || name.contains("lambda")

                val isLocalOrLambda = func.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                        || func.origin == IrDeclarationOrigin.LOCAL_FUNCTION

                val isSynthetic = func.origin.isSynthetic ||
                        func.origin == IrDeclarationOrigin.GENERATED_DATA_CLASS_MEMBER

                !isAnonymous && !isLocalOrLambda && !isSynthetic
            }
    }

    private fun resolveTargetType(context: AnnotationContext, callee: IrFunction, expression: IrCall): IrType {
        val baseType = context.type
        if (baseType is IrSimpleType && baseType.classifier is IrTypeParameterSymbol) {
            val typeParameter = baseType.classifier.owner as IrTypeParameter
            val index = callee.typeParameters.indexOf(typeParameter)
            if (index >= 0) {
                return expression.typeArguments[index] ?: baseType
            }
        }
        return baseType
    }

    private fun isParamValidForCapture(param: IrValueParameter, context: AnnotationContext, targetType: IrType, isRecursive: Boolean): Boolean {
        val name = param.name.asString()

        // keep normal param, exclude `this` pointer
        if (param.kind != IrParameterKind.Regular) return false

        if (isRecursive && name == context.targetContainerName) return false
        if (name in context.exclude) return false

        if (!checkTypeMatch(param.type, targetType, context.typeNullable)) return false

        if (!checkAnnotationsSubset(param, context.annotations)) return false

        return true
    }

    private fun checkTypeMatch(paramType: IrType, targetType: IrType, isNullableAllowed: Boolean): Boolean {
        if (!isNullableAllowed && paramType.isNullable()) return false

        return isDeepSubTypeOf(paramType, targetType)
    }

    /**
     * check types and type parameter recursively
     * rules:
     * - Param: Map<B, F>, Target: Map<B, C>
     * - matches if F is subclass of C
     */
    private fun isDeepSubTypeOf(param: IrType, target: IrType): Boolean {
        if (!param.isSubTypeOf(target, pluginContext)) return false
        if (param !is IrSimpleType || target !is IrSimpleType) return true

        val paramArgs = param.arguments
        val targetArgs = target.arguments

        // if the count of type parameters mismatch (might see in subclass extending)
        // depends on result of `isSubTypeOf`. but in strict mode, they should retain the same count regularly.
        if (paramArgs.size != targetArgs.size) return true

        for (i in paramArgs.indices) {
            val pArg = paramArgs[i]
            val tArg = targetArgs[i]

            // handle (*)
            // matches if is Map<*, *>
            if (tArg is IrStarProjection) continue
            if (pArg is IrStarProjection) return false // has * but target require specific type, do not match

            val pType = pArg.typeOrNull ?: continue
            val tType = tArg.typeOrNull ?: continue

            // check recursively
            // notice: nullable limitation
            // if type parameter of target is C?, then pType can be C or C?
            // if type parameter of target is C, then pType has to be C
            val isTargetArgNullable = tType.isNullable()

            if (isTargetArgNullable) {
                // Target is C?, then Param can be C or its derived subclass (no metter the Param is nullable or not)
                if (!isDeepSubTypeOf(pType, tType)) return false
            } else {
                // Target is C, then Param has to be none null type and derived subclass of C
                if (pType.isNullable()) return false
                if (!isDeepSubTypeOf(pType, tType)) return false
            }
        }

        return true
    }

    private fun checkAnnotationsSubset(param: IrValueParameter, requiredAnnotations: List<IrType>): Boolean {
        if (requiredAnnotations.isEmpty()) return true

        val paramAnnotationTypes = param.annotations.map { it.type }
        return requiredAnnotations.all { required ->
            paramAnnotationTypes.any { actual -> actual == required }
        }
    }

    private fun getOrComputeInjectionTasks(callee: IrFunction): List<Pair<IrValueParameter, AnnotationContext>> {
        return injectionCache.getOrPut(callee) {
            callee.parameters.mapNotNull { parameter ->
                val annotation = parameter.annotations.find {
                    it.symbol.owner.parentAsClass.fqNameWhenAvailable == annotationFqName
                } ?: return@mapNotNull null

                parameter to parseAnnotationContext(annotation, parameter, pluginContext)
            }
        }
    }

    private fun IrExpression?.isPlaceholderCall(): Boolean {
        if (this !is IrCall) return false

        val callee = this.symbol.owner

        val currentFqName = callee.correspondingPropertySymbol?.owner?.fqNameWhenAvailable
            ?: callee.fqNameWhenAvailable

        return currentFqName == placeholderFqName
    }

    private fun validateContextAndReport(expression: IrCall) {
        val scopes = allScopes.map { it.irElement }

        val immediateFunction = scopes.filterIsInstance<IrFunction>().lastOrNull()
        val immediateProperty = scopes.filterIsInstance<IrProperty>().lastOrNull()
        val immediateInitBlock = scopes.filterIsInstance<IrAnonymousInitializer>().lastOrNull()

        val calleeName = expression.symbol.owner.name.asString()

        when {
            immediateInitBlock != null -> {
                reportError(
                    "${CaptureDiagnosticCode.E001} The function '$calleeName' with @$annotationName cannot be called inside an 'init' block. " +
                            "Init blocks do not have a parameter list to capture from.",
                    expression
                )
                throw CaptureTerminationException()
            }

            // call in property getter/setter initialization (and not in any functions)
            immediateFunction == null && immediateProperty != null -> {
                reportError(
                    "${CaptureDiagnosticCode.E002} The function '$calleeName' with @$annotationName cannot be called in the initializer of property '${immediateProperty.name}'. " +
                            "Move this call into a function or property getter.",
                    expression
                )
                throw CaptureTerminationException()
            }

            // call in top level
            immediateFunction == null -> {
                reportError(
                    "${CaptureDiagnosticCode.E003} The function '$calleeName' with @$annotationName cannot be called at top-level. " +
                            "It must be invoked inside a function to capture parameters.",
                    expression
                )
                throw CaptureTerminationException()
            }
        }

        // call in local function
        val caller = immediateFunction
        if (caller.origin == IrDeclarationOrigin.LOCAL_FUNCTION) {
            reportWarning(
                "${CaptureDiagnosticCode.W001} Usage of @$annotationName inside Local Function '${caller.name}' is discouraged. " +
                        "The compiler may generate synthetic parameters (e.g. for closures) that conflict with " +
                        "automatic parameter capturing. Consider passing arguments explicitly.",
                expression
            )
        }
    }
}