package ink.iowoi.kotlin.compiler.plugin.captureupperarg

import ink.iowoi.kotlin.compiler.plugin.captureupperarg.annotation.CaptureUpperArg
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal val cuaaCollectField: String = CaptureUpperArg::collect.name
internal val cuaaCatchNearestCallTreeField: String = CaptureUpperArg::catchNearestCallTree.name
internal val cuaaExcludeField: String = CaptureUpperArg::exclude.name
internal val cuaaAnnotationsField: String = CaptureUpperArg::annotations.name
internal val annotationName: String = CaptureUpperArg::class.simpleName!!
internal const val placeholderName: String = "placeholder"
internal val annotationFqName: FqName = FqName(CaptureUpperArg::class.qualifiedName!!)
internal val placeholderFqName: FqName =
    annotationFqName.parent().parent()
        .child(Name.identifier("utils"))
        .child(Name.identifier(placeholderName))