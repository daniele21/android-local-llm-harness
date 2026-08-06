package io.github.daniele21.localllm.phonetest

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun compactShellKeepsBrandAndPrimaryActionVisible() {
        val topBarHeight = composeRule.onNodeWithTag("harnessTopBar").fetchSemanticsNode().boundsInRoot.height
        val topBarHeightDp = with(composeRule.density) { topBarHeight.toDp() }
        val maximumHeight = 120.dp
        assertTrue(
            "Top app bar (including the status-bar inset) must stay within $maximumHeight, but was $topBarHeightDp",
            topBarHeightDp <= maximumHeight,
        )
        composeRule.onNodeWithText("Harness").assertIsDisplayed()
        composeRule.onNodeWithText("Local AI Console").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-overview").assertIsDisplayed()
        composeRule.onNodeWithText("Import model").assertIsDisplayed()
        composeRule.onNodeWithText("Device resources").assertIsDisplayed()
    }

    @Test
    fun primaryDestinationsAndSettingsRemainReachable() {
        composeRule.onNodeWithTag("nav-playground").performClick()
        composeRule.onNodeWithText("Runs entirely on this device").assertIsDisplayed()

        composeRule.onNodeWithTag("nav-models").performClick()
        composeRule.onNodeWithText("Model catalog").assertIsDisplayed()

        composeRule.onNodeWithTag("nav-diagnostics").performClick()
        composeRule.onNodeWithText("Overall health").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()
        composeRule.onNodeWithText("PRIVACY").assertIsDisplayed()
    }

    @Test
    fun playgroundGenerationControlsExposeAccessibleExplicitPolicies() {
        composeRule.onNodeWithTag("nav-playground").performClick()
        composeRule.onNodeWithText("Generation settings  ·  Show").performClick()

        composeRule.onNodeWithTag("playground-temperature-slider").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-top-p-slider").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-repeat-penalty").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-repeat-last-n").assertIsDisplayed()
        composeRule.onNodeWithText("Seed policy").assertIsDisplayed()
        composeRule.onNodeWithText("Random each run").assertIsDisplayed()
        composeRule.onNodeWithText("Fixed").assertIsDisplayed()
        composeRule.onNodeWithText("Context policy").assertIsDisplayed()
        composeRule.onNodeWithText("Auto").assertIsDisplayed()
    }
}
