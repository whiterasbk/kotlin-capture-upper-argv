package ink.iowoi.kotlin.idea.plugin.captureupperarg

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1

object HighlightMessageRender {

    fun callInInitBlock(funcName: String, withCapital: Boolean = false) =
        "${HighlightErrorAndWarnings.CALL_IN_INIT_BLOCK.buildCapital(withCapital)}The function '$funcName' " +
                "with @$annotationName cannot be called inside an 'init' block. " +
                "Init blocks do not have a parameter list to capture from."

    fun callInPropertyInit(funcName: String, withCapital: Boolean = false) =
        "${HighlightErrorAndWarnings.CALL_IN_PROPERTY_INIT.buildCapital(withCapital)}The function '$funcName' " +
                "with @$annotationName cannot be called in the initializer of a property. " +
                "Move this call into a function."

    fun callInTopLevel(funcName: String, withCapital: Boolean = false) =
        "${HighlightErrorAndWarnings.CALL_IN_TOP_LEVEL.buildCapital(withCapital)}The function '$funcName' " +
                "with @$annotationName cannot be called in the top-level scope. " +
                "Move this call into a function."

    fun callInLocalFunction(funcName: String, withCapital: Boolean = false) =
        "${HighlightErrorAndWarnings.CALL_IN_LOCAL_FUNCTION.buildCapital(withCapital)}The function '$funcName' " +
                "with @$annotationName inside a Local Function is discouraged. The compiler may generate synthetic " +
                "parameters that conflict with automatic capturing. Consider passing arguments explicitly."

    fun unsupportedContainer(paramName: String, typeRender: String, withCapital: Boolean = false) =
        "${HighlightErrorAndWarnings.UNSUPPORTED_CONTAINER.buildCapital(withCapital)}Parameter annotation of '$paramName' " +
                "has 'collect = true' but its type is $typeRender, " +
                "which is not a supported Collection (Mutable)(Map/List/Set)."

    fun unsupportedContainerKey(paramName: String, actualType: String, withCapital: Boolean = false) =
        "${HighlightErrorAndWarnings.UNSUPPORTED_CONTAINER_KEY.buildCapital(withCapital)}Type of Map key for parameter '$paramName' " +
                "is $actualType, but @$annotationName only supports 'String' keys for automatic capturing."

    fun missingDefault(paramName: String, withCapital: Boolean = false) =
        "${HighlightErrorAndWarnings.MISSING_DEFAULT_VALUE.buildCapital(withCapital)}Parameter '$paramName' " +
                "is marked with @$annotationName but has no default value. " +
                "If the plugin fails to find a matching variable, compilation will fail. " +
                "Consider adding a default value (e.g., '= placeholder')."

    fun passingPlaceholder(paramName: String, withCapital: Boolean = false) =
        "${HighlightErrorAndWarnings.PASSING_PLACEHOLDER.buildCapital(withCapital)}Cannot find a matching variable for parameter '$paramName'. " +
                "The explicit 'placeholder' requires a valid context variable to be captured, using default value will cause failure."

    private fun KtDiagnosticFactory1<String>.buildCapital(flag: Boolean): String = if (flag) "[" + this.name + "] " else ""
}