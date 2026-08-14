package io.github.daniele21.localllm.evaluation

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.ThinkingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluationIdentityGoldenTest {
    @Test
    fun `ordered sample set digest matches v1 golden`() {
        val digest = CanonicalEvaluationHasher.sampleSetDigest(
            listOf(
                EvaluationCaseId("case-a"),
                EvaluationCaseId("case-b"),
                EvaluationCaseId("case-c"),
            ),
        )

        assertEquals(
            "50a6a262e3ad433c056e364d2491a0bfae47e9355b0cff504fdaeb4ee0b1cbd3",
            digest.sha256,
        )
    }

    @Test
    fun `evaluator set digest matches golden independent of map construction order`() {
        val first = evaluatorIdentity(linkedMapOf("ignoreCase" to "true", "trim" to "true"))
        val second = evaluatorIdentity(linkedMapOf("trim" to "true", "ignoreCase" to "true"))

        val firstDigest = CanonicalEvaluationHasher.evaluatorSetDigest(listOf(first))
        val secondDigest = CanonicalEvaluationHasher.evaluatorSetDigest(listOf(second))

        assertEquals(
            "b0f4e8c87b83219ca5f24afdb08a94aee04dfed61763a29552ad5f61c3826f11",
            firstDigest.sha256,
        )
        assertEquals(firstDigest, secondDigest)
    }

    @Test
    fun `case execution semantics digest matches v1 golden`() {
        val digest = CanonicalEvaluationHasher.caseExecutionSemanticsDigest(
            listOf(
                CaseExecutionSemanticIdentity(EvaluationCaseId("case-a"), "a".repeat(64)),
                CaseExecutionSemanticIdentity(EvaluationCaseId("case-b"), "b".repeat(64)),
            ),
        )

        assertEquals(
            "28a389e7fd54e4fa3e63829f1926a0fc345b68d00dafa5f0795fd314d9202532",
            digest.sha256,
        )
    }

    @Test
    fun `semantic execution fingerprint matches v1 golden`() {
        val identity = EvaluationSemanticExecutionIdentity.create(semanticExecution())

        assertEquals(
            "32a322e4c649da020e07147c68e8a2c67b723160a9d381b6c492978896dc4099",
            identity.fingerprint.sha256,
        )
    }

    @Test
    fun `equivalent clean run construction matches stable v1 run golden`() {
        val first = runIdentity()
        val second = runIdentity()

        assertEquals(
            "60f0e4ba4e82bf83f2236caeb2484c5a9da7aee5c7bc9c33d4e41d646fb953e4",
            first.fingerprint.sha256,
        )
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(first, second)
    }

    private fun evaluatorIdentity(parameters: Map<String, String>) = CaseEvaluatorIdentity(
        caseId = EvaluationCaseId("case-1"),
        evaluator = EvaluatorSpec(
            type = EvaluatorType.EXACT_MATCH,
            version = EvaluatorVersion(1),
            parameters = parameters,
        ),
    )

    private fun runIdentity(): EvaluationRunIdentity = EvaluationRunIdentity.create(
        model = EvaluationModelIdentity(
            artifactDigest = ModelDigest("a".repeat(64)),
            modelProfileId = "qwen35-08b-q4-k-m",
            tier = "0.8B",
            quantization = "Q4_K_M",
        ),
        dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("general-purpose"),
            version = EvaluationDatasetVersion("1.0.0"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        ),
        sampleSetDigest = SampleSetDigest("2".repeat(64)),
        samplingPolicy = SamplingPolicyRef(SamplingPolicyId("stratified"), 1),
        samplingSeed = 7L,
        evaluatorSetDigest = EvaluatorSetDigest("3".repeat(64)),
        semanticExecution = EvaluationSemanticExecutionIdentity.create(semanticExecution()),
        runtimeEnvironment = EvaluationRuntimeEnvironmentIdentity(
            deviceClass = "pixel-class-a",
            androidApiLevel = 36,
            abi = "arm64-v8a",
            backendRevision = "backend-rev",
            harnessBuildIdentity = "commit-123",
            runtimeTuningProfileId = "q35-08b-candidate",
            runtimeTuningProfileVersion = 1,
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
        ),
    )

    private fun semanticExecution(): EvaluationSemanticExecution = EvaluationSemanticExecution(
        profile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct-deterministic"), 1),
        backendRevision = "backend-rev",
        contextSize = 2_048,
        preset = InferencePresetRef(InferencePresetId("precise"), 1),
        thinkingMode = ThinkingMode.DISABLED,
        temperature = 0f,
        topP = 1f,
        topK = 1,
        minP = 0f,
        presencePenalty = 0f,
        repeatPenalty = 1f,
        repeatLastN = 64,
        seedPolicy = SeedPolicyType.FIXED,
        effectiveSeed = 7L,
        maxOutputTokens = 256,
        chatTemplateId = "qwen35",
        chatTemplateSource = ChatTemplateSource.GGUF,
        systemPromptVersion = "eval-v1",
        caseExecutionSemanticsDigest = CaseExecutionSemanticsDigest("4".repeat(64)),
    )
}
