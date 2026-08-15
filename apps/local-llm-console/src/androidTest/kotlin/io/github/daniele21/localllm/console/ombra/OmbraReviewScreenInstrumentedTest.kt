package io.github.daniele21.localllm.console.ombra

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSource
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.console.presentation.OmbraReviewPresentationResult
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjectionResult
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjector
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjectionSession
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import io.github.daniele21.localllm.console.redaction.ReviewOccurrence
import io.github.daniele21.localllm.ui.designsystem.OmbraStatusTone
import io.github.daniele21.localllm.ui.designsystem.OmbraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OmbraReviewScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

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
        composeRule.onNodeWithText("Mostra valore").assertIsDisplayed()
        composeRule.onNodeWithText("Esporta PDF").assertIsNotEnabled()
    }

    @Test
    fun explicitRevealAddsOnlyTheSelectedSensitiveSurfaceToSemantics() {
        val surface = "alice@example.test"
        val fixture = singleEmailFixture(surface, ReviewDecisionState.ACCEPTED)
        val presentation = (
            fixture.session.present(fixture.occurrences.single().id) as OmbraReviewPresentationResult.Ready
        ).model

        setReviewContent(OmbraReviewUiState.Ready(presentation, selectedIndex = 0))

        composeRule.onAllNodes(
            hasContentDescription(surface, substring = true),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithText("Nascondi valore").assertIsDisplayed()
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

        composeRule.onNodeWithText("Conflitti da risolvere").assertIsDisplayed()
        composeRule.onNodeWithText("Esporta PDF").assertIsNotEnabled()
    }

    @Test
    fun zeroPiiReviewRemainsAValidExportPath() {
        val segment = segment("Documento sintetico senza dati sensibili")
        val session = readySession(listOf(segment), definitions = emptyList(), occurrences = emptyList())
        val presentation = (session.present() as OmbraReviewPresentationResult.Ready).model

        setReviewContent(OmbraReviewUiState.Empty(presentation))

        composeRule.onNodeWithText("Nessun dato sensibile rilevato").assertIsDisplayed()
        composeRule.onNodeWithText("Esporta PDF").assertIsEnabled()
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

        composeRule.onNodeWithText("Esporta PDF").performScrollTo().assertIsDisplayed().assertIsEnabled()
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

    private data class ReviewFixture(
        val session: OmbraReviewProjectionSession,
        val occurrences: List<ReviewOccurrence>,
    )

    private companion object {
        val ReadyHarness = OmbraHarnessUiStatus(
            label = "Harness connesso",
            tone = OmbraStatusTone.LOCAL_READY,
            analysisReady = true,
        )
    }
}
