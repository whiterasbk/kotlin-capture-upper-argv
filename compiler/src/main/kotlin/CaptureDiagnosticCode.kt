package ink.iowoi.kotlin.compiler.plugin.captureupperarg

enum class CaptureDiagnosticCode(val id: String) {
    E001("CALL_IN_INIT_BLOCK"),
    E002("CALL_IN_PROPERTY_INITIALIZER"),
    E003("CALL_AT_TOP_LEVEL"), // maybe uncatchable

    E004("MATCHING_VARIABLE_NOT_FOUND"),
    E005("UNSUPPORTED_COLLECTION_TYPE"),
    E006("UNSUPPORTED_COLLECTION_KEY_TYPE"),

    W001("LOCAL_FUNCTION_USAGE_DISCOURAGED"),
    W002("NO_DEFAULT_VALUE_DECLARATION");

    override fun toString(): String = "[$id]"
}