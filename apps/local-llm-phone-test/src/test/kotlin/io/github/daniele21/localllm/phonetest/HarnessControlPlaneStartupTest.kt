package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.HostControlPlaneTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HarnessControlPlaneStartupTest {
    private val requirement =
        HarnessBuiltInApplicationRequirement(
            applicationId = APP_ID,
            acceptedPackageNames = setOf("io.github.daniele21.redactguard"),
            acceptedSignerSha256 = setOf(SIGNER),
            displayName = "RedactGuard",
        )
    private val spec = HarnessBuiltInControlPlaneSpec.ombra(listOf(requirement))

    @Test
    fun startupRepairsPartialStateAndRepeatedRunIsIdempotent() {
        val existingApplication = requirement.newRegistration(10)
        val store = RecordingStore(HostControlPlaneState(applications = listOf(existingApplication)))
        val startup = HarnessControlPlaneStartup(
            store = store,
            reconciler = HarnessControlPlaneReconciler(spec),
            epochClock = { 100 },
        )

        val first = startup.reconcile()
        val second = startup.reconcile()

        assertEquals(first, second)
        assertEquals(existingApplication, second.applications.single())
        assertEquals(spec.useCase, second.useCases.single())
        assertEquals(spec.preset, second.presets.single())
        assertEquals(spec.bindingFor(APP_ID), second.bindings.single())
        assertEquals(2, store.transactionCount)
    }

    @Test
    fun startupTransactionRunsOffCallingThread() {
        val store = RecordingStore(HostControlPlaneState())
        val callingThread = Thread.currentThread()
        val startup = HarnessControlPlaneStartup(
            store = store,
            reconciler = HarnessControlPlaneReconciler(spec),
            epochClock = { 100 },
        )

        startup.reconcile()

        assertNotEquals(callingThread, store.lastTransactionThread)
    }

    @Test
    fun startupConflictAbortsTransactionWithoutMutatingStore() {
        val incompatible = requirement.newRegistration(10).copy(signerSha256 = "b".repeat(64))
        val original = HostControlPlaneState(applications = listOf(incompatible))
        val store = RecordingStore(original)
        val startup = HarnessControlPlaneStartup(
            store = store,
            reconciler = HarnessControlPlaneReconciler(spec),
            epochClock = { 100 },
        )

        val failure = assertThrows(HarnessControlPlaneStartupConflictException::class.java) {
            startup.reconcile()
        }

        assertEquals(HarnessControlPlaneConflictCode.APPLICATION_IDENTITY, failure.code)
        assertEquals(APP_ID.value, failure.identity)
        assertEquals(original, store.snapshot())
    }

    private class RecordingStore(initial: HostControlPlaneState) : HostControlPlaneStore {
        private var state = initial
        var transactionCount: Int = 0
            private set
        var lastTransactionThread: Thread? = null
            private set

        override fun snapshot(): HostControlPlaneState = state

        override fun replace(state: HostControlPlaneState) {
            this.state = state
        }

        override fun transact(transaction: HostControlPlaneTransaction): HostControlPlaneState {
            transactionCount += 1
            lastTransactionThread = Thread.currentThread()
            val updated = transaction.apply(state)
            state = updated
            return updated
        }
    }

    private companion object {
        val APP_ID = ApplicationId("redactguard")
        const val SIGNER = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
    }
}
