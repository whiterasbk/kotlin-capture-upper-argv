package ink.iowoi.kotlin.idea.plugin.captureupperarg

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaCompilerPluginDiagnostic1
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.setDefaultValue
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class QuickFixRegistrar : KotlinQuickFixRegistrar() {

    override val list: KotlinQuickFixesList =
        KtQuickFixesListBuilder.registerPsiQuickFix {
            registerFactory(KotlinQuickFixFactory.IntentionBased { diagnostic: KaCompilerPluginDiagnostic1 ->
                if (diagnostic.factoryName != HighlightErrorAndWarnings.MISSING_DEFAULT_VALUE.name) return@IntentionBased emptyList()

                listOf(object : PsiElementBaseIntentionAction() {
                    override fun invoke(p: Project, e: Editor?, psi: PsiElement) {
                        val param = psi.getParentOfType<KtParameter>(true) ?: return
                        val ktFile = param.containingKtFile
                        param.setDefaultValue(KtPsiFactory(p).createExpression(placeholderName))
                        ktFile.addImport(placeholderFqName)
                    }

                    override fun isAvailable(p: Project, e: Editor?, psi: PsiElement): Boolean {
                        return psi.getParentOfType<KtParameter>(true)?.defaultValue == null
                    }

                    override fun getFamilyName(): String = annotationName
                    override fun getText(): String = QuickFixNames.ADDING_PLACEHOLDER
                })
            })

            registerFactory(KotlinQuickFixFactory.IntentionBased { diagnostic: KaCompilerPluginDiagnostic1 ->
                if (diagnostic.factoryName != HighlightErrorAndWarnings.UNSUPPORTED_CONTAINER_KEY.name) return@IntentionBased emptyList()

                buildList {
                    add(object : PsiElementBaseIntentionAction() {
                        override fun invoke(p: Project, e: Editor?, psi: PsiElement) {
                            // looking up to nearest type projection
                            val typeProjection = psi.getParentOfType<KtTypeProjection>(true)

                            val argList = typeProjection?.getParentOfType<KtTypeArgumentList>(true)
                                ?: psi.getParentOfType<KtTypeArgumentList>(true)
                                ?: return

                            // find UserType which owns this type projection
                            val userType = argList.parent as? KtUserType ?: return

                            val arguments = argList.arguments
                            if (arguments.isEmpty()) return

                            val psiFactory = KtPsiFactory(p)

                            val remainingArgsText = arguments.drop(1).joinToString(", ") { it.text }

                            val newTypeExpr = if (remainingArgsText.isEmpty()) {
                                "${userType.referencedName}<String>"
                            } else {
                                "${userType.referencedName}<String, $remainingArgsText>"
                            }

                            val newType = psiFactory.createType(newTypeExpr)
                            userType.getParentOfType<KtTypeReference>(true)?.replace(newType)

                        }

                        override fun isAvailable(p: Project, e: Editor?, psi: PsiElement): Boolean {
                            return psi is KtTypeReference ||
                                    psi.getParentOfType<KtTypeReference>(true) != null
                        }

                        override fun getFamilyName(): String = annotationName
                        override fun getText(): String = QuickFixNames.CHANGE_TYPE_TO_STRING
                    })
                }
            })

            registerFactory(KotlinQuickFixFactory.IntentionBased { diagnostic: KaCompilerPluginDiagnostic1 ->
                if (diagnostic.factoryName != HighlightErrorAndWarnings.UNSUPPORTED_CONTAINER.name) return@IntentionBased emptyList()

                buildList {
                    add(object : PsiElementBaseIntentionAction() {
                        override fun invoke(p: Project, e: Editor?, psi: PsiElement) {
                            val parameter = psi.getParentOfType<KtParameter>(true) ?: return
                            val annotationEntry = parameter.annotationEntries.find {
                                it.shortName?.asString() == annotationName
                            } ?: return

                            val psiFactory = KtPsiFactory(p)
                            val valueArgumentList = annotationEntry.valueArgumentList

                            if (valueArgumentList == null) {
                                // case A: @CaptureUpperArg | @CaptureUpperArg() -> @CaptureUpperArg(collect = false)
                                annotationEntry.add(psiFactory.createCallArguments("($cuaaCollectField = false)"))
                            } else {
                                val arguments = valueArgumentList.arguments
                                val collectArg = arguments.find { it.getArgumentName()?.asName?.asString() == cuaaCollectField }

                                if (collectArg != null) {
                                    // there is already 'collect' argument, set 'collect' to false
                                    collectArg.getArgumentExpression()?.replace(psiFactory.createExpression("false"))
                                } else {
                                    // no 'collect' argument, append
                                    val newArg = psiFactory.createArgument(psiFactory.createExpression("false"), org.jetbrains.kotlin.name.Name.identifier(cuaaCollectField))
                                    valueArgumentList.addArgument(newArg)
                                }
                            }
                        }

                        override fun isAvailable(p: Project, e: Editor?, psi: PsiElement): Boolean {
                            val parameter = psi.getParentOfType<KtParameter>(true) ?: return false
                            return parameter.annotationEntries.any { it.shortName?.asString() == annotationName }
                        }

                        override fun getFamilyName(): String = annotationName
                        override fun getText(): String = QuickFixNames.SET_COLLECTION_EQ_FALSE
                    })

                    add(object : PsiElementBaseIntentionAction() {
                        override fun invoke(p: Project, e: Editor?, psi: PsiElement) {
                            val typeReference = psi as? KtTypeReference
                                ?: psi.getParentOfType<KtTypeReference>(true)
                                ?: return

                            val psiFactory = KtPsiFactory(p)

                            val newType = psiFactory.createType("Map<String, Any?>")

                            typeReference.replace(newType)
                        }

                        override fun isAvailable(p: Project, e: Editor?, psi: PsiElement): Boolean {
                            return psi is KtTypeReference ||
                                    psi.getParentOfType<KtTypeReference>(true) != null
                        }

                        override fun getFamilyName(): String = annotationName
                        override fun getText(): String = QuickFixNames.CHANGE_TYPE_TO_MAP_STRING_ANY
                    })
                }
            })
        }
}