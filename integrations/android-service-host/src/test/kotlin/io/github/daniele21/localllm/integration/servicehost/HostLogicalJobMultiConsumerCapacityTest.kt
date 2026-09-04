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
import org.junit.Assert.assertTrue
import org.junit.Test

class HostLogicalJobMultiConsumerCapacityTest {
    private val useCaseId = UseCaseId("document-pii-detection")
    private val runtimeSessionId = HostRuntimeSessionId("runtime-A")
    private var nextId = 1

    @Test
    fun `bounded admission is shared across isolated consumer scopes`() {
        val registry = HostLogicalJobRegistry(
            maxJobs = 2,
            runtimeSessionId = runtimeSessionId,
            idFactory = { HostLogicalJobId("job-${nextId++}") },
        )
        val redactionScope = HostLogicalJobScope(ApplicationId("redactguard"), useCaseId)
        val meetingScope = HostLogicalJobScope(ApplicationId("meeting-consumer"), useCaseId)
        val thirdScope = HostLogicalJobScope(ApplicationId("third-consumer"), useCaseId)

        val first = registry.submit(redactionScope, HostClientRequestId("request-1"), execution())
        val second = registry.submit(meetingScope, HostClientRequestId("request-1"), execution())
        val failure = runCatching {
            registry.submit(thirdScope, HostClientRequestId("request-1"), execution())
        }.exceptionOrNull()

        assertTrue(first.created)
        assertTrue(second.created)
        assertTrue(first.snapshot.jobId != second.snapshot.jobId)
        assertTrue(failure is HostLogicalJobCapacityException)
        assertEquals(2, registry.size())
    }

    private fun execution() = ConsumerExecutionIdentity(
        useCaseId = useCaseId,
        capabilityRevision = "capability-a",
        preset = InferencePresetRef(InferencePresetId("balanced"), 1),
        reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
        outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
        sessionKind = SessionKind.STATELESS,
    )
}
