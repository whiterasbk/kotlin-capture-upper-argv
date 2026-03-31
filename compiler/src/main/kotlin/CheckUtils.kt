package ink.iowoi.kotlin.compiler.plugin.captureupperarg

fun buildW002WarningMessage(targetParam: String): String {
    return "${CaptureDiagnosticCode.W002} Parameter '$targetParam' is marked with @$annotationName " +
            "but has no default value. If the plugin fails to find a matching variable, " +
            "compilation will fail. Consider adding a default value (e.g., = \"arg = placeholder\")."
}

fun buildW005ErrorMessage(targetParamName: String, targetType: String): String {
    return "${CaptureDiagnosticCode.E005} Parameter '$targetParamName' has 'collect = true' but its type is $targetType, " +
            "which is not a supported Collection (Mutable?)(Map|List|Set)."
}

fun buildW006ErrorMessage(targetParamName: String, targetType: String): String {
    return """
        ${CaptureDiagnosticCode.E006}: Invalid container key type for '$targetParamName'.
        When using Map/MutableMap as a capture container, the Key type must be explicitly 'kotlin.String' 
        to serve as a valid identifier.
        [Expected]: kotlin.String
        [Found]: $targetType
    """.trimIndent()
}