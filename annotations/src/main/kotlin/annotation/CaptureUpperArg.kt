package ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotation

import ink.iowoi.kotlin.compiler.plugin.captureupperarg.catchNearestCallTreeDefault
import ink.iowoi.kotlin.compiler.plugin.captureupperarg.collectDefault
import kotlin.reflect.KClass

/**
 * Marks a value parameter to be automatically populated by the compiler plugin
 * by capturing matching arguments from the upper invocation context.
 *
 * When the compiler plugin encounters a parameter with this annotation, it performs
 * static analysis of the call stack (IR level) to find matching variables and
 * injects them into the call site, removing the need for **explicit** passing.
 *
 * ### Compiler Behavior
 * 1. **IR Transformation**: Operates during the Intermediate Representation stage to rewrite the call site.
 * 2. **Scope Resolution**: Traverses the scopes based on [catchNearestCallTree] to resolve dependencies.
 * 3. **Predicate Filtering**: Matches candidates by their type and specified [annotations].
 *
 * ### Usage Sample
 * ```kotlin
 * // 1. Define a function with the annotation
 * fun logInfo(@CaptureUpperArg traceId: String = placeholder) {
 *     println("Trace: $traceId")
 * }
 *
 * // 2. Call it within a scope where a matching type exists
 * fun serviceLayer(traceId: String) {
 *     // The plugin injects 'traceId' automatically here
 *     logInfo()
 * }
 * ```
 *
 * @property annotations Filters candidates; only parameters marked with all these [annotations] will be captured.
 * @property exclude Prevents specific variables from being captured if their names match any string in [exclude].
 * @property collect If `true`, the plugin aggregates all matching candidates into a collection.
 * Only supports [List], [Set], [Map], and their mutable variants. Defaults to [collectDefault].
 * @property catchNearestCallTree Defines the lookup strategy:
 * - `true`: Captures from the most immediate surrounding scope (including local closures).
 * - `false`: Searches for the nearest named, non-local scope to capture arguments.
 * Defaults to [catchNearestCallTreeDefault].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class CaptureUpperArg(
    val annotations: Array<KClass<out Annotation>> = [],
    val exclude: Array<String> = [],
    val collect: Boolean = collectDefault,
    val catchNearestCallTree: Boolean = catchNearestCallTreeDefault
)