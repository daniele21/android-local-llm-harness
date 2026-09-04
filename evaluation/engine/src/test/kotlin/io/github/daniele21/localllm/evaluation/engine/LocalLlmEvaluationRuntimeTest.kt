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
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmEvaluationRuntimeTest {
    @Test
    fun `cold preparation unloads residency and prepares exact selected model`() = runBlocking {
        val config = config(loadPolicy = EvaluationModelLoadPolicy.REQUIRE_COLD_LOAD)
        val client = FakeClient(loadedModel = OTHER_DIGEST)
        val runtime = runtime(
            client = client,
            config = config,
            residencyControl = EvaluationModelResidencyControl {
                client.events += "unload"
                client.loadedModel = null
                true
            },
        )

        val result = runtime.prepare(config)

        assertTrue(result is EvaluationStepResult.Success)
        assertEquals(listOf("unload", "prepare"), client.events)
        assertEquals(SELECTED_DIGEST, client.loadedModel)
    }

    @Test
    fun `preparation rejects a runtime that reports a different artifact`() = runBlocking {
        val config = config()
        val client = FakeClient(prepareDigest = OTHER_DIGEST)
        val result = runtime(client, config).prepare(config) as EvaluationStepResult.Failure

        assertEquals(EvaluationFailureStage.MODEL_PREPARATION, result.failure.stage)
        assertEquals(EvaluationFailureCode.RUNTIME_FAILURE, result.failure.code)
    }

    @Test
    fun `unscored warmup uses normal generation in an isolated stateless session`() = runBlocking {
        val config = config(warmupPolicy = EvaluationWarmupPolicy.ONE_UNSCORED_GENERATION)
        val client = FakeClient()
        val warmup = LocalLlmUnscoredWarmupExecution(client) { current, binding, sessionId ->
            GenerationRequest(
                requestId = RequestId("warmup-${current.runId.value}"),
                sessionId = sessionId,
                applicationId = binding.applicationId,
                useCaseId = binding.useCaseId,
                input = GenerationInput.Text("deterministic warmup"),
            )
        }
        val runtime = runtime(client, config, warmupExecution = warmup)

        assertTrue(runtime.prepare(config) is EvaluationStepResult.Success)
        assertTrue(runtime.warmup(config) is EvaluationStepResult.Success)

        assertEquals(listOf("prepare", "create:session-1", "generate:warmup-run-1", "close:session-1"), client.events)
        assertEquals(SessionKind.STATELESS, client.createdOptions.single().kind)
        assertTrue(client.activeSessions.isEmpty())
    }

    @Test
    fun `every scored case receives a distinct session closed before the next case`() = runBlocking {
        val config = config(caseIds = listOf("case-a", "case-b"))
        val client = FakeClient()
        val seenSessions = mutableListOf<SessionId>()
        val runtime = runtime(
            client = client,
            config = config,
            scoredCaseExecution = EvaluationScoredCaseExecutionPort { _, caseId, _, sessionId ->
                client.events += "case:${caseId.value}:${sessionId.value}"
                seenSessions += sessionId
                EvaluationStepResult.Success(scored(caseId))
            },
        )

        assertTrue(runtime.prepare(config) is EvaluationStepResult.Success)
        assertTrue(runtime.execute(config, EvaluationCaseId("case-a")) is EvaluationStepResult.Success)
        assertTrue(runtime.execute(config, EvaluationCaseId("case-b")) is EvaluationStepResult.Success)

        assertNotEquals(seenSessions[0], seenSessions[1])
        assertEquals(
            listOf(
                "prepare",
                "create:session-1",
                "case:case-a:session-1",
                "close:session-1",
                "create:session-2",
                "case:case-b:session-2",
                "close:session-2",
            ),
            client.events,
        )
        assertEquals(listOf(SessionKind.STATELESS, SessionKind.STATELESS), client.createdOptions.map { it.kind })
        assertEquals(SELECTED_DIGEST, client.loadedModel)
        assertTrue(client.activeSessions.isEmpty())
    }

    @Test
    fun `failed case execution still closes its isolated session`() = runBlocking {
        val config = config()
        val client = FakeClient()
        val failure = EvaluationFailure(
            stage = EvaluationFailureStage.GENERATION,
            code = EvaluationFailureCode.RUNTIME_FAILURE,
            caseId = EvaluationCaseId("case-a"),
        )
        val runtime = runtime(
            client = client,
            config = config,
            scoredCaseExecution = EvaluationScoredCaseExecutionPort { _, _, _, _ ->
                EvaluationStepResult.Failure(failure)
            },
        )

        assertTrue(runtime.prepare(config) is EvaluationStepResult.Success)
        val result = runtime.execute(config, EvaluationCaseId("case-a")) as EvaluationStepResult.Failure

        assertEquals(failure, result.failure)
        assertEquals(listOf("prepare", "create:session-1", "close:session-1"), client.events)
        assertTrue(client.activeSessions.isEmpty())
    }

    private fun runtime(
        client: FakeClient,
        config: EvaluationRunConfig,
        residencyControl: EvaluationModelResidencyControl? = null,
        warmupExecution: EvaluationWarmupExecutionPort = EvaluationWarmupExecutionPort { _, _, _ ->
            EvaluationStepResult.Success(Unit)
        },
        scoredCaseExecution: EvaluationScoredCaseExecutionPort = EvaluationScoredCaseExecutionPort { _, caseId, _, _ ->
            EvaluationStepResult.Success(scored(caseId))
        },
    ): LocalLlmEvaluationRuntime {
        val binding = EvaluationRuntimeBinding(
            applicationId = APPLICATION_ID,
            useCaseId = USE_CASE_ID,
            model = config.model,
            executionProfile = config.executionProfile,
        )
        return LocalLlmEvaluationRuntime(
            client = client,
            bindingSource = FixedEvaluationRuntimeBindingSource(listOf(binding)),
            warmupExecution = warmupExecution,
            scoredCaseExecution = scoredCaseExecution,
            residencyControl = residencyControl,
        )
    }

    private fun config(
        caseIds: List<String> = listOf("case-a"),
        loadPolicy: EvaluationModelLoadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
        warmupPolicy: EvaluationWarmupPolicy = EvaluationWarmupPolicy.NONE,
    ): EvaluationRunConfig {
        val dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("fixture"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        )
        return EvaluationRunConfig(
            runId = EvaluationRunId("run-1"),
            model = EvaluationModelIdentity(
                artifactDigest = SELECTED_DIGEST,
                modelProfileId = "supported-model",
                quantization = "Q4_K_M",
            ),
            dataset = dataset,
            sampling = SamplingSelection.create(
                dataset = dataset,
                policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
                seed = 0,
                orderedCaseIds = caseIds.map(::EvaluationCaseId),
            ),
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
            loadPolicy = loadPolicy,
            warmupPolicy = warmupPolicy,
            caseTimeoutMs = 30_000,
        )
    }

    private class FakeClient(var loadedModel: ModelDigest? = null, private val prepareDigest: ModelDigest = SELECTED_DIGEST) :
        LocalLlmClient {
        val events = mutableListOf<String>()
        val activeSessions = linkedSetOf<SessionId>()
        val createdOptions = mutableListOf<SessionOptions>()
        private var nextSession = 0

        override fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
            state = if (loadedModel == null) RuntimeState.IDLE else RuntimeState.READY,
            loadedModel = loadedModel,
            activeSessions = activeSessions.size,
            queuedRequests = 0,
        )

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
            events += "prepare"
            loadedModel = prepareDigest
            return PrepareResult(ready = true, modelDigest = prepareDigest, detail = "fixture")
        }

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId =
            createSession(applicationId, useCaseId, SessionOptions())

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId {
            nextSession += 1
            val sessionId = SessionId("session-$nextSession")
            activeSessions += sessionId
            createdOptions += options
            events += "create:${sessionId.value}"
            return sessionId
        }

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
            events += "generate:${request.requestId.value}"
            listener.onEvent(
                GenerationEvent.Completed(
                    requestId = request.requestId,
                    output = "ignored warmup output",
                    metrics = GenerationMetrics(
                        queueMs = 0,
                        modelLoadMs = null,
                        timeToFirstTokenMs = 1,
                        totalMs = 2,
                        inputTokens = 1,
                        outputTokens = 1,
                        decodeTokensPerSecond = 1.0,
                    ),
                ),
            )
            return object : GenerationHandle {
                override val requestId: RequestId = request.requestId

                override fun cancel() = Unit
            }
        }

        override fun closeSession(sessionId: SessionId) {
            events += "close:${sessionId.value}"
            activeSessions -= sessionId
        }
    }

    private companion object {
        val APPLICATION_ID = ApplicationId("evaluation")
        val USE_CASE_ID = UseCaseId("direct-v1")
        val SELECTED_DIGEST = ModelDigest("a".repeat(64))
        val OTHER_DIGEST = ModelDigest("b".repeat(64))

        fun scored(caseId: EvaluationCaseId) = EvaluationCaseResult(
            caseId = caseId,
            categoryId = EvaluationCategoryId("general"),
            evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
            status = EvaluationCaseStatus.SCORED,
            outcome = EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT),
            requestId = null,
            metrics = EvaluationCaseMetrics(),
        )
    }
}
