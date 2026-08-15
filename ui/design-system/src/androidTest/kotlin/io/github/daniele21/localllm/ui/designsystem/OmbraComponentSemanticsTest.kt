package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class OmbraComponentSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hiddenFindingDoesNotRenderOrAnnounceSensitiveValue() {
        val sensitiveTestValue = "persona.test@example.invalid"
        composeRule.setContent {
            OmbraTheme {
                OmbraFindingInspector(
                    category = "Email",
                    positionLabel = "Occorrenza 1 di 3",
                    value = OmbraFindingDisplayValue.Hidden("[EMAIL_1]", "Valore nascosto"),
                    decision = OmbraFindingDecision.UNDECIDED,
                    decisionLabel = "Da decidere",
                    acceptLabel = "Accetta",
                    ignoreLabel = "Ignora",
                    previousLabel = "Precedente",
                    nextLabel = "Successiva",
                    onDecisionChange = {},
                    onPrevious = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithText(sensitiveTestValue, useUnmergedTree = true).assertDoesNotExist()
        composeRule
            .onNode(hasContentDescription("Email. Occorrenza 1 di 3. Valore nascosto. Da decidere"))
            .assertExists()
    }

    @Test
    fun decisionControlsExposeAndUpdateSelectionState() {
        composeRule.setContent {
            var decision by remember { mutableStateOf(OmbraFindingDecision.UNDECIDED) }
            OmbraTheme {
                OmbraDecisionControls(
                    decision = decision,
                    acceptLabel = "Accetta",
                    ignoreLabel = "Ignora",
                    onDecisionChange = { decision = it },
                )
            }
        }

        composeRule.onNodeWithText("Accetta").assertIsNotSelected().performClick().assertIsSelected()
        composeRule.onNodeWithText("Ignora").assertIsNotSelected().performClick().assertIsSelected()
        composeRule.onNodeWithText("Accetta").assertIsNotSelected()
    }
}
