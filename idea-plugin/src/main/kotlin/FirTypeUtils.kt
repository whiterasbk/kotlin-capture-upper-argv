package ink.iowoi.kotlin.idea.plugin.captureupperarg

import org.jetbrains.kotlin.fir.types.*

fun FirTypeRef.isSupportedCollection(): Boolean {
    return with(coneType) {
        isList || isMutableList || isMap || isMutableMap || isSet || isMutableSet
    }
}