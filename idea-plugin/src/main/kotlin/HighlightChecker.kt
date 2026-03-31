package ink.iowoi.kotlin.idea.plugin.captureupperarg

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.types.*

class HighlightChecker(session: FirSession) : FirAdditionalCheckersExtension(session) {

    enum class CallContext { INIT_BLOCK, PROPERTY_INITIALIZER, TOP_LEVEL, NORMAL_FUNCTION, LOCAL_FUNCTION, LAMBDA }

    private fun annotationFilter(ann: FirAnnotation): Boolean = ann.fqName(session) == annotationFqName

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val functionCheckers: Set<FirFunctionChecker> = setOf(object : FirFunctionChecker(MppCheckerKind.Common) {
            override fun check(declaration: FirFunction, context: CheckerContext, reporter: DiagnosticReporter) {
                declaration.valueParameters.forEach { parameter ->
                    val annotation = parameter.annotations.find(::annotationFilter) ?: return@forEach

                    if (parameter.defaultValue == null) {
                        reporter.reportOn(
                            parameter.source,
                            HighlightErrorAndWarnings.MISSING_DEFAULT_VALUE,
                            HighlightMessageRender.missingDefault(parameter.name.asString()),
                            context,
                            SourceElementPositioningStrategies.DECLARATION_NAME
                        )
                    }

                    if (annotation.getBooleanValue(cuaaCollectField, session, true)) {
                        if (!parameter.returnTypeRef.isSupportedCollection()) {
                            reporter.reportOn(
                                parameter.source,
                                HighlightErrorAndWarnings.UNSUPPORTED_CONTAINER,
                                HighlightMessageRender.unsupportedContainer(parameter.name.asString(), parameter.returnTypeRef.coneType.renderReadable()),
                                context,
                                SourceElementPositioningStrategies.DECLARATION_RETURN_TYPE
                            )
                        }

                        if (parameter.returnTypeRef.coneType.isMap || parameter.returnTypeRef.coneType.isMutableMap) {
                            parameter.returnTypeRef
                                .coneType
                                .typeArguments
                                .firstOrNull()
                                ?.takeIf { it.type?.isString == false }
                                ?.let {
                                    reporter.reportOn(
                                        parameter.source,
                                        HighlightErrorAndWarnings.UNSUPPORTED_CONTAINER_KEY,
                                        HighlightMessageRender.unsupportedContainerKey(parameter.name.asString(),
                                            it.type?.renderReadable() ?: "<unknown>"
                                        ),
                                        context,
                                        SourceElementPositioningStrategies.DECLARATION_RETURN_TYPE
                                    )
                            }
                        }
                    }
                }
            }
        })
    }

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(object : FirFunctionCallChecker(MppCheckerKind.Common) {
            override fun check(expression: FirFunctionCall, context: CheckerContext, reporter: DiagnosticReporter) {
                val callee = expression.calleeReference.toResolvedFunctionSymbol() ?: return
                var hasCaptureParam = false

                for (paramSymbol in callee.valueParameterSymbols) {
                    paramSymbol.annotations.find(::annotationFilter) ?: continue
                    hasCaptureParam = true

                    expression.resolvedArgumentMapping?.entries?.forEach { (k, v) ->
                        if (k.isPlaceholderExpression(context.session)) {
                            reporter.reportOn(
                                k.source,
                                HighlightErrorAndWarnings.PASSING_PLACEHOLDER,
                                HighlightMessageRender.passingPlaceholder(v.name.asString()),
                                context,
                                SourceElementPositioningStrategies.VALUE_ARGUMENTS
                            )
                        }
                    }

                    if (paramSymbol.hasDefaultValue) {
                        if (paramSymbol.resolvedDefaultValue?.isPlaceholderExpression(context.session) == true) {
                            if (expression.resolvedArgumentMapping?.values?.map { it.symbol }?.contains(paramSymbol) == false) {
                                if (!paramSymbol.resolvedReturnTypeRef.isSupportedCollection()
                                    && !paramSymbol.resolvedReturnType.isMarkedNullable)
                                reporter.reportOn(
                                    expression.source,
                                    HighlightErrorAndWarnings.PASSING_PLACEHOLDER,
                                    HighlightMessageRender.passingPlaceholder(paramSymbol.name.asString()),
                                    context,
                                    SourceElementPositioningStrategies.VALUE_ARGUMENTS
                                )
                            }
                        }
                    }

                    // for ((argv, param) in expression.resolvedArgumentMapping ?: continue) {
                    //     if (param.symbol != paramSymbol) continue
                    //
                    //     if (argv.isPlaceholderExpression(context.session)) {
                    //         reporter.reportOn(
                    //             expression.source,
                    //             CaptureUpperArgErrors.FORBIDDEN_FUNCTION_WARNING,
                    //             "you should not pass placeholder here",
                    //             context
                    //         )
                    //     }
                    // }
                }

                if (hasCaptureParam) {
                    val callContext = checkCallContext(context)

                    when (callContext) {
                        CallContext.INIT_BLOCK -> reporter.reportOn(
                            expression.source,
                            HighlightErrorAndWarnings.CALL_IN_INIT_BLOCK,
                            HighlightMessageRender.callInInitBlock(callee.name.asString()),
                            context
                        )
                        CallContext.PROPERTY_INITIALIZER -> reporter.reportOn(
                            expression.source,
                            HighlightErrorAndWarnings.CALL_IN_PROPERTY_INIT,
                            HighlightMessageRender.callInPropertyInit(callee.name.asString()),
                            context
                        )
                        CallContext.TOP_LEVEL -> reporter.reportOn(
                            expression.source,
                            HighlightErrorAndWarnings.CALL_IN_TOP_LEVEL,
                            HighlightMessageRender.callInTopLevel(callee.name.asString()),
                            context
                        )
                        CallContext.LOCAL_FUNCTION -> reporter.reportOn(
                            expression.source,
                            HighlightErrorAndWarnings.CALL_IN_LOCAL_FUNCTION,
                            HighlightMessageRender.callInLocalFunction(callee.name.asString()),
                            context
                        )
                        else -> {}
                    }
                }
            }
        })
    }

    private fun FirExpression.isPlaceholderExpression(session: FirSession): Boolean {
        if (this !is FirPropertyAccessExpression) return false
        return toResolvedCallableSymbol(session)
            ?.callableId
            ?.asSingleFqName() == placeholderFqName
    }

    private fun checkCallContext(context: CheckerContext): CallContext {
        val stack = context.containingDeclarations
        if (stack.isEmpty()) return CallContext.TOP_LEVEL

        if (stack.any { it is FirAnonymousInitializer }) return CallContext.INIT_BLOCK

        val lastFunction = stack.lastOrNull { it is FirFunction }
        if (lastFunction != null) {
            if (lastFunction is FirAnonymousFunction) return CallContext.LAMBDA

            // if the current function is not a class member, then it is wrapped by another function, or it is local
            val functions = stack.filterIsInstance<FirFunction>()
            if (functions.size > 1 && lastFunction !is FirPropertyAccessor) return CallContext.LOCAL_FUNCTION
        }

        val lastPropertyIndex = stack.indexOfLast { it is FirProperty }
        if (lastPropertyIndex != -1) {
            val hasFunctionAfterProperty = stack.subList(lastPropertyIndex + 1, stack.size).any { it is FirFunction }
            if (!hasFunctionAfterProperty) {
                return CallContext.PROPERTY_INITIALIZER
            }
        }

        val hasMeaningfulDeclaration = stack.any {
            it is FirClass || it is FirFunction || it is FirAnonymousInitializer
        }
        if (!hasMeaningfulDeclaration) {
            return CallContext.TOP_LEVEL
        }

        return CallContext.NORMAL_FUNCTION
    }
}