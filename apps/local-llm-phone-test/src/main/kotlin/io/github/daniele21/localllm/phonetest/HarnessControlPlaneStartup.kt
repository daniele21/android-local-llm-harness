package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore

internal class HarnessControlPlaneStartupConflictException(val code: HarnessControlPlaneConflictCode, val identity: String) :
    IllegalStateException("Built-in control-plane conflict: $code ($identity)")

/** Applies the pure built-in reconciliation as one store transaction before readers are exposed. */
internal class HarnessControlPlaneStartup(
    private val store: HostControlPlaneStore,
    private val reconciler: HarnessControlPlaneReconciler,
    private val epochClock: () -> Long = System::currentTimeMillis,
) {
    fun reconcile(): HostControlPlaneState = store.transact { current ->
        when (val result = reconciler.reconcile(current, epochClock())) {
            is HarnessControlPlaneReconciliationResult.Success -> result.state

            is HarnessControlPlaneReconciliationResult.Conflict ->
                throw HarnessControlPlaneStartupConflictException(result.code, result.identity)
        }
    }
}
