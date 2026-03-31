package ink.iowoi.kotlin.idea.plugin.captureupperarg

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.Test

@RunWith(JUnit4::class)
class HighlightCheckerTestClass : BaseTestClass() {

    @Test
    fun testCallContexts() = doHighlightCheckTest("""
            class MyService {
                val p = ${"log".asErrorMarkup(HighlightMessageRender.callInPropertyInit("log", true))}()
                
                init {
                    ${"log".asErrorMarkup(HighlightMessageRender.callInInitBlock("log", true))}()
                }
                
                fun member(a: Int) = log()
            }
            
            fun log(@$annotationName data: Map<String, Any> = $placeholderName) {}
    """)

    @Test
    fun testTopLevelAndLocalFunction() = doHighlightCheckTest("""
            val topCall = ${"log".asErrorMarkup(HighlightMessageRender.callInPropertyInit("log", true))}()

            fun outer() {
                fun local() {
                    ${"log".asWarningMarkup(HighlightMessageRender.callInLocalFunction("log", true))}()
                }
            }

            fun log(@$annotationName data: Map<String, Any> = $placeholderName) {}
    """)

    @Test
    fun testDeclarationErrors() = doHighlightCheckTest("""
            fun f1(@$annotationName data:${"Int".asErrorMarkup(HighlightMessageRender.unsupportedContainer("data", "Int", true))} = $placeholderName) {}

            fun f2(@$annotationName options:${"Map<Int, Any>".asErrorMarkup(HighlightMessageRender.unsupportedContainerKey("options", "Int", true))} = $placeholderName) {}

            fun f3(@$annotationName ${"context".asWarningMarkup(HighlightMessageRender.missingDefault("context", true))}: Map<String, Any>) {}
    """)

    @Test
    fun testPlaceholderAbuse() = doHighlightCheckTest("""
            fun service() {
                log(${"placeholder".asErrorMarkup(HighlightMessageRender.passingPlaceholder("data", true))})
            }

            fun log(@$annotationName data: Map<String, Any> = $placeholderName) {}
    """)

    @Test
    fun testImplicitPlaceholderFailure() = doHighlightCheckTest("""
            class TestService {
                fun fail() {
                    log1${"()".asErrorMarkup(HighlightMessageRender.passingPlaceholder("data", true))}
                }
    
                fun success() {
                    log2() // will pass null
                    log3() // will pass empty list
                }
            }

            fun log1(@$annotationName($cuaaCollectField = false) data: String = $placeholderName) {}
            fun log2(@$annotationName($cuaaCollectField = false) data: String? = $placeholderName) {}
            fun log3(@$annotationName data: List<String> = $placeholderName) {}
    """)

    private fun String.asErrorMarkup(desc: String): String {
        return """<error descr="$desc">$this</error>"""
    }

    private fun String.asWarningMarkup(desc: String): String {
        return """<warning descr="$desc">$this</warning>"""
    }
}