package ink.iowoi.kotlin.idea.plugin.captureupperarg

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

fun FirAnnotation.findArgumentByName(name: String): FirExpression? {
    return argumentMapping.mapping[Name.identifier(name)]
}

fun FirAnnotation.getBooleanValue(name: String, session: FirSession, default: Boolean = true): Boolean {
    val argument = findArgumentByName(name) ?: return default
    return argument.evaluateAs<FirLiteralExpression>(session)?.value as? Boolean ?: return default
}


fun FirAnnotation.getStringArrayValue(name: String, session: FirSession): List<String> {
    val argument = findArgumentByName(name) ?: return emptyList()
    return argument.evaluateAs<FirArrayLiteral>(session)
        ?.arguments
        ?.mapNotNull {
            it.evaluateAs<FirLiteralExpression>(session)?.value as? String
        } ?: emptyList()
}

fun FirAnnotation.getClassIdArrayValue(name: String, session: FirSession): List<ClassId> {
    val argument = findArgumentByName(name) ?: return emptyList()
    return argument.evaluateAs<FirArrayLiteral>(session)
        ?.arguments
        ?.mapNotNull {
            it.evaluateAs<FirGetClassCall>(session)
                ?.argument
                ?.evaluateAs<FirResolvedQualifier>(session)
                ?.classId
        } ?: emptyList()
}