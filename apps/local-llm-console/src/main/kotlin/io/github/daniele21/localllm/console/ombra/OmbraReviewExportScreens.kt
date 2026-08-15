@file:Suppress("FunctionName", "LongParameterList")

package io.github.daniele21.localllm.console.ombra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.daniele21.localllm.console.presentation.OmbraExportReceipt
import io.github.daniele21.localllm.console.presentation.OmbraReviewConflictState
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import io.github.daniele21.localllm.ui.designsystem.LocalOmbraSpacing
import io.github.daniele21.localllm.ui.designsystem.OmbraFindingDecision
import io.github.daniele21.localllm.ui.designsystem.OmbraFindingDisplayValue
import io.github.daniele21.localllm.ui.designsystem.OmbraFindingInspector
import io.github.daniele21.localllm.ui.designsystem.OmbraPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.OmbraReviewBanner
import io.github.daniele21.localllm.ui.designsystem.OmbraScaffold
import io.github.daniele21.localllm.ui.designsystem.OmbraSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.OmbraStatusBadge
import io.github.daniele21.localllm.ui.designsystem.OmbraStatusTone

@Composable
internal fun OmbraReviewScreen(
    state: OmbraReviewUiState,
    harness: OmbraHarnessUiStatus,
    onPrepareReview: () -> Unit,
    onMove: (Int) -> Unit,
    onToggleReveal: () -> Unit,
    onDecision: (ReviewDecisionState) -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit,
) {
    OmbraScaffold(
        title = "OMBRA",
        stepLabel = "Revisione",
        navigationLabel = "Nuovo PDF",
        onNavigationClick = onReset,
    ) { innerPadding ->
        OmbraReviewContent(innerPadding) {
            OmbraStatusBadge(text = harness.label, tone = harness.tone)
            when (state) {
                OmbraReviewUiState.NotReady -> {
                    Text(text = "Preparazione della revisione", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "OMBRA sta preparando una vista sicura con i valori nascosti per impostazione predefinita.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OmbraSecondaryButton(text = "Riprova preparazione", onClick = onPrepareReview)
                }

                is OmbraReviewUiState.Blocked -> {
                    Text(text = "Revisione non disponibile", style = MaterialTheme.typography.headlineLarge)
                    OmbraReviewBanner(
                        title = "Controllo di sicurezza non superato",
                        detail = "OMBRA non mostra né esporta risultati quando la projection di review non è affidabile.",
                        tone = OmbraStatusTone.ERROR,
                    )
                }

                is OmbraReviewUiState.Empty -> {
                    Text(text = "Nessun dato sensibile rilevato", style = MaterialTheme.typography.headlineLarge)
                    OmbraReviewBanner(
                        title = "Analisi completata",
                        detail = "Non ci sono occorrenze da approvare. Puoi creare il PDF risultante.",
                        tone = OmbraStatusTone.LOCAL_READY,
                    )
                    OmbraPrimaryButton(
                        text = "Esporta PDF",
                        enabled = state.presentation.hidden.summary.canContinue,
                        onClick = onExport,
                    )
                }

                is OmbraReviewUiState.Ready -> {
                    OmbraReviewCandidate(
                        state = state,
                        onMove = onMove,
                        onToggleReveal = onToggleReveal,
                        onDecision = onDecision,
                    )
                    val summary = state.presentation.hidden.summary
                    if (summary.unresolvedConflictCount > 0) {
                        OmbraReviewBanner(
                            title = "Conflitti da risolvere",
                            detail = "Scegli quali occorrenze oscurare o ignorare prima di esportare.",
                            tone = OmbraStatusTone.REVIEW,
                        )
                    } else if (summary.pendingCount > 0) {
                        OmbraReviewBanner(
                            title = "${summary.pendingCount} decisioni mancanti",
                            detail = "Completa la revisione di tutte le occorrenze prima di esportare.",
                            tone = OmbraStatusTone.REVIEW,
                        )
                    }
                    OmbraPrimaryButton(
                        text = "Esporta PDF",
                        enabled = summary.canContinue,
                        onClick = onExport,
                    )
                }
            }
        }
    }
}

@Composable
private fun OmbraReviewCandidate(
    state: OmbraReviewUiState.Ready,
    onMove: (Int) -> Unit,
    onToggleReveal: () -> Unit,
    onDecision: (ReviewDecisionState) -> Unit,
) {
    val candidate = state.currentCandidate
    val revealed = state.presentation.revealedCandidate?.takeIf { it.occurrenceId == candidate.occurrenceId }
    val preview =
        state.presentation.hidden.segments
            .firstOrNull { segment -> segment.segmentId == candidate.occurrenceId.source.segmentId }
            ?.text

    Text(text = "Verifica le occorrenze", style = MaterialTheme.typography.headlineLarge)
    preview?.let { safePreview ->
        Text(
            text = safePreview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OmbraFindingInspector(
        category = candidate.typeLabel,
        positionLabel = "Occorrenza ${candidate.position} di ${candidate.total}",
        value =
        if (revealed == null) {
            OmbraFindingDisplayValue.Hidden(candidate.placeholder, "Valore nascosto")
        } else {
            OmbraFindingDisplayValue.Revealed(revealed.surface, "Contenuto sensibile rivelato")
        },
        decision = candidate.decision.toDesignDecision(),
        decisionLabel = candidate.decision.toDecisionLabel(),
        acceptLabel = "Oscura",
        ignoreLabel = "Ignora",
        previousLabel = "Precedente",
        nextLabel = "Successiva",
        previousEnabled = state.selectedIndex > 0,
        nextEnabled = state.selectedIndex < state.presentation.hidden.candidates.lastIndex,
        onDecisionChange = { decision -> onDecision(decision.toDomainDecision()) },
        onPrevious = { onMove(-1) },
        onNext = { onMove(1) },
    )
    OmbraSecondaryButton(
        text = if (revealed == null) "Mostra valore" else "Nascondi valore",
        onClick = onToggleReveal,
    )
    if (candidate.conflictState == OmbraReviewConflictState.REQUIRES_DECISION) {
        Text(
            text = "Questa occorrenza si sovrappone a un’altra rilevazione e richiede una decisione esplicita.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun OmbraExportProgressScreen(harness: OmbraHarnessUiStatus, onCancel: () -> Unit) {
    OmbraScaffold(title = "OMBRA", stepLabel = "Esportazione") { innerPadding ->
        OmbraReviewContent(innerPadding) {
            Text(text = "Creazione del PDF", style = MaterialTheme.typography.headlineLarge)
            OmbraStatusBadge(text = harness.label, tone = harness.tone)
            Text(
                text = "OMBRA sta generando un nuovo PDF con le sole occorrenze approvate per l’oscuramento.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OmbraSecondaryButton(text = "Annulla", onClick = onCancel)
        }
    }
}

@Composable
internal fun OmbraExportSuccessScreen(receipt: OmbraExportReceipt?, onReset: () -> Unit) {
    OmbraScaffold(title = "OMBRA", stepLabel = "Completato") { innerPadding ->
        OmbraReviewContent(innerPadding) {
            Text(text = "PDF protetto creato", style = MaterialTheme.typography.headlineLarge)
            OmbraReviewBanner(
                title = "Esportazione completata",
                detail =
                receipt?.let { value ->
                    "${value.pageCount} pagine · ${value.byteCount} byte scritti localmente."
                } ?: "Il PDF è stato creato localmente.",
                tone = OmbraStatusTone.LOCAL_READY,
            )
            OmbraPrimaryButton(text = "Proteggi un altro documento", onClick = onReset)
        }
    }
}

@Composable
private fun OmbraReviewContent(innerPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(LocalOmbraSpacing.current.md),
        verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.md),
    ) {
        content()
    }
}

private fun ReviewDecisionState.toDesignDecision(): OmbraFindingDecision = when (this) {
    ReviewDecisionState.PENDING -> OmbraFindingDecision.UNDECIDED
    ReviewDecisionState.ACCEPTED -> OmbraFindingDecision.ACCEPTED
    ReviewDecisionState.IGNORED -> OmbraFindingDecision.IGNORED
}

private fun ReviewDecisionState.toDecisionLabel(): String = when (this) {
    ReviewDecisionState.PENDING -> "Da decidere"
    ReviewDecisionState.ACCEPTED -> "Da oscurare"
    ReviewDecisionState.IGNORED -> "Da lasciare visibile"
}

private fun OmbraFindingDecision.toDomainDecision(): ReviewDecisionState = when (this) {
    OmbraFindingDecision.UNDECIDED -> ReviewDecisionState.PENDING
    OmbraFindingDecision.ACCEPTED -> ReviewDecisionState.ACCEPTED
    OmbraFindingDecision.IGNORED -> ReviewDecisionState.IGNORED
}
