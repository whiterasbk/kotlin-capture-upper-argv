package ink.iowoi.kotlin.compiler.plugin.captureupperarg

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.isSubtypeOf
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName


private val mapFqName by lazy { FqName(Map::class.qualifiedName!!) }
private val listFqName by lazy { FqName(List::class.qualifiedName!!) }
private val setFqName by lazy { FqName(Set::class.qualifiedName!!) }

fun IrType.isMapType(): Boolean
   = type.classFqName == mapFqName

fun IrType.isListType(): Boolean
    = type.classFqName == listFqName

fun IrType.isSetType(): Boolean
    = type.classFqName == setFqName

fun IrType.isMutableMapType(): Boolean
        = type.classFqName?.asString() == "kotlin.collections.MutableMap"

fun IrType.isMutableListType(): Boolean
        = type.classFqName?.asString() == "kotlin.collections.MutableList"

fun IrType.isMutableSetType(): Boolean
        = type.classFqName?.asString() == "kotlin.collections.MutableSet"


@OptIn(UnsafeDuringIrConstructionAPI::class)
fun isMapAndSubtypeOfMapType(type: IrType, context: IrPluginContext): Boolean
        = isSubtypeOfClass(type, Map::class.qualifiedName!!, context)

/**
 * use Class::class.qualifiedName to fetch full qualified name
 * do care the type parameters is matched or not
 */
fun isSubtypeOfClass(type: IrType, qualifiedName: String, context: IrPluginContext): Boolean {
    val fqName = FqName(qualifiedName)
    val classId = ClassId.topLevel(fqName)
    val symbol = context.referenceClass(classId) ?: return false

    // sample: MutableList<T> is subclass of List<T>
    return type.isSubtypeOfClass(symbol)
}

fun IrType.isSubTypeOf(type: IrType, context: IrPluginContext): Boolean {
    val typeSystem = IrTypeSystemContextImpl(context.irBuiltIns)
    return isSubtypeOf(type, typeSystem)
}


