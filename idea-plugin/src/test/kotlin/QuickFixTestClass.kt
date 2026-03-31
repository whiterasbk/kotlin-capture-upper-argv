package ink.iowoi.kotlin.idea.plugin.captureupperarg

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.Test

@RunWith(JUnit4::class)
class QuickFixTestClass : BaseTestClass() {

    @Test
    fun testAddPlaceholderFix() = doNormalQuickFixTest(
        beforeCode = "fun log(@$annotationName <caret>data: Map<String, Any>) {}",
        afterCode = "fun log(@$annotationName data: Map<String, Any> = $placeholderName) {}",
        fixText = QuickFixNames.ADDING_PLACEHOLDER
    )

    @Test
    fun testChangeKeyTypeFix() {
        doNormalQuickFixTest(
            beforeCode = "fun log(@$annotationName data: Map<<caret>Int, Any> = $placeholderName) {}",
            fixText = QuickFixNames.CHANGE_TYPE_TO_STRING,
            afterCode = "fun log(@$annotationName data: Map<String, Any> = $placeholderName) {}"
        )
        doNormalQuickFixTest(
            beforeCode = "fun <T>log(@$annotationName data: Map<<caret>T, Any> = $placeholderName) {}",
            afterCode = "fun <T>log(@$annotationName data: Map<String, Any> = $placeholderName) {}",
            fixText = QuickFixNames.CHANGE_TYPE_TO_STRING
        )
        doNormalQuickFixTest(
            beforeCode = "fun log(@$annotationName data: MutableMap<<caret>Int, Any> = $placeholderName) {}",
            afterCode = "fun log(@$annotationName data: MutableMap<String, Any> = $placeholderName) {}",
            fixText = QuickFixNames.CHANGE_TYPE_TO_STRING
        )
    }

    @Test
    fun testSetCollectFalseFix() = doNormalQuickFixTest(
        beforeCode = "fun log(@$annotationName<caret> data: Int = placeholder) {}",
        afterCode = "fun log(@$annotationName($cuaaCollectField = false) data: Int = $placeholderName) {}",
        fixText = QuickFixNames.SET_COLLECTION_EQ_FALSE
    )

    @Test
    fun testAutoImportFix() {
        val before = """
            import $annotationFqName

            fun log(@$annotationName <caret>data: Map<String, Any>) {}
        """.trimIndent()

        val after = """
            import $annotationFqName
            import $placeholderFqName
    
            fun log(@$annotationName data: Map<String, Any> = $placeholderName) {}
        """.trimIndent()

        myFixture.configureByText("Test.kt", before)
        val action = myFixture.findSingleIntention(QuickFixNames.ADDING_PLACEHOLDER)
        myFixture.launchAction(action)
        myFixture.checkResult(after)
    }
}