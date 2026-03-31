package ink.iowoi.kotlin.idea.plugin.captureupperarg

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class InlayHintsTestClass : BaseTestClass() {

    @Test
    fun `test capture upper argument basic hint type filter`() = testInlayHint("""
        fun business(a: Int, b: String) = log(/*<# data = listOf(a) #>*/)
        fun log(@$annotationName data: List<Int> = $placeholderName) {}
    """)

    @Test
    fun `test capture upper argument basic hint none collect mode`() = testInlayHint("""
        fun business(a: Int, b: String, c: Long) = log(/*<# data = c #>*/)
        fun log(@$annotationName($cuaaCollectField = false) data: Long = $placeholderName) {}
    """)

    @Test
    fun `test capture upper argument basic hint determine catch type`() = testInlayHint("""
        fun business(a: Int, b: List<Int>, c: List<String>) = log(/*<# data = b #>*/)
        fun log(@$annotationName($cuaaCollectField = false) data: List<Int> = $placeholderName) {}
    """)

    @Test
    fun `test capture upper argument basic hint nullable filter`() = testInlayHint("""
        fun business1(a: Int, b: Int?) = log1(/*<# data = listOf(a, b) #>*/)
        fun log1(@$annotationName data: List<Int?> = $placeholderName) {}
        fun business2(a: Int, b: Int?) = log2(/*<# data = listOf(a) #>*/)
        fun log2(@$annotationName data: List<Int> = $placeholderName) {}
    """)

    @Test
    fun `test capture upper argument hint with one specific annotation`() = testInlayHint("""
        annotation class Sensitive

        class Test {
            fun transfer(@Sensitive token: String, normalInfo: String) {
                secureCall(/*<# secret = listOf(token) #>*/)
            }
    
            fun secureCall(@$annotationName($cuaaAnnotationsField = [Sensitive::class]) secret: List<String> = $placeholderName) {}
        }
    """)

    @Test
    fun `test capture upper argument hint with multi specific annotations`() = testInlayHint("""
        annotation class Sensitive
        annotation class Qualifier

        class Test {
            fun transfer(@Sensitive @Qualifier token: String, @Sensitive normalInfo: String, @Qualifier cjk: String, mmd: String) {
                secureCall(/*<# secret = listOf(token) #>*/)
            }
    
            fun secureCall(@$annotationName($cuaaAnnotationsField = [Sensitive::class, Qualifier::class]) secret: List<String> = $placeholderName) {}
        }
    """)

    @Test
    fun `test complex map and nesting type matching`() = testInlayHint("""
        fun business(b: Map<String, Int>, c: Map<String, Int?>, d: Map<String, Long>) {
            log(b, c, d/*<# data0 = mapOf("b" to b, "c" to c, "d" to d), data1 = listOf(b, d), data3 = listOf(b, c) #>*/)
        }

        fun log(
            b: Map<String, Int>, c: Map<String, Int?>, d: Map<String, Long>,
            @$annotationName data0: Map<String, Any?> = $placeholderName,
            @$annotationName data1: List<Map<String, Any>> = $placeholderName,
            @$annotationName data3: List<Map<String, Int?>> = $placeholderName
        ) {}
    """)

    @Test
    fun `test deep nesting and covariance`() = testInlayHint("""
        open class Q; class LLk : Q()

        fun test(f: Map<Int, Map<Int, String>>, i: Map<Int, Map<Int, LLk>>) {
            sink(f, i/*<# data4 = listOf(f), data5 = listOf(i) #>*/)
        }

        fun sink(
            f: Map<Int, Map<Int, String>>, i: Map<Int, Map<Int, LLk>>,
            @$annotationName data4: List<Map<Int, Map<Int, String>>> = $placeholderName,
            @$annotationName data5: List<Map<Int, Map<Int, Q>>> = $placeholderName
        ) {}
    """)

    @Test
    fun `test annotation attributes collect and exclude`() = testInlayHint("""
        fun context(s: String, name: String, age: Int) {
            w2(name/*<# info = s, argv = listOf(name, age) #>*/)
        }

        fun w2(
            some: String, 
            @$annotationName($cuaaCollectField = false) info: String = $placeholderName, 
            @$annotationName($cuaaExcludeField = ["s"]) argv: List<Any> = $placeholderName
        ) {}
    """)

    @Test
    fun `test generics function bounds`() = testInlayHint("""
        interface Gyua<T> 
        fun <T: Gyua<Int>> callGeneric(f: T) {
            ww(f/*<# a = mapOf("f" to f) #>*/)
        }
        
        fun <TF: Gyua<B>, B> ww(l: TF, @CaptureUpperArg a: Map<String, TF> = $placeholderName) {}
    """)

    @Test
    fun `test non-local scopes and lambdas`() = testInlayHint("""
        fun outer(x: Int) {
            val lambda = { j: Int ->
                val y = 20
                base1(/*<# a = listOf(j), b = listOf(x) #>*/)
            }
            
            fun local(z: Int) {
                base1(/*<# a = listOf(z), b = listOf(x) #>*/)
            }
        }

        fun base1(
            @$annotationName($cuaaCatchNearestCallTreeField = true) a: List<Int> = $placeholderName, 
            @$annotationName($cuaaCatchNearestCallTreeField = false) b: List<Int> = $placeholderName,
        ) {}
    """)

    @Test
    fun `test property initializers and init blocks`() = testInlayHint("""
        class A(val constructorParam: Int) {
            val prop = base1() // NO_HINTS

            init {
                val localInInit = 50
                base1() // NO_HINTS
            }
        }

        fun base1(@$annotationName a: List<Map<String, Int>> = $placeholderName) {}
    """, false)


}