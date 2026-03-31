package ink.iowoi.kotlin.idea.plugin.captureupperarg

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.*

object HighlightErrorAndWarnings {

    val UNSUPPORTED_CONTAINER by error1<PsiElement, String>(
        SourceElementPositioningStrategies.DECLARATION_RETURN_TYPE
    )

    val UNSUPPORTED_CONTAINER_KEY by error1<PsiElement, String>(
        SourceElementPositioningStrategies.DECLARATION_RETURN_TYPE
    )

    val MISSING_DEFAULT_VALUE by warning1<PsiElement, String>(
        SourceElementPositioningStrategies.DECLARATION_NAME
    )

    val PASSING_PLACEHOLDER by error1<PsiElement, String>(
        SourceElementPositioningStrategies.DEFAULT
    )

    val CALL_IN_INIT_BLOCK by error1<PsiElement, String>(
        SourceElementPositioningStrategies.REFERENCED_NAME_BY_QUALIFIED
    )

    val CALL_IN_PROPERTY_INIT by error1<PsiElement, String>(
        SourceElementPositioningStrategies.REFERENCED_NAME_BY_QUALIFIED
    )

    val CALL_IN_LOCAL_FUNCTION by warning1<PsiElement, String>(
        SourceElementPositioningStrategies.REFERENCED_NAME_BY_QUALIFIED
    )

    val CALL_IN_TOP_LEVEL by error1<PsiElement, String>(
        SourceElementPositioningStrategies.REFERENCED_NAME_BY_QUALIFIED
    )
}