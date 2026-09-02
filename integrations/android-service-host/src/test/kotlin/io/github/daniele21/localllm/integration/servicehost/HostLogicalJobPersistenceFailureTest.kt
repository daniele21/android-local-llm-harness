package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobRequestId
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobSubmitRequest
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.toConsumerLogicalJobWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostLogicalJobPersistenceFailureTest {
    private val useCaseId = UseCaseId("document-pii-detection")
    private val scope = HostLogicalJobScope(ApplicationId("redactguard"), useCaseId)
    private val caller =
        AuthorizedCaller(
            uid = 42,
            packageName = "io.github.daniele21.redactguard",
            applicationId = scope.applicationId,
            allowedUseCases = setOf(useCaseId),
        )
    private val execution =
        ConsumerExecutionIdentity(
            useCaseId = useCaseId,
            capabilityRevision = "capability-a",
            preset = InferencePresetRef(InferencePresetId("balanced"), 1),
            reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
            outputConstraint = ConsumerOutputConstraintKind.JSON,
            sessionKind = SessionKind.STATELESS,
        )

    @Test
    fun `submit persistence failure becomes bounded runtime failure without creating a job`() {
        val store = CountingMetadataStore(failOnReplace = 1)
        val registry = registry(store)
        val coordinator = HostLogicalJobCoordinator(registry)

        val response = coordinator.submit(caller, CountingClient(), request())

        assertEquals(WireErrorCodes.RUNTIME_FAILURE, response.error?.code)
        assertNull(response.snapshot)
        assertEquals(0, registry.size())
    }

    @Test
    fun `transition persistence failure interrupts current process job and releases durable demand`() {
        val store = CountingMetadataStore(failOnReplace = 2)
        val registry = registry(store)
        val demand = HostLogicalJobExecutionDemand()
        val demandEvents = mutableListOf<Boolean>()
        demand.setListener(demandEvents::add)
        val client = CountingClient()
        val coordinator = HostLogicalJobCoordinator(registry, demand)

        val response = coordinator.submit(caller, client, request())
        val jobId = HostLogicalJobId("job-1")

        assertEquals(WireErrorCodes.RUNTIME_FAILURE, response.error?.code)
        assertEquals(HostLogicalJobState.INTERRUPTED, registry.snapshot(scope, jobId)?.state)
        assertEquals(0, client.createSessionCalls)
        assertEquals(listOf(true, false), demandEvents)
    }

    private fun registry(store: HostLogicalJobMetadataStore): HostLogicalJobRegistry =
        HostLogicalJobRegistry(
            maxJobs = 4,
            runtimeSessionId = HostRuntimeSessionId("runtime-A"),
            idFactory = { HostLogicalJobId("job-1") },
            metadataStore = store,
        )

    private fun request() =
        ConsumerLogicalJobSubmitRequest(
            clientRequestId = ConsumerLogicalJobRequestId("analysis-1:chunk-1"),
            useCaseId = useCaseId,
            preparedId = ConsumerPreparedId("prepared-1"),
            expectedExecution = execution,
            input = ConsumerGenerationInput.Text("sensitive input must remain process-local"),
            outputConstraint = ConsumerOutputConstraint.Json,
        ).toConsumerLogicalJobWire(ClientTokenParcel("token"), "operation-1")
}

private class CountingMetadataStore(
    private val failOnReplace: Int,
) : HostLogicalJobMetadataStore {
    private var replaceCalls = 0
    private val snapshots = LinkedHashMap<HostLogicalJobId, HostLogicalJobSnapshot>()

    override fun load(maxJobs: Int): List<HostLogicalJobSnapshot> = snapshots.values.take(maxJobs)

    override fun replace(snapshot: HostLogicalJobSnapshot, evictedJobId: HostLogicalJobId?): Boolean {
        replaceCalls += 1
        if (replaceCalls == failOnReplace) return false
        evictedJobId?.let(snapshots::remove)
        snapshots[snapshot.jobId] = snapshot
        return true
    }
}

private class CountingClient : ConsumerLocalLlmClient {
    var createSessionCalls = 0

    override fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult = error("not used")

    override fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult = error("not used")

    override fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult {
        createSessionCalls += 1
        return ConsumerSessionResult.Created(SessionId("session-1"))
    }

    override fun generate(
        request: ConsumerGenerationRequest,
        listener: ConsumerGenerationListener,
    ): ConsumerGenerationStartResult = error("must not be reached")

    override fun closeSession(sessionId: SessionId) = Unit
}
