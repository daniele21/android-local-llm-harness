package io.github.daniele21.localllm.console.ombra

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSource
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.console.presentation.OmbraReviewPresentationResult
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjectionResult
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjectionSession
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjector
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import io.github.daniele21.localllm.console.redaction.ReviewOccurrence
import io.github.daniele21.localllm.ui.designsystem.OmbraStatusTone
import io.github.daniele21.localllm.ui.designsystem.OmbraTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OmbraReviewScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun configureRequestedOrientation() {
        val requestedOrientation =
            when (val orientation = InstrumentationRegistry.getArguments().getString(ORIENTATION_ARGUMENT, ORIENTATION_PORTRAIT)) {
                ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> error("Unsupported OMBRA test orientation: $orientation")
            }
        val expectedOrientation =
            when (requestedOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
                else -> error("Unsupported requested orientation: $requestedOrientation")
            }

        composeRule.activity.requestedOrientation = requestedOrientation
        composeRule.waitUntil(timeoutMillis = ORIENTATION_TIMEOUT_MILLIS) {
            composeRule.activity.resources.configuration.orientation == expectedOrientation
        }
    }

    @Test
    fun hiddenReviewDoesNotExposeSensitiveSurfaceToSemantics() {
        val surface = "alice@example.test"
        val fixture = singleEmailFixture(surface, ReviewDecisionState.PENDING)
        val presentation = (fixture.session.present() as OmbraReviewPresentationResult.Ready).model

        setReviewContent(OmbraReviewUiState.Ready(presentation, selectedIndex = 0))

        composeRule.onAllNodes(
            hasContentDescription(surface, substring = true),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        composeRule.onNodeWithText("Contatta [EMAIL_1]", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Mostra valore").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Esporta PDF").assertIsNotEnabled()
    }

    @Test
    fun explicitRevealAddsOnlyTheSelectedSensitiveSurfaceToSemantics() {
        val surface = "alice@example.test"
        val fixture = singleEmailFixture(surface, ReviewDecisionState.ACCEPTED)
        val ready = fixture.session.present(fixture.occurrences.single().id) as OmbraReviewPresentationResult.Ready
        val presentation = ready.model

        setReviewContent(OmbraReviewUiState.Ready(presentation, selectedIndex = 0))

        composeRule.onAllNodes(
            hasContentDescription(surface, substring = true),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithText("Nascondi valore").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Esporta PDF").assertIsEnabled()
    }

    @Test
    fun unresolvedOverlapIsAnnouncedAndBlocksExport() {
        val segment = segment("ABCD")
        val first = definition("first", "Primo")
        val second = definition("second", "Secondo")
        val occurrences = listOf(
            occurrence(segment, first.id, "ABC", start = 0, end = 3, ReviewDecisionState.ACCEPTED),
            occurrence(segment, second.id, "BCD", start = 1, end = 4, ReviewDecisionState.ACCEPTED),
        )
        val session = readySession(listOf(segment), listOf(first, second), occurrences)
        val presentation = (session.present() as OmbraReviewPresentationResult.Ready).model

        setReviewContent(OmbraReviewUiState.Ready(presentation, selectedIndex = 0))

        composeRule.onNodeWithText("Conflitti da risolvere").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Esporta PDF").assertIsNotEnabled()
    }

    @Test
    fun zeroPiiReviewRemainsAValidExportPath() {
        val segment = segment("Documento sintetico senza dati sensibili")
        val session = readySession(listOf(segment), definitions = emptyList(), occurrences = emptyList())
        val presentation = (session.present() as OmbraReviewPresentationResult.Ready).model

        setReviewContent(OmbraReviewUiState.Empty(presentation))

        composeRule.onNodeWithText("Nessun dato sensibile rilevato").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Esporta PDF").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun acceptedReviewKeepsPrimaryActionReachableAtTwoHundredPercentFontScale() {
        val fixture = singleEmailFixture("alice@example.test", ReviewDecisionState.ACCEPTED)
        val presentation = (fixture.session.present() as OmbraReviewPresentationResult.Ready).model

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 2f),
            ) {
                OmbraTheme {
                    OmbraReviewScreen(
                        state = OmbraReviewUiState.Ready(presentation, selectedIndex = 0),
                        harness = ReadyHarness,
                        onPrepareReview = {},
                        onMove = {},
                        onToggleReveal = {},
                        onDecision = {},
                        onExport = {},
                        onReset = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Esporta PDF").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun reviewResetActionInvokesFreshDocumentBoundary() {
        val fixture = singleEmailFixture("alice@example.test", ReviewDecisionState.ACCEPTED)
        val presentation = (fixture.session.present() as OmbraReviewPresentationResult.Ready).model
        var resetCalls = 0

        composeRule.setContent {
            OmbraTheme {
                OmbraReviewScreen(
                    state = OmbraReviewUiState.Ready(presentation, selectedIndex = 0),
                    harness = ReadyHarness,
                    onPrepareReview = {},
                    onMove = {},
                    onToggleReveal = {},
                    onDecision = {},
                    onExport = {},
                    onReset = { resetCalls += 1 },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Nuovo PDF").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, resetCalls) }
    }

    @Test
    fun exportProgressCancellationActionRemainsReachable() {
        var cancelCalls = 0

        composeRule.setContent {
            OmbraTheme {
                OmbraExportProgressScreen(
                    harness = ReadyHarness,
                    onCancel = { cancelCalls += 1 },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Annulla").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, cancelCalls) }
    }

    private fun setReviewContent(state: OmbraReviewUiState) {
        composeRule.setContent {
            OmbraTheme {
                OmbraReviewScreen(
                    state = state,
                    harness = ReadyHarness,
                    onPrepareReview = {},
                    onMove = {},
                    onToggleReveal = {},
                    onDecision = {},
                    onExport = {},
                    onReset = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun singleEmailFixture(surface: String, decision: ReviewDecisionState): ReviewFixture {
        val text = "Contatta $surface"
        val segment = segment(text)
        val email = definition("email", "Email")
        val start = text.indexOf(surface)
        val occurrences = listOf(
            occurrence(
                segment = segment,
                typeId = email.id,
                surface = surface,
                start = start,
                end = start + surface.length,
                decision = decision,
            ),
        )
        return ReviewFixture(
            session = readySession(listOf(segment), listOf(email), occurrences),
            occurrences = occurrences,
        )
    }

    private fun readySession(
        segments: List<DocumentSegment>,
        definitions: List<PiiDefinition>,
        occurrences: List<ReviewOccurrence>,
    ): OmbraReviewProjectionSession =
        (OmbraReviewProjector.build(segments, definitions, occurrences) as OmbraReviewProjectionResult.Ready).session

    private fun segment(text: String): DocumentSegment = DocumentSegment(
        id = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
        pageIndex = 0,
        blockIndex = 0,
        normalizedText = text,
    )

    private fun definition(id: String, label: String): PiiDefinition = PiiDefinition(
        id = PiiTypeId.parse(id),
        label = label,
        definition = "Definizione sintetica per $label",
        source = PiiDefinitionSource.CUSTOM,
    )

    private fun occurrence(
        segment: DocumentSegment,
        typeId: PiiTypeId,
        surface: String,
        start: Int,
        end: Int,
        decision: ReviewDecisionState,
    ): ReviewOccurrence = ReviewOccurrence(
        id = OccurrenceId(typeId, SourceOccurrence(segment.id, SourceRange(start, end))),
        surface = surface,
        decision = decision,
    )

    private data class ReviewFixture(val session: OmbraReviewProjectionSession, val occurrences: List<ReviewOccurrence>)

    private companion object {
        const val ORIENTATION_ARGUMENT = "orientation"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val ORIENTATION_LANDSCAPE = "landscape"
        const val ORIENTATION_TIMEOUT_MILLIS = 10_000L

        val ReadyHarness = OmbraHarnessUiStatus(
            label = "Harness connesso",
            tone = OmbraStatusTone.LOCAL_READY,
            analysisReady = true,
        )
    }
}
