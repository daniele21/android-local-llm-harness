package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryAdmissionControllerTest {
    private val budget = RuntimeMemoryBudget(
        minimumAvailableBytes = 256,
        safetyReserveBytes = 128,
        maxProcessPssBytes = 2_000,
        maxResidentContexts = 2,
    )
    private val controller = MemoryAdmissionController(budget)

    @Test
    fun `allows request when available memory and process pss stay within budget`() {
        val decision = controller.decide(
            observation = RuntimeMemoryObservation(
                processPssBytes = 1_000,
                availableMemoryBytes = 1_500,
                lowMemory = false,
            ),
            request = contextRequest(peakBytes = 500, residentContexts = 1),
        )

        assertEquals(MemoryAdmissionDecision.Allow, decision)
    }

    @Test
    fun `platform low memory overrides otherwise safe headroom`() {
        val decision = controller.decide(
            observation = RuntimeMemoryObservation(
                processPssBytes = 500,
                availableMemoryBytes = 5_000,
                lowMemory = true,
            ),
            request = contextRequest(peakBytes = 100, residentContexts = 0),
        )

        assertReject(MemoryAdmissionRejectReason.PLATFORM_LOW_MEMORY, decision)
    }

    @Test
    fun `rejects context when resident context limit is reached`() {
        val decision = controller.decide(
            observation = RuntimeMemoryObservation(availableMemoryBytes = 5_000),
            request = contextRequest(peakBytes = 100, residentContexts = 2),
        )

        assertReject(MemoryAdmissionRejectReason.RESIDENT_CONTEXT_LIMIT, decision)
    }

    @Test
    fun `rejects when available memory would cross floor plus reserve`() {
        val decision = controller.decide(
            observation = RuntimeMemoryObservation(availableMemoryBytes = 800),
            request = contextRequest(peakBytes = 500, residentContexts = 0),
        )

        assertReject(MemoryAdmissionRejectReason.AVAILABLE_MEMORY_FLOOR, decision)
    }

    @Test
    fun `rejects when projected process pss exceeds configured ceiling`() {
        val decision = controller.decide(
            observation = RuntimeMemoryObservation(
                processPssBytes = 1_700,
                availableMemoryBytes = 5_000,
            ),
            request = contextRequest(peakBytes = 400, residentContexts = 0),
        )

        assertReject(MemoryAdmissionRejectReason.PROCESS_PSS_LIMIT, decision)
    }

    @Test
    fun `requires at least one configured headroom observation`() {
        val decision = controller.decide(
            observation = RuntimeMemoryObservation(nativeHeapBytes = 100),
            request = contextRequest(peakBytes = 50, residentContexts = 0),
        )

        assertReject(MemoryAdmissionRejectReason.HEADROOM_OBSERVATION_REQUIRED, decision)
    }

    @Test
    fun `detects overflow instead of wrapping byte arithmetic`() {
        val decision = controller.decide(
            observation = RuntimeMemoryObservation(availableMemoryBytes = Long.MAX_VALUE),
            request = contextRequest(peakBytes = Long.MAX_VALUE, residentContexts = 0),
        )

        assertReject(MemoryAdmissionRejectReason.BYTE_ARITHMETIC_OVERFLOW, decision)
    }

    @Test
    fun `model admission does not consume resident context capacity`() {
        val decision = controller.decide(
            observation = RuntimeMemoryObservation(
                processPssBytes = 1_000,
                availableMemoryBytes = 5_000,
            ),
            request = MemoryAdmissionRequest(
                resource = MemoryAdmissionResource.MODEL,
                estimate = estimate(100),
                residency = RuntimeResidencySnapshot(
                    modelLoaded = false,
                    residentContexts = 2,
                    activeGeneration = false,
                    queuedGenerations = 0,
                ),
            ),
        )

        assertEquals(MemoryAdmissionDecision.Allow, decision)
    }

    private fun contextRequest(peakBytes: Long, residentContexts: Int): MemoryAdmissionRequest = MemoryAdmissionRequest(
        resource = MemoryAdmissionResource.CONTEXT,
        estimate = estimate(peakBytes),
        residency = RuntimeResidencySnapshot(
            modelLoaded = true,
            residentContexts = residentContexts,
            activeGeneration = false,
            queuedGenerations = 0,
        ),
    )

    private fun estimate(peakBytes: Long): MemoryCostEstimate = MemoryCostEstimate(
        residentBytes = if (peakBytes == Long.MAX_VALUE) peakBytes else peakBytes / 2,
        peakIncrementalBytes = peakBytes,
        source = MemoryCostSource.CANDIDATE,
        profileId = "test-profile",
    )

    private fun assertReject(reason: MemoryAdmissionRejectReason, decision: MemoryAdmissionDecision) {
        assertEquals(MemoryAdmissionDecision.Reject(reason), decision)
    }
}
