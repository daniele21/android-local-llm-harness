@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "OMBRA components light", showBackground = true)
@Composable
internal fun OmbraComponentsLightPreview() {
    OmbraTheme(darkTheme = false) {
        OmbraComponentPreviewContent()
    }
}

@Preview(name = "OMBRA components dark", showBackground = true)
@Composable
internal fun OmbraComponentsDarkPreview() {
    OmbraTheme(darkTheme = true) {
        OmbraComponentPreviewContent()
    }
}

@Composable
private fun OmbraComponentPreviewContent() {
    Column(
        modifier = Modifier.padding(LocalOmbraSpacing.current.md),
        verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.md),
    ) {
        OmbraStatusBadge(text = "Harness connesso", tone = OmbraStatusTone.LOCAL_READY)
        OmbraTaskProgressStep(
            title = "PII in analisi",
            state = OmbraProgressState.ACTIVE,
            detail = "Chunk 2 di 4",
        )
        OmbraDefinitionSelectionRow(
            label = "Email",
            definition = "Indirizzo email riferibile a una persona",
            selected = true,
            onSelectedChange = {},
        )
        OmbraReviewBanner(
            title = "Verifica necessaria",
            detail = "Controlla ogni candidato prima di esportare il documento.",
        )
        OmbraRedactionPlaceholder(
            placeholder = "[EMAIL_1]",
            hiddenContentDescription = "Valore sensibile nascosto, email 1",
        )
        OmbraExportSummary(
            acceptedLabel = "12 accettati",
            ignoredLabel = "2 ignorati",
            pagesLabel = "4 pagine",
        )
        OmbraPrimaryButton(text = "Continua", onClick = {})
        OmbraSecondaryButton(text = "Annulla", onClick = {})
    }
}
