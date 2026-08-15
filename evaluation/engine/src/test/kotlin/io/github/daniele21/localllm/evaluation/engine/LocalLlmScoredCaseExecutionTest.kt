package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
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
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
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
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
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
import io.github.daniele21.localllm.evaluation.evaluators.ExactMatchEvaluator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLlmScoredCaseExecutionTest {
    @Test
    fun `scores only terminal answer output through the normal generation path`() = runBlocking {
        val case = case()
        val client = FakeClient(
            terminal = GenerationEvent.Completed(
                requestId = REQUEST_ID,
                output = "reasoning plus final wrapper",
                reasoningOutput = "Paris is likely because...",
                answerOutput = "Paris",
                metrics = metrics(),
            ),
        )
        val execution = execution(client, case) { _, _, binding, sessionId ->
            request(binding, sessionId)
        }

        val result = execution.execute(CONFIG, CASE_ID, BINDING, SESSION_ID) as EvaluationStepResult.Success

        assertEquals(EvaluationCaseStatus.SCORED, result.value.status)
        assertEquals(EvaluatorOutcomeCode.CORRECT, result.value.outcome?.code)
        assertEquals(REQUEST_ID, result.value.requestId)
        assertEquals(REQUEST_ID, client.generatedRequest?.requestId)
    }

    @Test
    fun `request identity mismatch fails before generation`() = runBlocking {
        val client = FakeClient(terminal = null)
        val execution = execution(client, case()) { _, _, binding, _ ->
            request(binding, SessionId("wrong-session"))
        }

        val result = execution.execute(CONFIG, CASE_ID, BINDING, SESSION_ID) as EvaluationStepResult.Failure

        assertEquals(EvaluationFailureStage.GENERATION, result.failure.stage)
        assertEquals(EvaluationFailureCode.INVALID_CONFIGURATION, result.failure.code)
        assertEquals(CASE_ID, result.failure.caseId)
        assertEquals(null, client.generatedRequest)
    }

    @Test
    fun `terminal runtime failure remains typed generation failure`() = runBlocking {
        val client = FakeClient(
            terminal = GenerationEvent.Failed(
                requestId = REQUEST_ID,
                error = io.github.daniele21.localllm.contracts.LocalLlmError.NativeRuntime("fixture failure"),
            ),
        )
        val execution = execution(client, case()) { _, _, binding, sessionId -> request(binding, sessionId) }

        val result = execution.execute(CONFIG, CASE_ID, BINDING, SESSION_ID) as EvaluationStepResult.Failure

        assertEquals(EvaluationFailureStage.GENERATION, result.failure.stage)
        assertEquals(EvaluationFailureCode.RUNTIME_FAILURE, result.failure.code)
        assertEquals(CASE_ID, result.failure.caseId)
    }

    private fun execution(client: LocalLlmClient, case: EvaluationDatasetCaseV1, requestFactory: EvaluationCaseGenerationRequestFactory) =
        LocalLlmScoredCaseExecution(
            client = client,
            caseSource = EvaluationCaseDefinitionSource { _, caseId -> case.takeIf { it.id == caseId } },
            requestFactory = requestFactory,
        )

    private fun case() = EvaluationDatasetCaseV1(
        id = CASE_ID,
        categoryId = EvaluationCategoryId("general"),
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Capital of France?")),
        expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "Paris"),
        evaluator = EvaluatorSpec(
            type = EvaluatorType.EXACT_MATCH,
            version = ExactMatchEvaluator.VERSION,
            parameters = mapOf(
                ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_SENSITIVE,
                ExactMatchEvaluator.PARAM_WHITESPACE to ExactMatchEvaluator.WHITESPACE_EXACT,
            ),
        ),
    )

    private fun request(binding: EvaluationRuntimeBinding, sessionId: SessionId) = GenerationRequest(
        requestId = REQUEST_ID,
        sessionId = sessionId,
        applicationId = binding.applicationId,
        useCaseId = binding.useCaseId,
        input = GenerationInput.Text("Capital of France?"),
    )

    private class FakeClient(private val terminal: GenerationEvent?) : LocalLlmClient {
        var generatedRequest: GenerationRequest? = null

        override fun runtimeSnapshot() = RuntimeSnapshot(RuntimeState.READY, MODEL_DIGEST, 0, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId) = PrepareResult(true, MODEL_DIGEST, "fixture")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId) = SESSION_ID

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
            generatedRequest = request
            terminal?.let(listener::onEvent)
            return object : GenerationHandle {
                override val requestId = request.requestId

                override fun cancel() = Unit
            }
        }

        override fun closeSession(sessionId: SessionId) = Unit
    }

    private companion object {
        val CASE_ID = EvaluationCaseId("case-1")
        val SESSION_ID = SessionId("session-1")
        val REQUEST_ID = RequestId("request-1")
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
                orderedCaseIds = listOf(CASE_ID),
            ),
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
        val BINDING = EvaluationRuntimeBinding(APPLICATION_ID, USE_CASE_ID, CONFIG.model, CONFIG.executionProfile)

        fun metrics() = GenerationMetrics(
            queueMs = 0,
            modelLoadMs = null,
            timeToFirstTokenMs = 1,
            totalMs = 2,
            inputTokens = 4,
            outputTokens = 1,
            decodeTokensPerSecond = 2.0,
        )
    }
}
