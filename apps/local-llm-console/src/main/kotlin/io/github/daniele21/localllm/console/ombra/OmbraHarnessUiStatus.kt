package io.github.daniele21.localllm.console.ombra

import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState
import io.github.daniele21.localllm.ui.designsystem.OmbraStatusTone

internal data class OmbraHarnessUiStatus(
    val label: String,
    val tone: OmbraStatusTone,
    val analysisReady: Boolean,
)

internal fun ombraHarnessUiStatus(state: SharedRuntimeConnectionState): OmbraHarnessUiStatus =
    when (state) {
        SharedRuntimeConnectionState.CONNECTED ->
            OmbraHarnessUiStatus("Harness connesso", OmbraStatusTone.LOCAL_READY, true)

        SharedRuntimeConnectionState.BINDING,
        SharedRuntimeConnectionState.NEGOTIATING,
        -> OmbraHarnessUiStatus("Connessione a Harness", OmbraStatusTone.NEUTRAL, false)

        SharedRuntimeConnectionState.PERMISSION_DENIED ->
            OmbraHarnessUiStatus("Accesso a Harness negato", OmbraStatusTone.ERROR, false)

        SharedRuntimeConnectionState.INCOMPATIBLE ->
            OmbraHarnessUiStatus("Harness incompatibile", OmbraStatusTone.ERROR, false)

        SharedRuntimeConnectionState.HOST_NOT_INSTALLED ->
            OmbraHarnessUiStatus("Harness non disponibile", OmbraStatusTone.ERROR, false)

        SharedRuntimeConnectionState.DISCONNECTED,
        SharedRuntimeConnectionState.CONNECTION_LOST,
        -> OmbraHarnessUiStatus("Harness disconnesso", OmbraStatusTone.REVIEW, false)

        SharedRuntimeConnectionState.CLOSED ->
            OmbraHarnessUiStatus("Harness non disponibile", OmbraStatusTone.NEUTRAL, false)
    }
