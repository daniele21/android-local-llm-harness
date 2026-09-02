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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HostLogicalJobRestartReconciliationTest {
    private val useCaseId = UseCaseId("document-pii-detection")
    private val scope = HostLogicalJobScope(ApplicationId("redactguard"), useCaseId)
    private val execution =
        ConsumerExecutionIdentity(
            useCaseId = useCaseId,
            capabilityRevision = "capability-a",
            preset = InferencePresetRef(InferencePresetId("balanced"), 3),
            reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
            outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
        )

    @Test
    fun `stale running metadata becomes interrupted once after host restart`() {
        val store = InMemoryMetadataStore(listOf(snapshot(state = HostLogicalJobState.RUNNING, revision = 4)))

        val restored = registry(runtimeSession = "runtime-B", store = store).snapshot(scope, JOB_ID)!!

        assertEquals(HostLogicalJobState.INTERRUPTED, restored.state)
        assertEquals(5, restored.revision)
        assertEquals(HostRuntimeSessionId("runtime-A"), restored.runtimeSessionId)
        assertEquals(restored, store.snapshot(JOB_ID))

        val restoredAgain = registry(runtimeSession = "runtime-C", store = store).snapshot(scope, JOB_ID)!!

        assertEquals(HostLogicalJobState.INTERRUPTED, restoredAgain.state)
        assertEquals(5, restoredAgain.revision)
        assertEquals(HostRuntimeSessionId("runtime-A"), restoredAgain.runtimeSessionId)
    }

    @Test
    fun `terminal metadata remains terminal across host restart`() {
        val terminal = snapshot(state = HostLogicalJobState.SUCCEEDED, revision = 7)
        val store = InMemoryMetadataStore(listOf(terminal))

        val restored = registry(runtimeSession = "runtime-B", store = store).snapshot(scope, JOB_ID)

        assertEquals(terminal, restored)
        assertEquals(terminal, store.snapshot(JOB_ID))
    }

    @Test
    fun `restored metadata remains isolated by authenticated scope`() {
        val store = InMemoryMetadataStore(listOf(snapshot(state = HostLogicalJobState.RUNNING, revision = 2)))
        val registry = registry(runtimeSession = "runtime-B", store = store)
        val otherScope = HostLogicalJobScope(ApplicationId("other-app"), useCaseId)

        assertNull(registry.snapshot(otherScope, JOB_ID))
        assertEquals(HostLogicalJobState.INTERRUPTED, registry.snapshot(scope, JOB_ID)?.state)
    }

    @Test
    fun `failed persistence rejects new job without in-memory residue`() {
        val store = InMemoryMetadataStore(replaceSucceeds = false)
        val registry = registry(runtimeSession = "runtime-A", store = store)

        assertThrows(HostLogicalJobPersistenceException::class.java) {
            registry.submit(scope, HostClientRequestId("request-new"), execution)
        }

        assertEquals(0, registry.size())
        assertTrue(store.allSnapshots().isEmpty())
    }

    @Test
    fun `failed transition persistence leaves authoritative snapshot unchanged`() {
        val store = InMemoryMetadataStore()
        val registry = registry(runtimeSession = "runtime-A", store = store)
        val queued = registry.submit(scope, HostClientRequestId("request-new"), execution).snapshot
        store.replaceSucceeds = false

        assertThrows(HostLogicalJobPersistenceException::class.java) {
            registry.transition(
                scope,
                queued.jobId,
                HostLogicalJobTransition(
                    state = HostLogicalJobState.PREPARING,
                    revision = 1,
                    attempt = 1,
                    runtimeSessionId = HostRuntimeSessionId("runtime-A"),
                ),
            )
        }

        assertEquals(queued, registry.snapshot(scope, queued.jobId))
        assertEquals(queued, store.snapshot(queued.jobId))
    }

    @Test
    fun `capacity eviction removes terminal metadata in same durable replacement`() {
        val terminal = snapshot(state = HostLogicalJobState.SUCCEEDED, revision = 3)
        val store = InMemoryMetadataStore(listOf(terminal))
        val registry = registry(runtimeSession = "runtime-A", store = store, maxJobs = 1)

        val replacement = registry.submit(scope, HostClientRequestId("request-replacement"), execution).snapshot

        assertEquals(1, registry.size())
        assertNull(registry.snapshot(scope, JOB_ID))
        assertNull(store.snapshot(JOB_ID))
        assertEquals(replacement, store.snapshot(replacement.jobId))
    }

    @Test
    fun `metadata round trip contains execution identity but no inference payload field`() {
        val original = snapshot(state = HostLogicalJobState.PREPARING, revision = 1)

        val record = original.toMetadataRecord()
        val restored = record.toSnapshot()
        val persistedFieldNames =
            HostLogicalJobMetadataRecord::class.java.declaredFields
                .filterNot { field -> field.isSynthetic || java.lang.reflect.Modifier.isStatic(field.modifiers) }
                .map { field -> field.name }
                .toSet()

        assertEquals(original, restored)
        assertEquals(
            setOf(
                "jobId",
                "clientRequestId",
                "applicationId",
                "useCaseId",
                "capabilityRevision",
                "presetId",
                "presetVersion",
                "reasoningMode",
                "outputConstraint",
                "sessionKind",
                "state",
                "revision",
                "attempt",
                "runtimeSessionId",
            ),
            persistedFieldNames,
        )
    }

    private fun registry(
        runtimeSession: String,
        store: HostLogicalJobMetadataStore,
        maxJobs: Int = 8,
    ): HostLogicalJobRegistry = HostLogicalJobRegistry(
        maxJobs = maxJobs,
        runtimeSessionId = HostRuntimeSessionId(runtimeSession),
        idFactory = { HostLogicalJobId("job-new") },
        metadataStore = store,
    )

    private fun snapshot(
        state: HostLogicalJobState,
        revision: Long,
    ): HostLogicalJobSnapshot = HostLogicalJobSnapshot(
        jobId = JOB_ID,
        clientRequestId = HostClientRequestId("request-1"),
        scope = scope,
        execution = execution,
        state = state,
        revision = revision,
        attempt = 1,
        runtimeSessionId = HostRuntimeSessionId("runtime-A"),
    )

    private companion object {
        val JOB_ID = HostLogicalJobId("job-1")
    }
}

private class InMemoryMetadataStore(
    snapshots: List<HostLogicalJobSnapshot> = emptyList(),
    var replaceSucceeds: Boolean = true,
) : HostLogicalJobMetadataStore {
    private val snapshotsById = LinkedHashMap<HostLogicalJobId, HostLogicalJobSnapshot>()

    init {
        snapshots.forEach { snapshot -> snapshotsById[snapshot.jobId] = snapshot }
    }

    override fun load(maxJobs: Int): List<HostLogicalJobSnapshot> = snapshotsById.values.take(maxJobs)

    override fun replace(snapshot: HostLogicalJobSnapshot, evictedJobId: HostLogicalJobId?): Boolean {
        if (!replaceSucceeds) return false
        evictedJobId?.let(snapshotsById::remove)
        snapshotsById[snapshot.jobId] = snapshot
        return true
    }

    fun snapshot(jobId: HostLogicalJobId): HostLogicalJobSnapshot? = snapshotsById[jobId]

    fun allSnapshots(): List<HostLogicalJobSnapshot> = snapshotsById.values.toList()
}
