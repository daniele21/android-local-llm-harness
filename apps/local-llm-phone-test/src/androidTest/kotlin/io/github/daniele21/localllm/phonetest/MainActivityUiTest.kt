package io.github.daniele21.localllm.phonetest

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
        awaitText("Choose a model")
        composeRule.onNodeWithText("Choose a model").assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("0.8B").assertIsDisplayed()
        composeRule.onNodeWithText("2B").assertIsDisplayed()
        composeRule.onNodeWithText("4B").assertIsDisplayed()
        composeRule.onNodeWithText("Filter").assertIsDisplayed()
        assertTextAbsent("Inventory")
        assertTextAbsent("Model catalog")
        assertTextAbsent("Import model")
        assertTextAbsent("Model size")

        composeRule.onNodeWithTag("nav-diagnostics").performClick()
        composeRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("Physical validation").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()
        composeRule.onNodeWithText("PRIVACY").assertIsDisplayed()
        assertTextAbsent("Brand palette")
    }

    @Test
    fun modelsTierProgressivelyDisclosesQuantizationVariants() {
        composeRule.onNodeWithTag("nav-models").performClick()
        awaitText("Choose a model")

        composeRule.onNodeWithText("4B").performClick()
        awaitText("6 other variants")
        composeRule.onNodeWithText("Qwen3.5 · 4B").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("UD-Q4_K_XL").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Recommended").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("6 other variants").performScrollTo().assertIsDisplayed()
        assertTextAbsent("Q4_K_M")
        holdForMediaEvidence()
        captureModelsEvidence("collapsed")

        composeRule.onNodeWithText("6 other variants").performClick()
        composeRule.onNodeWithText("Q4_K_M").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Show fewer").performScrollTo().assertIsDisplayed()
        holdForMediaEvidence()
        captureModelsEvidence("expanded")

        composeRule.onNodeWithText("Show fewer").performClick()
        awaitText("6 other variants")
        assertTextAbsent("Q4_K_M")
        holdForMediaEvidence()
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

    @Test
    fun diagnosticsUsesOverviewThenDeterministicDrillDown() {
        composeRule.onNodeWithTag("nav-diagnostics").performClick()
        composeRule.onNodeWithText("Health").performClick()
        composeRule.onNodeWithText("Overall health").assertIsDisplayed()
        composeRule.onNodeWithText("Back to diagnostics").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Physical validation").assertIsDisplayed()
        assertTextAbsent("Back to diagnostics")
    }

    @Test
    fun performanceHistoryAlwaysStatesWhetherEvidenceSupportsADecision() {
        composeRule.onNodeWithTag("nav-performance").performClick()
        composeRule.onNodeWithText("Performance").assertIsDisplayed()
        composeRule.onNodeWithTag("performance-section-history").performClick()

        assertAnyTextPresent(
            "Decision evidence is loading",
            "No supported choice can be made",
            "No supported choice yet",
            "Recorded runs are not enough to rank choices",
        )
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun holdForMediaEvidence() {
        composeRule.waitForIdle()
        SystemClock.sleep(1_000)
    }

    private fun captureModelsEvidence(name: String) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val evidenceDir = File(requireNotNull(targetContext.getExternalFilesDir(null)), "ui-evidence/models")
        if (!evidenceDir.exists()) {
            check(evidenceDir.mkdirs()) { "Unable to create Models UI evidence directory" }
        }
        val output = File(evidenceDir, "$name.png")
        output.outputStream().use { stream ->
            check(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to write Models UI evidence $name"
            }
        }
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
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun assertAnyTextPresent(vararg options: String) {
        assertTrue(
            "One source-backed decision state must be present: ${options.joinToString()}",
            options.any { option ->
                composeRule.onAllNodesWithText(option).fetchSemanticsNodes().isNotEmpty()
            },
        )
    }
}
