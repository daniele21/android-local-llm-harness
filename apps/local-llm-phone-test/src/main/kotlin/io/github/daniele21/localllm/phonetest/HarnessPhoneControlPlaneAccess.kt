package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore

/**
 * Phone composition boundary that keeps persisted control-plane state, observed Android identity and read-only
 * runtime sources explicit.
 *
 * Normal reads may project the currently installed package/signer identity without mutating persistence. Explicit
 * authorization can then reconcile that exact observed identity before the canonical authorization mutation.
 */
internal class HarnessPhoneControlPlaneAccess(
    private val store: HostControlPlaneStore,
    val applicationsRuntimeSource: HarnessApplicationsRuntimeSource,
    private val observedIdentityReconciler: HarnessObservedApplicationIdentityReconciler? = null,
) : HostControlPlaneStore by store {
    constructor(
        context: Context,
        store: HostControlPlaneStore,
        applicationsRuntimeSource: HarnessApplicationsRuntimeSource,
    ) : this(
        store = store,
        applicationsRuntimeSource = applicationsRuntimeSource,
        observedIdentityReconciler = HarnessObservedApplicationIdentityReconciler(context, store),
    )

    override fun snapshot(): HostControlPlaneState =
        observedIdentityReconciler?.previewCurrentState() ?: store.snapshot()

    fun reconcileObservedIdentityIfNeeded(): HostControlPlaneState =
        observedIdentityReconciler?.reconcileIfNeeded() ?: store.snapshot()
}
