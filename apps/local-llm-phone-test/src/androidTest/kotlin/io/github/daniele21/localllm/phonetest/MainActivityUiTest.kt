package io.github.daniele21.localllm.phonetest

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    fun compactShellKeepsBrandAndTaskFirstEntryPointVisible() {
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
        composeRule.onNodeWithText("Models").assertIsDisplayed()
        composeRule.onNodeWithText("Choose a model").assertIsDisplayed()
        composeRule.onNodeWithText("Device evidence").assertIsDisplayed()
        assertTextAbsent("All systems operational · ready for inference")
        assertTextAbsent("Run health check")
    }

    @Test
    fun primaryDestinationsAndSettingsRemainReachable() {
        composeRule.onNodeWithTag("nav-playground").performClick()
        composeRule.onNodeWithText("Runs entirely on this device").assertIsDisplayed()
        composeRule.onNodeWithText("how much is the earth radius?").assertIsDisplayed()

        composeRule.onNodeWithTag("nav-models").performClick()
        composeRule.onNodeWithText("Status").assertIsDisplayed()
        composeRule.onNodeWithText("Model size").assertIsDisplayed()
        composeRule.onNodeWithText("All sizes").assertIsDisplayed()
        composeRule.onNodeWithText("0.8B").assertIsDisplayed()
        composeRule.onNodeWithText("2B").assertIsDisplayed()
        assertTextAbsent("Inventory")
        assertTextAbsent("Model catalog")
        assertTextAbsent("Import model")

        composeRule.onNodeWithTag("nav-diagnostics").performClick()
        composeRule.onNodeWithText("Diagnostics").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()
        composeRule.onNodeWithText("PRIVACY").assertIsDisplayed()
        assertTextAbsent("Brand palette")
    }

    @Test
    fun playgroundRevealsAdvancedAndExpertControlsProgressively() {
        composeRule.onNodeWithTag("nav-playground").performClick()
        composeRule.onNodeWithTag("playground-advanced-toggle").assertIsDisplayed()
        assertTextAbsent("Seed policy")
        assertTagAbsent("playground-min-p")

        composeRule.onNodeWithTag("playground-advanced-toggle").performClick()
        composeRule.onNodeWithTag("playground-thinking-on").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-temperature-slider").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-top-p-slider").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-expert-toggle").assertIsDisplayed()
        assertTagAbsent("playground-min-p")

        composeRule.onNodeWithTag("playground-expert-toggle").performClick()
        composeRule.onNodeWithTag("playground-min-p").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-presence-penalty").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-repeat-penalty").assertIsDisplayed()
        composeRule.onNodeWithTag("playground-repeat-last-n").assertIsDisplayed()
        composeRule.onNodeWithText("Seed policy").assertIsDisplayed()
        composeRule.onNodeWithText("Random each run").assertIsDisplayed()
        composeRule.onNodeWithText("Fixed").assertIsDisplayed()
        composeRule.onNodeWithText("Context policy").assertIsDisplayed()
        composeRule.onNodeWithText("Auto").assertIsDisplayed()
    }

    private fun assertTextAbsent(text: String) {
        assertTrue(
            "$text must not be present in the UI",
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun assertTagAbsent(tag: String) {
        assertTrue(
            "$tag must not be present in the UI",
            composeRule.onAllNodesWithText(tag).fetchSemanticsNodes().isEmpty(),
        )
    }
}
