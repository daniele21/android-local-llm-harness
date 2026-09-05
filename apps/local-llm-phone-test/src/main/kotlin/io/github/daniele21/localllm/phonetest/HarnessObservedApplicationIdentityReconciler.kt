package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore

/**
 * Refreshes source-backed package/signing identity before Binder authorization.
 *
 * PackageManager observation is never authority by itself: a new independent consumer remains PENDING and an
 * observed signer replacement becomes SIGNATURE_CHANGED. The transaction runs only when the pure reconciliation
 * detects an actual state change, keeping the normal per-call authorization path read-only.
 */
internal class HarnessObservedApplicationIdentityReconciler(
    private val store: HostControlPlaneStore,
    private val observedPolicies: () -> List<AuthorizedClientPolicy>,
    private val epochClock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        context: Context,
        store: HostControlPlaneStore,
        epochClock: () -> Long = System::currentTimeMillis,
    ) : this(
        store = store,
        observedPolicies = { HarnessSharedRuntimePolicy.authorizedClients(context.applicationContext) },
        epochClock = epochClock,
    )

    fun reconcileIfNeeded(): HostControlPlaneState {
        val observedAtEpochMs = epochClock()
        val reconciler =
            HarnessControlPlaneReconciler(
                HarnessSharedRuntimePolicy.builtInOmbraControlPlaneSpec(observedPolicies()),
            )
        val current = store.snapshot()
        return when (val preview = reconciler.reconcile(current, observedAtEpochMs)) {
            is HarnessControlPlaneReconciliationResult.Conflict ->
                throw HarnessControlPlaneStartupConflictException(preview.code, preview.identity)

            is HarnessControlPlaneReconciliationResult.Success -> {
                if (!preview.changed) {
                    preview.state
                } else {
                    store.transact { latest ->
                        when (val result = reconciler.reconcile(latest, observedAtEpochMs)) {
                            is HarnessControlPlaneReconciliationResult.Success -> result.state
                            is HarnessControlPlaneReconciliationResult.Conflict ->
                                throw HarnessControlPlaneStartupConflictException(result.code, result.identity)
                        }
                    }
                }
            }
        }
    }
}
