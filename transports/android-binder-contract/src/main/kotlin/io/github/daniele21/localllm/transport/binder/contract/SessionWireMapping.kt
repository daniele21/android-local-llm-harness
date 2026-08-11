package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ContextPolicy
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions

fun SessionOptions.toWire(): SessionOptionsParcel = SessionOptionsParcel(
    contextPolicyTag =
    when (contextPolicy) {
        ContextPolicy.Auto -> WireTags.CONTEXT_AUTO
        is ContextPolicy.Manual -> WireTags.CONTEXT_MANUAL
    },
    manualContextTokens = (contextPolicy as? ContextPolicy.Manual)?.tokens,
    sessionKindTag =
    when (kind) {
        SessionKind.STATELESS -> WireTags.SESSION_STATELESS
        SessionKind.CONVERSATIONAL -> WireTags.SESSION_CONVERSATIONAL
    },
)

fun SessionOptionsParcel.toCore(): SessionOptions {
    val contextPolicy =
        when (contextPolicyTag) {
            WireTags.CONTEXT_AUTO -> ContextPolicy.Auto
            WireTags.CONTEXT_MANUAL -> ContextPolicy.Manual(requireNotNull(manualContextTokens))
            else -> throw invalidWireTag("context policy", contextPolicyTag)
        }
    val sessionKind =
        when (sessionKindTag) {
            WireTags.SESSION_STATELESS -> SessionKind.STATELESS
            WireTags.SESSION_CONVERSATIONAL -> SessionKind.CONVERSATIONAL
            else -> throw invalidWireTag("session kind", sessionKindTag)
        }
    return SessionOptions(contextPolicy = contextPolicy, kind = sessionKind)
}
