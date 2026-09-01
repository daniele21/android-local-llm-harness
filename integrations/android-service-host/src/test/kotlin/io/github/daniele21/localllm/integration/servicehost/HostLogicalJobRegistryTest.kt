package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostLogicalJobRegistryTest {
    private val useCaseId = UseCaseId("document-pii-detection")
    private val scope = HostLogicalJobScope(ApplicationId("redactguard"), useCaseId)
    private val otherScope = HostLogicalJobScope(ApplicationId("other-app"), useCaseId)
    private val runtimeA = HostRuntimeSessionId("runtime-A")
    private val execution = execution("capability-a", 1)
    private var nextId = 1

    @Test
    fun `same caller request id converges to one logical job`() {
        val registry = registry()
        val requestId = HostClientRequestId("rg42:analysis:chunk-7:v1")

        val first = registry.submit(scope, requestId, execution)
        val duplicate = registry.submit(scope, requestId, execution)

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals(first.snapshot.jobId, duplicate.snapshot.jobId)
        assertEquals(execution, duplicate.snapshot.execution)
        assertEquals(1, registry.size())
    }

    @Test
    fun `same caller request id with different execution identity fails closed`() {
        val registry = registry()
        val requestId = HostClientRequestId("rg42:analysis:chunk-7:v1")
        registry.submit(scope, requestId, execution)

        val failure =
            runCatching {
                registry.submit(scope, requestId, execution("capability-b", 2))
            }.exceptionOrNull()

        assertTrue(failure is HostLogicalJobIdentityConflictException)
        assertEquals(1, registry.size())
    }

    @Test
    fun `same request id is isolated by authenticated logical scope`() {
        val registry = registry()
        val requestId = HostClientRequestId("shared-key")

        val first = registry.submit(scope, requestId, execution)
        val second = registry.submit(otherScope, requestId, execution)

        assertTrue(first.created)
        assertTrue(second.created)
        assertFalse(first.snapshot.jobId == second.snapshot.jobId)
        assertEquals(2, registry.size())
    }

    @Test
    fun `job snapshot cannot be read from another scope`() {
        val registry = registry()
        val job = registry.submit(scope, HostClientRequestId("request-1"), execution).snapshot

        assertNull(registry.snapshot(otherScope, job.jobId))
        assertEquals(job, registry.snapshot(scope, job.jobId))
    }

    @Test
    fun `stale revision cannot overwrite newer job state`() {
        val registry = registry()
        val job = registry.submit(scope, HostClientRequestId("request-1"), execution).snapshot
        val preparing = registry.transition(
            scope,
            job.jobId,
            HostLogicalJobTransition(HostLogicalJobState.PREPARING, 1, 1, runtimeA),
        )!!
        val running = registry.transition(
            scope,
            job.jobId,
            HostLogicalJobTransition(HostLogicalJobState.RUNNING, 2, 1, runtimeA),
        )!!
        val stale = registry.transition(
            scope,
            job.jobId,
            HostLogicalJobTransition(HostLogicalJobState.FAILED_FINAL, 1, 1, runtimeA),
        )!!

        assertEquals(HostLogicalJobState.PREPARING, preparing.state)
        assertEquals(HostLogicalJobState.RUNNING, running.state)
        assertEquals(running, stale)
    }

    @Test
    fun `runtime session mismatch interrupts non terminal job`() {
        val job = registry().submit(scope, HostClientRequestId("request-1"), execution).snapshot.copy(
            state = HostLogicalJobState.RUNNING,
            revision = 4,
        )

        val reconciled = HostLogicalJobLifecycle.interruptStaleRuntime(job, HostRuntimeSessionId("runtime-B"))

        assertEquals(HostLogicalJobState.INTERRUPTED, reconciled.state)
        assertEquals(5, reconciled.revision)
        assertEquals(runtimeA, reconciled.runtimeSessionId)
        assertEquals(execution, reconciled.execution)
    }

    @Test
    fun `interrupted job starts recovery in new runtime session and increments attempt`() {
        val interrupted = registry().submit(scope, HostClientRequestId("request-1"), execution).snapshot.copy(
            state = HostLogicalJobState.INTERRUPTED,
            revision = 3,
        )

        val recovering = HostLogicalJobLifecycle.apply(
            interrupted,
            HostLogicalJobTransition(
                state = HostLogicalJobState.RECOVERING,
                revision = 4,
                attempt = 2,
                runtimeSessionId = HostRuntimeSessionId("runtime-B"),
            ),
        )

        assertEquals(HostLogicalJobState.RECOVERING, recovering.state)
        assertEquals(2, recovering.attempt)
        assertEquals(HostRuntimeSessionId("runtime-B"), recovering.runtimeSessionId)
        assertEquals(execution, recovering.execution)
    }

    @Test
    fun `capacity evicts terminal job but never active job`() {
        val registry = registry(maxJobs = 1)
        val first = registry.submit(scope, HostClientRequestId("first"), execution).snapshot
        registry.transition(
            scope,
            first.jobId,
            HostLogicalJobTransition(HostLogicalJobState.PREPARING, 1, 1, runtimeA),
        )
        registry.transition(
            scope,
            first.jobId,
            HostLogicalJobTransition(HostLogicalJobState.RUNNING, 2, 1, runtimeA),
        )
        registry.transition(
            scope,
            first.jobId,
            HostLogicalJobTransition(HostLogicalJobState.SUCCEEDED, 3, 1, runtimeA),
        )

        val second = registry.submit(scope, HostClientRequestId("second"), execution)

        assertTrue(second.created)
        assertEquals(1, registry.size())
        assertNull(registry.snapshot(scope, first.jobId))
    }

    @Test
    fun `capacity fails closed when every job is non terminal`() {
        val registry = registry(maxJobs = 1)
        registry.submit(scope, HostClientRequestId("first"), execution)

        val failure = runCatching { registry.submit(scope, HostClientRequestId("second"), execution) }.exceptionOrNull()

        assertTrue(failure is HostLogicalJobCapacityException)
        assertEquals(1, registry.size())
    }

    private fun execution(capabilityRevision: String, presetVersion: Int): ConsumerExecutionIdentity =
        ConsumerExecutionIdentity(
            useCaseId = useCaseId,
            capabilityRevision = capabilityRevision,
            preset = InferencePresetRef(InferencePresetId("balanced"), presetVersion),
            reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
            outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
        )

    private fun registry(maxJobs: Int = 8): HostLogicalJobRegistry = HostLogicalJobRegistry(
        maxJobs = maxJobs,
        runtimeSessionId = runtimeA,
        idFactory = { HostLogicalJobId("job-${nextId++}") },
    )
}
