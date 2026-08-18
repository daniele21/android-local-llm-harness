package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HarnessDecisionRulesTest {
    @Test
    fun `rule engine covers required host control-plane conditions`() {
        val repository = RecordingDecisionRepository()
        val engine = engine(repository)

        engine.reconcile(
            conditions = listOf(
                HarnessDecisionCondition.PendingConsumer(APP_ID),
                HarnessDecisionCondition.MissingApplicationConfiguration(APP_ID),
                HarnessDecisionCondition.ConfigurationUnavailable(APP_ID, USE_CASE_ID, HarnessUnavailableResource.BINDING),
                HarnessDecisionCondition.ConfigurationUnavailable(APP_ID, USE_CASE_ID, HarnessUnavailableResource.USE_CASE),
                HarnessDecisionCondition.ConfigurationUnavailable(
                    APP_ID,
                    USE_CASE_ID,
                    HarnessUnavailableResource.PRESET,
                    PRESET_ID,
                    presetRevision = 2,
                    bindingRevision = 3,
                ),
                HarnessDecisionCondition.BoundModelUnavailable(
                    APP_ID,
                    USE_CASE_ID,
                    HarnessBoundModelProblem.MISSING,
                    modelDigest = MODEL_A,
                    presetId = PRESET_ID,
                    presetRevision = 2,
                    bindingRevision = 3,
                ),
                HarnessDecisionCondition.BoundModelUnavailable(
                    APP_ID,
                    USE_CASE_ID,
                    HarnessBoundModelProblem.INCOMPATIBLE,
                    modelDigest = MODEL_A,
                    presetId = PRESET_ID,
                    presetRevision = 2,
                    bindingRevision = 3,
                ),
                HarnessDecisionCondition.BrokenPreset(APP_ID, USE_CASE_ID, PRESET_ID, 2, bindingRevision = 3),
                HarnessDecisionCondition.ProtectedResidentModelConflict(APP_ID, USE_CASE_ID, MODEL_A, MODEL_B),
                HarnessDecisionCondition.CriticalMemoryPressure(
                    APP_ID,
                    USE_CASE_ID,
                    MODEL_A,
                    HarnessMemoryPressureOutcome.ACTIVATION_REVOKED,
                ),
                HarnessDecisionCondition.SignerReauthorizationRequired(APP_ID),
            ),
            nowEpochMs = 100,
        )

        assertEquals(
            setOf(
                "NEW_CONSUMER_PENDING",
                "APPLICATION_CONFIGURATION_REQUIRED",
                "BINDING_UNAVAILABLE",
                "USE_CASE_UNAVAILABLE",
                "PRESET_UNAVAILABLE",
                "BOUND_MODEL_MISSING",
                "BOUND_MODEL_INCOMPATIBLE",
                "PRESET_BROKEN",
                "PROTECTED_MODEL_CONFLICT",
                "CRITICAL_MEMORY_PRESSURE",
                "SIGNER_REAUTHORIZATION_REQUIRED",
            ),
            repository.unresolved().map { it.code }.toSet(),
        )
        assertTrue(repository.unresolved().all { it.action != HarnessDecisionAction.NONE })
    }

    @Test
    fun `equivalent unresolved condition is deduplicated`() {
        val repository = RecordingDecisionRepository()
        val ids = CountingDecisionIdFactory()
        val engine = HarnessDecisionRuleEngine(repository, ids)
        val condition = HarnessDecisionCondition.MissingApplicationConfiguration(APP_ID)

        engine.reconcile(listOf(condition), nowEpochMs = 100)
        val first = repository.unresolved().single()
        val upsertsAfterFirstPass = repository.upsertCalls

        engine.reconcile(listOf(condition), nowEpochMs = 200)

        assertEquals(first, repository.unresolved().single())
        assertEquals(upsertsAfterFirstPass, repository.upsertCalls)
        assertEquals(1, ids.createdCount)
    }

    @Test
    fun `decision resolves only after underlying condition disappears`() {
        val repository = RecordingDecisionRepository()
        val engine = engine(repository)
        val condition = HarnessDecisionCondition.BrokenPreset(APP_ID, USE_CASE_ID, PRESET_ID, 2)

        engine.reconcile(listOf(condition), nowEpochMs = 100)
        assertEquals(1, repository.unresolved().size)

        engine.reconcile(listOf(condition), nowEpochMs = 150)
        assertEquals(1, repository.unresolved().size)

        engine.reconcile(emptyList(), nowEpochMs = 200)

        assertTrue(repository.unresolved().isEmpty())
        assertEquals(200, repository.recent().single().resolvedAtEpochMs)
    }

    @Test
    fun `condition recurrence after resolution opens a new decision identity`() {
        val repository = RecordingDecisionRepository()
        val engine = engine(repository)
        val condition = HarnessDecisionCondition.SignerReauthorizationRequired(APP_ID)

        engine.reconcile(listOf(condition), nowEpochMs = 100)
        val firstId = repository.unresolved().single().decisionId
        engine.reconcile(emptyList(), nowEpochMs = 200)
        engine.reconcile(listOf(condition), nowEpochMs = 300)
        val reopened = repository.unresolved().single()

        assertNotEquals(firstId, reopened.decisionId)
        assertEquals(300, reopened.createdAtEpochMs)
        assertEquals(2, repository.recent().size)
        assertEquals(1, repository.recent().count { it.isResolved })
    }

    @Test
    fun `memory pressure decision keeps only bounded operational evidence`() {
        val repository = RecordingDecisionRepository()
        val engine = engine(repository)

        engine.reconcile(
            listOf(
                HarnessDecisionCondition.CriticalMemoryPressure(
                    applicationId = APP_ID,
                    useCaseId = USE_CASE_ID,
                    modelDigest = MODEL_A,
                    outcome = HarnessMemoryPressureOutcome.MODEL_EVICTED,
                ),
            ),
            nowEpochMs = 100,
        )

        val event = repository.unresolved().single()
        assertEquals(HarnessDecisionCategory.WARNING, event.category)
        assertEquals(HarnessDecisionAction.REVIEW_MEMORY_PRESSURE, event.action)
        assertEquals(setOf("outcome", "modelDigest"), event.evidence.keys)
        assertTrue(event.evidence.values.none { "prompt" in it.lowercase() || "document" in it.lowercase() })
    }

    private fun engine(repository: RecordingDecisionRepository): HarnessDecisionRuleEngine =
        HarnessDecisionRuleEngine(repository, CountingDecisionIdFactory())

    private companion object {
        val APP_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
        val PRESET_ID = InferencePresetId("quality")
        val MODEL_A = ModelDigest("a".repeat(64))
        val MODEL_B = ModelDigest("b".repeat(64))
    }
}

private class CountingDecisionIdFactory : HarnessDecisionIdFactory {
    private val counter = AtomicInteger(0)

    val createdCount: Int
        get() = counter.get()

    override fun newId(): HarnessDecisionId = HarnessDecisionId("decision-${counter.incrementAndGet()}")
}

private class RecordingDecisionRepository : HarnessDecisionRepository {
    private val events = linkedMapOf<HarnessDecisionId, HarnessDecisionEvent>()
    var upsertCalls: Int = 0
        private set

    override fun upsert(event: HarnessDecisionEvent) {
        upsertCalls += 1
        events[event.decisionId] = event
    }

    override fun unresolved(limit: Int): List<HarnessDecisionEvent> =
        events.values.filterNot(HarnessDecisionEvent::isResolved).take(limit)

    override fun recent(limit: Int): List<HarnessDecisionEvent> =
        events.values.sortedByDescending(HarnessDecisionEvent::createdAtEpochMs).take(limit)

    override fun find(decisionId: HarnessDecisionId): HarnessDecisionEvent? = events[decisionId]
}
