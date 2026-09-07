package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore

/**
 * Refreshes source-backed package/signing identity before Binder authorization.
 *
 * PackageManager observation is never authority by itself: a new independent consumer remains PENDING and an
 * observed signer replacement becomes SIGNATURE_CHANGED. Read-only surfaces may preview that same canonical
 * reconciliation without persisting it; explicit authorization and Binder authorization persist it first.
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

    /** Returns the source-observed identity projection without mutating the persisted Control Plane. */
    fun previewCurrentState(): HostControlPlaneState = reconcile(store.snapshot(), epochClock()).state

    fun reconcileIfNeeded(): HostControlPlaneState {
        val observedAtEpochMs = epochClock()
        val reconciler = reconciler()
        val preview = reconcile(reconciler, store.snapshot(), observedAtEpochMs)
        return if (!preview.changed) {
            preview.state
        } else {
            store.transact { latest -> reconcile(reconciler, latest, observedAtEpochMs).state }
        }
    }

    private fun reconcile(current: HostControlPlaneState, observedAtEpochMs: Long): HarnessControlPlaneReconciliationResult.Success =
        reconcile(reconciler(), current, observedAtEpochMs)

    private fun reconcile(
        reconciler: HarnessControlPlaneReconciler,
        current: HostControlPlaneState,
        observedAtEpochMs: Long,
    ): HarnessControlPlaneReconciliationResult.Success = when (val result = reconciler.reconcile(current, observedAtEpochMs)) {
        is HarnessControlPlaneReconciliationResult.Success -> result
        is HarnessControlPlaneReconciliationResult.Conflict -> throw conflict(result)
    }

    private fun reconciler(): HarnessControlPlaneReconciler = HarnessControlPlaneReconciler(
        HarnessSharedRuntimePolicy.builtInOmbraControlPlaneSpec(observedPolicies()),
    )

    private fun conflict(result: HarnessControlPlaneReconciliationResult.Conflict) =
        HarnessControlPlaneStartupConflictException(result.code, result.identity)
}
