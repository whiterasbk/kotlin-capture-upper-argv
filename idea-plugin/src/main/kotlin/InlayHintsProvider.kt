package ink.iowoi.kotlin.idea.plugin.captureupperarg

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.catchNearestCallTreeDefault
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.collectDefault
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.isLocal
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.parents

@OptIn(KaExperimentalApi::class)
class InlayHintsProvider : InlayHintsProviderFactory, InlayHintsProvider {

    companion object {
        const val PROVIDER_ID = IdePluginConfig.INLAY_PROVIDER_ID
        // const val OPTION_SHOW_VARIABLE = "capture.upper.arg.show.variable"
        // const val OPTION_SHOW_COLLECTION = "capture.upper.arg.show.collection"
    }

    private enum class ContainerType { Variable, Map, MutableMap, Set, MutableSet, List, MutableList }

    private data class AnnotationContext(
        val targetContainerName: String,
        val containerType: ContainerType?,
        val annotations: List<KaType>,
        val type: KaType?,
        val typeNullable: Boolean?,
        val exclude: List<String>,
        val catchNearestCallTree: Boolean,
    )

    override fun getSupportedLanguages(): Set<Language> = setOf(KotlinLanguage.INSTANCE)

    override fun getProvidersForLanguage(language: Language): List<InlayProviderInfo> {
        val info = getProviderInfo(language, PROVIDER_ID)
        return if (info != null) listOf(info) else emptyList()
    }

    override fun getProviderInfo(language: Language, providerId: String): InlayProviderInfo? {
        if (language !is KotlinLanguage || providerId != PROVIDER_ID) return null

        return InlayProviderInfo(
            provider = this,
            providerId = PROVIDER_ID,
            options = setOf(
                // InlayOptionInfo(OPTION_SHOW_VARIABLE, true, "Show variable captures"),
                // InlayOptionInfo(OPTION_SHOW_COLLECTION, true, "Show collection (Map/List/Set) captures")
            ),
            isEnabledByDefault = true,
            providerName = IdePluginConfig.INLAY_PROVIDER_NAME,
        )
    }

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        if (file !is KtFile) return null

        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                if (element !is KtCallExpression) return

                analyze(element) {

                    val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return
                    val injectionTasks = collectAnnotationContexts(resolvedCall)
                    if (injectionTasks.isEmpty()) return@analyze

                    val hintResults: List<String> = buildList {
                        for ((parameterSymbol, context) in injectionTasks) {
                            val matchedArgs = findPredictableCaptureArgs(element, context, resolvedCall.symbol, resolvedCall, parameterSymbol)

                            if (matchedArgs.isEmpty()) continue

                            val paramName = parameterSymbol.name.asString()

                            val displayText = when (context.containerType) {
                                ContainerType.Variable -> matchedArgs.first().name.asString()
                                ContainerType.Map -> "mapOf(${mapPairValueParameterNames(matchedArgs)})"
                                ContainerType.MutableMap -> "mutableMapOf(${mapPairValueParameterNames(matchedArgs)})"
                                ContainerType.List -> "listOf(${iterValueParameterNames(matchedArgs)})"
                                ContainerType.MutableList -> "mutableListOf(${iterValueParameterNames(matchedArgs)})"
                                ContainerType.Set -> "setOf(${iterValueParameterNames(matchedArgs)})"
                                ContainerType.MutableSet -> "mutableSetOf(${iterValueParameterNames(matchedArgs)})"
                                null -> continue
                            }

                            add("$paramName = $displayText")
                        }
                    }

                    if (hintResults.isNotEmpty()) {
                        val offset = element.valueArgumentList?.rightParenthesis?.textOffset ?: element.textRange.endOffset
                        sink.addPresentation(
                            InlineInlayPosition(offset, relatedToPrevious = true),
                            tooltip = IdePluginConfig.INLAY_PROVIDER_TOOLTIPS,
                            hintFormat = HintFormat.default
                        ) {

                            collapsibleList(
                                state = if (ApplicationManager.getApplication().isUnitTestMode) CollapseState.Expanded else CollapseState.Collapsed,
                                expandedState = {
                                    toggleButton {
                                        smartText(hintResults)
                                    }
                                },
                                collapsedState = {
                                    toggleButton {
                                        text(hintResults.joinToString(", "))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun mapPairValueParameterNames(matchedArgs: List<KaValueParameterSymbol>): String = matchedArgs.joinToString(", ") { "\"${it.name.asString()}\" to ${it.name.asString()}" }

    private fun iterValueParameterNames(matchedArgs: List<KaValueParameterSymbol>): String = matchedArgs.joinToString(", ") { it.name.asString() }

    private enum class HintSplitStrategy { BY_LENGTH, BY_ELEMENT }

    private fun PresentationTreeBuilder.smartText(
        hintResults: List<String>,
        separator: String = ", ",
        actionData: InlayActionData? = null,
        strategy: HintSplitStrategy = HintSplitStrategy.BY_LENGTH
    ) {
        if (hintResults.isEmpty()) return

        hintResults.forEachIndexed { index, result ->
            when (strategy) {
                HintSplitStrategy.BY_LENGTH -> {
                    // 策略：物理切分，确保每一段都 <= 30
                    if (result.length > 30) {
                        result.chunked(30).forEach { chunk ->
                            text(chunk, actionData)
                        }
                    } else {
                        text(result, actionData)
                    }
                }
                HintSplitStrategy.BY_ELEMENT -> {
                    // 策略：尊重原始元素，一个元素只生成一个节点
                    // 注意：如果此处 result.length > 30，IDE 内部会自动补上 "..."
                    text(result, actionData)
                }
            }

            // 渲染分隔符
            if (index < hintResults.size - 1) {
                text(separator)
            }
        }
    }

    private fun KaSession.findPredictableCaptureArgs(
        callElement: KtCallExpression,
        context: AnnotationContext,
        calleeSymbol: KaFunctionSymbol,
        resolvedCall: KaFunctionCall<*>,
        parameterSymbol: KaValueParameterSymbol,
    ): List<KaValueParameterSymbol> {

        val callerSymbol = if (!context.catchNearestCallTree) {
            callElement.parents
                .filterIsInstance<KtFunction>()
                .map { it.symbol as? KaFunctionSymbol }
                .firstOrNull { func ->
                    if (func == null) return@firstOrNull false
                    val name = func.name?.asString() ?: return@firstOrNull false
                    val isAnonymous = name == "<anonymous>" || name.contains("lambda")
                    !isAnonymous && !func.isLocal
                }
        } else {
            callElement.parents.filterIsInstance<KtFunction>().firstOrNull()?.symbol as? KaFunctionSymbol
        } ?: return emptyList()

        val targetType = if (context.containerType != ContainerType.Variable) {
            getFinalCaptureType(parameterSymbol, resolvedCall)
        } else {
            context.type
        } ?: return emptyList()
        val isRecursive = callerSymbol == calleeSymbol

        return callerSymbol.valueParameters.filter { param ->
            val paramName = param.name.asString()
            if (isRecursive && paramName == context.targetContainerName) return@filter false
            if (paramName in context.exclude) return@filter false
            if (!checkTypeMatchFir(param.returnType, targetType, targetType.isMarkedNullable)) return@filter false
            if (!checkAnnotationsSubsetFir(param, context.annotations)) return@filter false
            true
        }
    }

    private fun KaSession.checkTypeMatchFir(
        paramType: KaType,
        targetType: KaType,
        isNullableAllowed: Boolean
    ): Boolean {
        if (!isNullableAllowed && paramType.isMarkedNullable) return false

        return isDeepSubTypeOfFir(paramType, targetType)
    }

    private fun KaSession.isDeepSubTypeOfFir(param: KaType, target: KaType): Boolean {
        if (!param.isSubtypeOf(target)) return false

        val paramArgs = (param as? KaClassType)?.typeArguments ?: return true
        val targetArgs = (target as? KaClassType)?.typeArguments ?: return true

        if (paramArgs.size != targetArgs.size) return true

        for (i in paramArgs.indices) {
            val pArg = paramArgs[i].type ?: continue
            val tArg = targetArgs[i].type ?: continue

            if (targetArgs[i] is KaStarTypeProjection) continue
            if (paramArgs[i] is KaStarTypeProjection) return false

            val isTargetArgNullable = tArg.isMarkedNullable

            if (isTargetArgNullable) {
                if (!isDeepSubTypeOfFir(pArg, tArg)) return false
            } else {
                if (pArg.isMarkedNullable) return false
                if (!isDeepSubTypeOfFir(pArg, tArg)) return false
            }
        }
        return true
    }

    private fun checkAnnotationsSubsetFir(
        param: KaValueParameterSymbol,
        requiredAnnotations: List<KaType>
    ): Boolean {
        if (requiredAnnotations.isEmpty()) return true

        val paramAnnotationTypes: List<ClassId> = param.annotations.mapNotNull { it.classId }
        return requiredAnnotations.all { required ->
            paramAnnotationTypes.any { actual ->
                // actual.semanticallyEquals(required)
                actual == required.symbol?.classId
            }
        }
    }

    private fun KaSession.getFinalCaptureType(
        parameterSymbol: KaValueParameterSymbol,
        resolvedCall: KaFunctionCall<*>
    ): KaType {
        // 1. 获取声明时的原始类型 (例如 Map<String, FDASF>)
        val declaredType = parameterSymbol.returnType

        // 2. 获取调用处的泛型映射映射关系 (FDASF -> F)
        val typeArgsMap = resolvedCall.typeArgumentsMapping

        // 3. 执行替换逻辑
        val finalType = if (typeArgsMap.isNotEmpty()) {
            // 创建替换器并作用于原始类型
            val substitutor = createSubstitutor(typeArgsMap)
            substitutor.substitute(declaredType)
        } else {
            declaredType
        }

        // 4. 从最终类型中提取 Value 端的泛型实参
        return if (finalType is KaClassType) {

            // val kl = resolvedCall.symbol.valueParameters.last().returnType
            // (kl as? KaClassType)?.let {
            //     val a = it.typeArguments.last()
            // }

            // 拿到 Map 的第二个参数
            finalType.typeArguments.lastOrNull()?.type ?: finalType
        } else {
            finalType
        }
    }

    private fun KaSession.collectAnnotationContexts(resolvedCall: KaFunctionCall<*>): Map<KaValueParameterSymbol, AnnotationContext> {
        val result = mutableMapOf<KaValueParameterSymbol, AnnotationContext>()
        val symbol = resolvedCall.symbol

        val manuallyPassedParameters = resolvedCall.argumentMapping.values.map { it.symbol }

        for (parameterSymbol in symbol.valueParameters) {

            // must set default value and user did not pass argument manually
            if (parameterSymbol.hasDefaultValue && !manuallyPassedParameters.contains(parameterSymbol)) {

                val annotation = parameterSymbol.annotations.find {
                    it.classId?.asSingleFqName() == annotationFqName
                }

                if (annotation != null) {
                    val context = parseAnnotationContext(parameterSymbol, annotation)
                    result[parameterSymbol] = context
                }
            }
        }
        return result
    }

    private fun KaSession.parseAnnotationContext(parameterSymbol: KaValueParameterSymbol, annotation: KaAnnotation): AnnotationContext {

        val targetFirType = parameterSymbol.returnType
        val argMap = annotation.arguments.associate { it.name.asString() to it.expression }
        fun collectConstantValue(name: String): Any? = (argMap[name] as? KaAnnotationValue.ConstantValue)?.value?.value
        fun collectArrayValue(name: String): Collection<KaAnnotationValue>? = (argMap[name] as? KaAnnotationValue.ArrayValue)?.values

        val isCollectMode: Boolean = collectConstantValue(cuaaCollectField) as? Boolean ?: collectDefault
        val excludeArray = collectArrayValue(cuaaExcludeField)
            ?.filterIsInstance<KaAnnotationValue.ConstantValue>()
            ?.mapNotNull { it.value.value as? String }
        val annotationClassArray = collectArrayValue(cuaaAnnotationsField)
            ?.filterIsInstance<KaAnnotationValue.ClassLiteralValue>()
            ?.map { it.type }
            ?.filter { it.isSubtypeOf(StandardClassIds.Annotation) }

        val derivedContainerType: ContainerType? = if (!isCollectMode) ContainerType.Variable else {
            when {
                targetFirType.isClassType(StandardClassIds.Map) -> ContainerType.Map
                targetFirType.isClassType(StandardClassIds.Set) -> ContainerType.Set
                targetFirType.isClassType(StandardClassIds.List) -> ContainerType.List
                targetFirType.isClassType(StandardClassIds.MutableMap) -> ContainerType.MutableMap
                targetFirType.isClassType(StandardClassIds.MutableSet) -> ContainerType.MutableSet
                targetFirType.isClassType(StandardClassIds.MutableList) -> ContainerType.MutableList
                else -> null
            }
        }

        return AnnotationContext(
            targetContainerName = parameterSymbol.name.asString(),
            containerType = derivedContainerType,
            type = parameterSymbol.returnType,
            typeNullable = parameterSymbol.returnType.isMarkedNullable,
            exclude = excludeArray ?: emptyList(),
            annotations = annotationClassArray ?: emptyList(),
            catchNearestCallTree = collectConstantValue(cuaaCatchNearestCallTreeField) as? Boolean ?: catchNearestCallTreeDefault
        )
    }

}