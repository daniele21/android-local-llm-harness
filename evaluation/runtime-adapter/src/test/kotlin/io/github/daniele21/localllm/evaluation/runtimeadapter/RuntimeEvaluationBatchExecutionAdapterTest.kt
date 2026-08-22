package io.github.daniele21.localllm.evaluation.runtimeadapter

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationInput
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import io.github.daniele21.localllm.evaluation.engine.EvaluationBatchExecutionPort
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseBatch
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseDefinitionSource
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseGenerationRequestFactory
import io.github.daniele21.localllm.evaluation.engine.EvaluationRuntimeBinding
import io.github.daniele21.localllm.evaluation.engine.EvaluationRuntimeBindingSource
import io.github.daniele21.localllm.evaluation.engine.EvaluationStepResult
import io.github.daniele21.localllm.evaluation.engine.EvaluationTelemetryCorrelationPort
import io.github.daniele21.localllm.evaluation.evaluators.ExactMatchEvaluator
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchCaseResult
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchClient
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchHandle
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchListener
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchOutcome
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEvaluationBatchExecutionAdapterTest {
    @Test
    fun `native runtime batch preserves order scores outputs and closes isolated sessions`() = runBlocking {
        val client = FakeClient()
        val runtime = FakeBatchClient(
            RuntimeEvaluationBatchOutcome.Completed(
                listOf(
                    completed(REQUEST_A, "Paris"),
                    completed(REQUEST_B, "Rome"),
                ),
            ),
        )
        val adapter = adapter(client, runtime)

        val result = adapter.execute(CONFIG, EvaluationCaseBatch(0, CASE_IDS)) as EvaluationStepResult.Success

        assertEquals(CASE_IDS, result.value.map { it.caseId })
        assertEquals(listOf(EvaluatorOutcomeCode.CORRECT, EvaluatorOutcomeCode.CORRECT), result.value.map { it.outcome?.code })
        assertEquals(listOf(REQUEST_A, REQUEST_B), result.value.map { it.requestId })
        assertEquals(listOf(SessionKind.STATELESS, SessionKind.STATELESS), client.createdOptions.map(SessionOptions::kind))
        assertEquals(client.createdSessions.reversed(), client.closedSessions)
        assertEquals(listOf(REQUEST_A, REQUEST_B), runtime.request?.requests?.map(GenerationRequest::requestId))
        assertEquals(RequestId("evaluation-batch:run-1:0"), runtime.request?.batchId)
    }

    @Test
    fun `one case tail delegates to serial compatibility port without runtime batch`() = runBlocking {
        val client = FakeClient()
        val runtime = FakeBatchClient(null)
        var fallbackBatch: EvaluationCaseBatch? = null
        val fallback = EvaluationBatchExecutionPort { _, batch ->
            fallbackBatch = batch
            EvaluationStepResult.Success(emptyList())
        }
        val adapter = adapter(client, runtime, fallback)
        val tail = EvaluationCaseBatch(1, listOf(CASE_A))

        val result = adapter.execute(CONFIG, tail)

        assertTrue(result is EvaluationStepResult.Success)
        assertEquals(tail, fallbackBatch)
        assertNull(runtime.request)
        assertTrue(client.createdSessions.isEmpty())
    }

    @Test
    fun `runtime result reordering fails closed and still closes every session`() = runBlocking {
        val client = FakeClient()
        val runtime = FakeBatchClient(
            RuntimeEvaluationBatchOutcome.Completed(
                listOf(
                    completed(REQUEST_B, "Rome"),
                    completed(REQUEST_A, "Paris"),
                ),
            ),
        )

        val result = adapter(client, runtime).execute(CONFIG, EvaluationCaseBatch(0, CASE_IDS)) as EvaluationStepResult.Failure

        assertEquals(EvaluationFailureCode.INVALID_CONFIGURATION, result.failure.code)
        assertEquals(CASE_A, result.failure.caseId)
        assertEquals(client.createdSessions.reversed(), client.closedSessions)
    }

    @Test
    fun `batch timeout cancels runtime unit and returns one typed timeout per case`() = runBlocking {
        val client = FakeClient()
        val runtime = FakeBatchClient(null)

        val result = adapter(client, runtime).execute(
            CONFIG.copy(caseTimeoutMs = 20),
            EvaluationCaseBatch(0, CASE_IDS),
        ) as EvaluationStepResult.Success

        assertEquals(listOf(EvaluationCaseStatus.TIMEOUT, EvaluationCaseStatus.TIMEOUT), result.value.map { it.status })
        assertEquals(listOf(EvaluationFailureCode.CASE_TIMEOUT, EvaluationFailureCode.CASE_TIMEOUT), result.value.map { it.failure?.code })
        assertTrue(runtime.cancelCalled)
        assertEquals(client.createdSessions.reversed(), client.closedSessions)
    }

    @Test
    fun `request identity mismatch fails before runtime submission and cleans sessions`() = runBlocking {
        val client = FakeClient()
        val runtime = FakeBatchClient(null)
        val adapter = RuntimeEvaluationBatchExecutionAdapter(
            client = client,
            batchClient = runtime,
            bindingSource = bindingSource(),
            caseSource = caseSource(),
            requestFactory = EvaluationCaseGenerationRequestFactory { _, case, binding, _ ->
                request(case.id, binding, SessionId("wrong"))
            },
            singletonFallback = emptyFallback(),
        )

        val result = adapter.execute(CONFIG, EvaluationCaseBatch(0, CASE_IDS)) as EvaluationStepResult.Failure

        assertEquals(EvaluationFailureCode.INVALID_CONFIGURATION, result.failure.code)
        assertNull(runtime.request)
        assertEquals(client.createdSessions.reversed(), client.closedSessions)
    }

    @Test
    fun `cancelled native case becomes typed evaluation cancellation while peers remain scored`() = runBlocking {
        val client = FakeClient()
        val runtime = FakeBatchClient(
            RuntimeEvaluationBatchOutcome.Completed(
                listOf(
                    RuntimeEvaluationBatchCaseResult.Cancelled(REQUEST_A, metrics()),
                    completed(REQUEST_B, "Rome"),
                ),
            ),
        )

        val result = adapter(client, runtime).execute(CONFIG, EvaluationCaseBatch(0, CASE_IDS)) as EvaluationStepResult.Success

        assertEquals(EvaluationCaseStatus.CANCELLED, result.value[0].status)
        assertEquals(EvaluationFailureCode.CANCELLED, result.value[0].failure?.code)
        assertEquals(EvaluationCaseStatus.SCORED, result.value[1].status)
    }

    private fun adapter(
        client: FakeClient,
        runtime: FakeBatchClient,
        fallback: EvaluationBatchExecutionPort = emptyFallback(),
    ) = RuntimeEvaluationBatchExecutionAdapter(
        client = client,
        batchClient = runtime,
        bindingSource = bindingSource(),
        caseSource = caseSource(),
        requestFactory = EvaluationCaseGenerationRequestFactory { _, case, binding, sessionId -> request(case.id, binding, sessionId) },
        singletonFallback = fallback,
        telemetry = EvaluationTelemetryCorrelationPort { requestId ->
            EvaluationCaseMetrics(totalMs = if (requestId == REQUEST_A) 10 else 11, outputTokens = 1)
        },
    )

    private fun bindingSource() = EvaluationRuntimeBindingSource { model, profile ->
        BINDING.takeIf { BINDING.model == model && BINDING.executionProfile == profile }
    }

    private fun caseSource() = EvaluationCaseDefinitionSource { _, caseId -> cases().firstOrNull { it.id == caseId } }

    private fun emptyFallback() = EvaluationBatchExecutionPort { _, _ -> EvaluationStepResult.Success(emptyList()) }

    private fun request(caseId: EvaluationCaseId, binding: EvaluationRuntimeBinding, sessionId: SessionId) = GenerationRequest(
        requestId = if (caseId == CASE_A) REQUEST_A else REQUEST_B,
        sessionId = sessionId,
        applicationId = binding.applicationId,
        useCaseId = binding.useCaseId,
        input = GenerationInput.Text(if (caseId == CASE_A) "Capital of France?" else "Capital of Italy?"),
    )

    private fun cases() = listOf(
        case(CASE_A, "Capital of France?", "Paris"),
        case(CASE_B, "Capital of Italy?", "Rome"),
    )

    private fun case(id: EvaluationCaseId, prompt: String, expected: String) = EvaluationDatasetCaseV1(
        id = id,
        categoryId = EvaluationCategoryId("general"),
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, prompt)),
        expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, expected),
        evaluator = EvaluatorSpec(
            type = EvaluatorType.EXACT_MATCH,
            version = ExactMatchEvaluator.VERSION,
            parameters = mapOf(
                ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_SENSITIVE,
                ExactMatchEvaluator.PARAM_WHITESPACE to ExactMatchEvaluator.WHITESPACE_EXACT,
            ),
        ),
    )

    private fun completed(requestId: RequestId, output: String) = RuntimeEvaluationBatchCaseResult.Completed(
        requestId = requestId,
        output = output,
        metrics = metrics(),
    )

    private fun metrics() = GenerationMetrics(
        queueMs = 0,
        modelLoadMs = null,
        timeToFirstTokenMs = null,
        totalMs = 10,
        inputTokens = 3,
        outputTokens = 1,
        decodeTokensPerSecond = 2.0,
    )

    private class FakeClient : LocalLlmClient {
        val createdSessions = mutableListOf<SessionId>()
        val createdOptions = mutableListOf<SessionOptions>()
        val closedSessions = mutableListOf<SessionId>()
        private var nextSession = 0

        override fun runtimeSnapshot() = RuntimeSnapshot(RuntimeState.READY, MODEL_DIGEST, createdSessions.size - closedSessions.size, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId) = PrepareResult(true, MODEL_DIGEST, "fixture")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId =
            createSession(applicationId, useCaseId, SessionOptions())

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId {
            val id = SessionId("session-${++nextSession}")
            createdSessions += id
            createdOptions += options
            return id
        }

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle = error("serial generation not expected")

        override fun closeSession(sessionId: SessionId) {
            closedSessions += sessionId
        }
    }

    private class FakeBatchClient(private val outcome: RuntimeEvaluationBatchOutcome?) : RuntimeEvaluationBatchClient {
        var request: RuntimeEvaluationBatchRequest? = null
        var cancelCalled = false

        override fun generateEvaluationBatch(
            request: RuntimeEvaluationBatchRequest,
            listener: RuntimeEvaluationBatchListener,
        ): RuntimeEvaluationBatchHandle {
            this.request = request
            outcome?.let(listener::onTerminal)
            return object : RuntimeEvaluationBatchHandle {
                override val batchId: RequestId = request.batchId

                override fun cancel(): Boolean {
                    cancelCalled = true
                    return true
                }

                override fun cancelCase(requestId: RequestId): Boolean = false
            }
        }
    }

    private companion object {
        val CASE_A = EvaluationCaseId("case-a")
        val CASE_B = EvaluationCaseId("case-b")
        val CASE_IDS = listOf(CASE_A, CASE_B)
        val REQUEST_A = RequestId("request-a")
        val REQUEST_B = RequestId("request-b")
        val MODEL_DIGEST = ModelDigest("a".repeat(64))
        val APPLICATION_ID = ApplicationId("evaluation")
        val USE_CASE_ID = UseCaseId("direct-v1")
        val DATASET = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("fixture"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        )
        val CONFIG = EvaluationRunConfig(
            runId = EvaluationRunId("run-1"),
            model = EvaluationModelIdentity(MODEL_DIGEST, "supported-model", "Q4_K_M"),
            dataset = DATASET,
            sampling = SamplingSelection.create(
                dataset = DATASET,
                policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
                seed = 0,
                orderedCaseIds = CASE_IDS,
            ),
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
        val BINDING = EvaluationRuntimeBinding(APPLICATION_ID, USE_CASE_ID, CONFIG.model, CONFIG.executionProfile)
    }
}
