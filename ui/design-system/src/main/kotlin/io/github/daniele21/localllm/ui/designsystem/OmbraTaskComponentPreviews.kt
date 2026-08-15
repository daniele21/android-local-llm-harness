@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "OMBRA task shell", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
internal fun OmbraTaskShellPreview() {
    OmbraTheme(darkTheme = false) {
        OmbraScaffold(
            title = "OMBRA",
            stepLabel = "Importazione",
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(LocalOmbraSpacing.current.md),
                verticalArrangement = Arrangement.spacedBy(LocalOmbraSpacing.current.lg),
            ) {
                Text(text = "Proteggi un documento", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                OmbraDocumentPickerSurface(
                    title = "Seleziona un PDF",
                    description = "Il documento rimane su questo dispositivo.",
                    actionLabel = "Importa PDF",
                    onClick = {},
                )
                OmbraStatusBadge(text = "Harness connesso", tone = OmbraStatusTone.LOCAL_READY)
            }
        }
    }
}

@Preview(name = "OMBRA finding hidden", showBackground = true, widthDp = 360)
@Composable
internal fun OmbraHiddenFindingPreview() {
    OmbraTheme(darkTheme = false) {
        OmbraHiddenFindingPreviewContent()
    }
}

@Preview(name = "OMBRA finding hidden dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun OmbraHiddenFindingDarkPreview() {
    OmbraTheme(darkTheme = true) {
        OmbraHiddenFindingPreviewContent()
    }
}

@Preview(name = "OMBRA definition editor", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
internal fun OmbraDefinitionEditorPreview() {
    OmbraTheme(darkTheme = false) {
        OmbraDefinitionEditorSheet(
            state =
            OmbraDefinitionEditorState(
                name = "Codice cliente",
                definition = "Identificativo assegnato al cliente",
                example = "CLI-0001",
                nameSupportingText = "15 caratteri",
                definitionSupportingText = "42 caratteri",
                canAdd = true,
            ),
            title = "Aggiungi PII personalizzato",
            guidance = "La definizione guida il rilevamento, ma non garantisce una corrispondenza.",
            nameLabel = "Nome",
            definitionLabel = "Definizione",
            exampleLabel = "Esempio (facoltativo)",
            addLabel = "Aggiungi",
            cancelLabel = "Annulla",
            onNameChange = {},
            onDefinitionChange = {},
            onExampleChange = {},
            onAdd = {},
            onDismiss = {},
        )
    }
}

@Composable
private fun OmbraHiddenFindingPreviewContent() {
    Column(modifier = Modifier.padding(LocalOmbraSpacing.current.md)) {
        OmbraFindingInspector(
            category = "Email",
            positionLabel = "Occorrenza 1 di 3",
            value = OmbraFindingDisplayValue.Hidden(placeholder = "[EMAIL_1]", stateLabel = "Valore nascosto"),
            decision = OmbraFindingDecision.UNDECIDED,
            decisionLabel = "Da decidere",
            acceptLabel = "Accetta",
            ignoreLabel = "Ignora",
            previousLabel = "Precedente",
            nextLabel = "Successiva",
            previousEnabled = false,
            onDecisionChange = {},
            onPrevious = {},
            onNext = {},
        )
    }
}
