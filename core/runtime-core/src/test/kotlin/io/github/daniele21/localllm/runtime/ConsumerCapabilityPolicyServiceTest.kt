package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerCapabilityErrorCode
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.ConsumerReasoningPreference
import io.github.daniele21.localllm.contracts.ConsumerSelectionRequest
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseCapabilities
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.contracts.UseCaseReadiness
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.InferencePreset
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ReasoningStreamProtocol
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseProfile
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConsumerCapabilityPolicyServiceTest {
    @Test
    fun `authorized discovery returns only use-case policy and has no verification side effect`() {
        val fixture = CapabilityFixture(verified = true)

        val result = fixture.service.discover(fixture.applicationId, fixture.useCaseId)

        val capabilities = (result as ConsumerCapabilityResult.Available).capabilities
        assertEquals(fixture.useCaseId, capabilities.useCaseId)
        assertEquals(UseCaseReadiness.READY, capabilities.readiness)
        assertEquals(listOf(fixture.deterministicRef), capabilities.presets.map { it.ref })
        assertEquals(fixture.deterministicRef, capabilities.defaultPreset)
        assertEquals(setOf(ConsumerOutputConstraintKind.JSON_SCHEMA), capabilities.outputConstraints)
        assertEquals(setOf(SessionKind.STATELESS), capabilities.sessionKinds)
        assertEquals(0, fixture.store.verifyCalls)
        assertEquals(1, fixture.store.findCalls)
    }

    @Test
    fun `unauthorized application is rejected before profile resolution or model-store access`() {
        val fixture = CapabilityFixture(verified = true)
        val otherApplication = ApplicationId("other-app")

        val result = fixture.service.discover(otherApplication, fixture.useCaseId)

        val rejected = result as ConsumerCapabilityResult.Rejected
        assertEquals(ConsumerCapabilityErrorCode.USE_CASE_NOT_ALLOWED, rejected.code)
        assertEquals(0, fixture.registry.resolveCalls)
        assertEquals(0, fixture.store.findCalls)
    }

    @Test
    fun `missing bound model is exposed as unavailable without fallback or verification`() {
        val fixture = CapabilityFixture(verified = null)

        val result = fixture.service.discover(fixture.applicationId, fixture.useCaseId)

        val capabilities = (result as ConsumerCapabilityResult.Available).capabilities
        assertEquals(UseCaseReadiness.UNAVAILABLE_MODEL, capabilities.readiness)
        assertEquals(0, fixture.store.verifyCalls)
    }

    @Test
    fun `unverified installed model requires preparation rather than discovery verification`() {
        val fixture = CapabilityFixture(verified = false)

        val result = fixture.service.discover(fixture.applicationId, fixture.useCaseId)

        val capabilities = (result as ConsumerCapabilityResult.Available).capabilities
        assertEquals(UseCaseReadiness.AVAILABLE_REQUIRES_PREPARATION, capabilities.readiness)
        assertEquals(0, fixture.store.verifyCalls)
    }

    @Test
    fun `omitted preset resolves host default and preserves resolved model binding`() {
        val fixture = CapabilityFixture(verified = true)
        val discovered = fixture.capabilities()

        val decision = fixture.service.validateSelection(
            fixture.applicationId,
            fixture.useCaseId,
            ConsumerSelectionRequest(
                capabilityRevision = discovered.capabilityRevision,
                outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            ),
        )

        val accepted = decision as ConsumerPolicyDecision.Accepted
        assertEquals(fixture.deterministicRef, accepted.preset?.ref)
        assertEquals(fixture.digest, accepted.resolvedUseCase.model.artifact.digest)
        assertEquals(EffectiveConsumerReasoningMode.DISABLED, accepted.reasoningMode)
    }

    @Test
    fun `preset present in internal profile but not exposed by consumer policy is rejected`() {
        val fixture = CapabilityFixture(verified = true)

        val decision = fixture.service.validateSelection(
            fixture.applicationId,
            fixture.useCaseId,
            ConsumerSelectionRequest(
                preset = fixture.qualityRef,
                outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            ),
        )

        val rejected = decision as ConsumerPolicyDecision.Rejected
        assertEquals(ConsumerCapabilityErrorCode.PRESET_NOT_ALLOWED, rejected.code)
    }

    @Test
    fun `stale capability revision is rejected after readiness changes`() {
        val fixture = CapabilityFixture(verified = false)
        val staleRevision = fixture.capabilities().capabilityRevision
        fixture.store.setVerified(fixture.digest, true)
        val freshRevision = fixture.capabilities().capabilityRevision
        assertNotEquals(staleRevision, freshRevision)

        val decision = fixture.service.validateSelection(
            fixture.applicationId,
            fixture.useCaseId,
            ConsumerSelectionRequest(
                capabilityRevision = staleRevision,
                outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            ),
        )

        val rejected = decision as ConsumerPolicyDecision.Rejected
        assertEquals(ConsumerCapabilityErrorCode.STALE_CAPABILITY, rejected.code)
    }

    @Test
    fun `surfaced reasoning request is rejected when use-case policy does not expose it`() {
        val fixture = CapabilityFixture(verified = true)

        val decision = fixture.service.validateSelection(
            fixture.applicationId,
            fixture.useCaseId,
            ConsumerSelectionRequest(
                reasoning = ConsumerReasoningPreference.SURFACED_IF_SUPPORTED,
                outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            ),
        )

        val rejected = decision as ConsumerPolicyDecision.Rejected
        assertEquals(ConsumerCapabilityErrorCode.REASONING_NOT_ALLOWED, rejected.code)
    }

    @Test
    fun `output and session choices are intersected with host and resolved use-case policy`() {
        val fixture = CapabilityFixture(verified = true)

        val outputDecision = fixture.service.validateSelection(
            fixture.applicationId,
            fixture.useCaseId,
            ConsumerSelectionRequest(outputConstraint = ConsumerOutputConstraintKind.TEXT),
        )
        val sessionDecision = fixture.service.validateSelection(
            fixture.applicationId,
            fixture.useCaseId,
            ConsumerSelectionRequest(
                outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
                sessionKind = SessionKind.CONVERSATIONAL,
            ),
        )

        assertEquals(
            ConsumerCapabilityErrorCode.OUTPUT_NOT_ALLOWED,
            (outputDecision as ConsumerPolicyDecision.Rejected).code,
        )
        assertEquals(
            ConsumerCapabilityErrorCode.SESSION_KIND_NOT_ALLOWED,
            (sessionDecision as ConsumerPolicyDecision.Rejected).code,
        )
    }

    @Test
    fun `mismatched resolved binding is rejected instead of leaking another use case`() {
        val fixture = CapabilityFixture(verified = true)
        fixture.registry.override = fixture.resolved.copy(
            binding = fixture.resolved.binding.copy(useCaseId = UseCaseId("another-use-case")),
        )

        val result = fixture.service.discover(fixture.applicationId, fixture.useCaseId)

        val rejected = result as ConsumerCapabilityResult.Rejected
        assertEquals(ConsumerCapabilityErrorCode.USE_CASE_NOT_ALLOWED, rejected.code)
        assertEquals(0, fixture.store.findCalls)
    }

    @Test
    fun `capability contract has no model path url digest or selector fields`() {
        val fields = UseCaseCapabilities::class.java.declaredFields.map { it.name.lowercase() }

        assertFalse(fields.any { "path" in it })
        assertFalse(fields.any { "url" in it })
        assertFalse(fields.any { "digest" in it })
        assertFalse(fields.any { it == "modelid" || it == "model" || it == "modelselector" })
    }

    @Test
    fun `surfaced reasoning is accepted only when both policy and effective generation support it`() {
        val fixture = CapabilityFixture(
            verified = true,
            reasoningCapability = ConsumerReasoningCapability.SURFACED_OPTIONAL,
            surfacedReasoning = true,
        )

        val decision = fixture.service.validateSelection(
            fixture.applicationId,
            fixture.useCaseId,
            ConsumerSelectionRequest(
                reasoning = ConsumerReasoningPreference.SURFACED_IF_SUPPORTED,
                outputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            ),
        )

        val accepted = decision as ConsumerPolicyDecision.Accepted
        assertEquals(EffectiveConsumerReasoningMode.SURFACED, accepted.reasoningMode)
    }

    @Test
    fun `policy that advertises unsupported output is marked incompatible`() {
        val fixture = CapabilityFixture(
            verified = true,
            policyOutputConstraints = setOf(
                ConsumerOutputConstraintKind.JSON_SCHEMA,
                ConsumerOutputConstraintKind.TEXT,
            ),
        )

        val capabilities = fixture.capabilities()

        assertEquals(UseCaseReadiness.INCOMPATIBLE, capabilities.readiness)
    }
}

private class CapabilityFixture(
    verified: Boolean?,
    reasoningCapability: ConsumerReasoningCapability = ConsumerReasoningCapability.NOT_SUPPORTED,
    surfacedReasoning: Boolean = false,
    policyOutputConstraints: Set<ConsumerOutputConstraintKind> = setOf(ConsumerOutputConstraintKind.JSON_SCHEMA),
) {
    val applicationId = ApplicationId("ombra")
    val useCaseId = UseCaseId("document-pii-detection")
    val digest = ModelDigest("a".repeat(64))
    val deterministicRef = InferencePresetRef(InferencePresetId("deterministic"), 1)
    val qualityRef = InferencePresetRef(InferencePresetId("quality"), 1)

    private val deterministicPreset = preset(
        ref = deterministicRef,
        thinking = surfacedReasoning,
    )
    private val qualityPreset = preset(ref = qualityRef, thinking = false)
    val resolved = resolvedUseCase()
    val registry = FixtureProfileRegistry(resolved)
    val store = FixtureModelStore().apply {
        if (verified != null) put(digest, verified)
    }
    private val policyRegistry = InMemoryConsumerUseCasePolicyRegistry(
        listOf(
            ConsumerUseCasePolicy(
                applicationId = applicationId,
                useCaseId = useCaseId,
                revision = "ombra-pii-v1",
                exposedPresets = setOf(deterministicRef),
                defaultPreset = deterministicRef,
                reasoning = reasoningCapability,
                outputConstraints = policyOutputConstraints,
                sessionKinds = setOf(SessionKind.STATELESS),
                limits = ConsumerLimits(
                    maxInputCharacters = 32_768,
                    maxConversationMessages = 1,
                    maxJsonSchemaCharacters = 32_768,
                ),
            ),
        ),
    )
    val service = ConsumerCapabilityPolicyService(registry, store, policyRegistry)

    fun capabilities(): UseCaseCapabilities =
        (service.discover(applicationId, useCaseId) as ConsumerCapabilityResult.Available).capabilities

    private fun resolvedUseCase(): ResolvedUseCase {
        val artifact = GgufArtifact(
            digest = digest,
            fileName = "model.gguf",
            sizeBytes = 7L,
            architecture = "qwen35",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported("fixture"),
        )
        val model = GgufModelProfile(
            id = "qwen35-pii",
            artifact = artifact,
            contextSize = 4_096,
            batchSize = 256,
            microBatchSize = 128,
            cpuThreads = 4,
            batchThreads = 4,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = "pii-profile",
            modelProfileId = model.id,
            systemPromptVersion = "v1",
            generationDefaults = deterministicPreset.generation,
            outputMode = OutputMode.JSON_SCHEMA,
            cachePolicy = UseCaseCachePolicy(
                retainModelWarmMs = 30_000,
                reuseStatelessContext = false,
                enablePrefixSnapshot = false,
                enableDeterministicResultCache = false,
            ),
            healthSuiteId = "pii-health-v1",
            systemPrompt = "Return PII as structured JSON.",
            presets = listOf(deterministicPreset, qualityPreset),
            defaultPreset = deterministicRef,
        )
        val binding = AppModelBinding(
            applicationId = applicationId,
            useCaseId = useCaseId,
            useCaseProfileId = useCase.id,
        )
        return ResolvedUseCase(binding, useCase, model)
    }

    private fun preset(ref: InferencePresetRef, thinking: Boolean): InferencePreset =
        InferencePreset(
            ref = ref,
            generation = GenerationDefaults(
                maxOutputTokens = 256,
                temperature = 0f,
                topP = 1f,
                topK = 0,
                thinkingMode = if (thinking) ThinkingMode.ENABLED else ThinkingMode.DISABLED,
                seed = 42L,
                reasoningStreamProtocol = if (thinking) {
                    ReasoningStreamProtocol.QWEN35_THINK_TAGS
                } else {
                    ReasoningStreamProtocol.NONE
                },
            ),
            systemPromptVersion = "v1",
            systemPrompt = "Return PII as structured JSON.",
            allowedOutputModes = setOf(OutputMode.JSON_SCHEMA),
        )
}

private class FixtureProfileRegistry(private val expected: ResolvedUseCase) : ModelProfileRegistry {
    var resolveCalls: Int = 0
    var override: ResolvedUseCase? = null

    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        resolveCalls += 1
        if (applicationId != expected.binding.applicationId || useCaseId != expected.binding.useCaseId) {
            error("No binding")
        }
        return override ?: expected
    }
}

private class FixtureModelStore : ModelStore {
    private val models = linkedMapOf<ModelDigest, StoredModel>()
    var findCalls: Int = 0
    var verifyCalls: Int = 0

    fun put(digest: ModelDigest, verified: Boolean) {
        models[digest] = StoredModel(
            digest = digest,
            file = File("/private/harness/models/${digest.sha256}/model.gguf"),
            sizeBytes = 7L,
            verified = verified,
        )
    }

    fun setVerified(digest: ModelDigest, verified: Boolean) {
        val existing = checkNotNull(models[digest])
        models[digest] = existing.copy(verified = verified)
    }

    override fun find(digest: ModelDigest): StoredModel? {
        findCalls += 1
        return models[digest]
    }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult {
        verifyCalls += 1
        error("Capability discovery must not verify models")
    }

    override fun remove(digest: ModelDigest): Boolean = models.remove(digest) != null

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(
        modelCount = models.size,
        totalBytes = models.values.sumOf(StoredModel::sizeBytes),
        entries = models.values.toList(),
    )
}
