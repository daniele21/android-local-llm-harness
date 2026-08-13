package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerContentType
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedSelection
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.ConsumerSelectionRequest
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConsumerLocalLlmFacadeTest {
    @Test
    fun `consumer completes discover prepare session generate close without host-private fields`() {
        val fixture = FacadeFixture()
        val capabilities = (fixture.facade.capabilities(fixture.useCaseId) as ConsumerCapabilityResult.Available).capabilities
        val prepared = fixture.facade.prepare(
            ConsumerPrepareRequest(
                useCaseId = fixture.useCaseId,
                selection = ConsumerSelectionRequest(capabilityRevision = capabilities.capabilityRevision),
            ),
        ) as ConsumerPrepareResult.Prepared
        assertEquals(fixture.presetRef, prepared.selection.preset)
        assertEquals(ConsumerOutputConstraintKind.JSON_SCHEMA, prepared.selection.outputConstraint)
        assertEquals(SessionKind.STATELESS, prepared.selection.sessionKind)

        val session = fixture.facade.createSession(prepared.selection.preparedId) as ConsumerSessionResult.Created
        val events = mutableListOf<ConsumerGenerationEvent>()
        val start = fixture.facade.generate(
            ConsumerGenerationRequest(
                requestId = RequestId("consumer-request"),
                sessionId = session.sessionId,
                input = ConsumerGenerationInput.Text("document"),
                outputConstraint = ConsumerOutputConstraint.JsonSchema("{\"type\":\"object\"}"),
            ),
            ConsumerGenerationListener(events::add),
        )

        assertTrue(start is ConsumerGenerationStartResult.Accepted)
        assertEquals(1, events.filterIsInstance<ConsumerGenerationEvent.Started>().size)
        assertFalse(events.filterIsInstance<ConsumerGenerationEvent.ContentDelta>().any { it.contentType == ConsumerContentType.REASONING })
        assertEquals("{\"pii\":[]}", events.filterIsInstance<ConsumerGenerationEvent.Completed>().single().answer)
        assertNull(events.filterIsInstance<ConsumerGenerationEvent.Completed>().single().surfacedReasoning)
        val legacyRequest = checkNotNull(fixture.delegate.lastRequest)
        assertEquals(fixture.applicationId, legacyRequest.applicationId)
        assertEquals(fixture.useCaseId, legacyRequest.useCaseId)
        assertEquals(fixture.presetRef, legacyRequest.overrides.preset)
        assertEquals(ThinkingMode.DISABLED, legacyRequest.overrides.thinkingMode)
        assertNull(legacyRequest.overrides.temperature)
        assertNull(legacyRequest.overrides.maxOutputTokens)

        fixture.facade.closeSession(session.sessionId)
        assertEquals(listOf(session.sessionId), fixture.delegate.closedSessions)
    }

    @Test
    fun `stale capability is rejected before legacy preparation`() {
        val fixture = FacadeFixture()
        val stale = (fixture.facade.capabilities(fixture.useCaseId) as ConsumerCapabilityResult.Available).capabilities
        fixture.store.setVerified(fixture.digest, false)

        val result = fixture.facade.prepare(
            ConsumerPrepareRequest(
                useCaseId = fixture.useCaseId,
                selection = ConsumerSelectionRequest(capabilityRevision = stale.capabilityRevision),
            ),
        ) as ConsumerPrepareResult.Rejected

        assertEquals(ConsumerErrorCode.STALE_CAPABILITY, result.failure.code)
        assertEquals(0, fixture.delegate.prepareCalls)
    }

    @Test
    fun `generation enforces prepared input and output policy before delegate`() {
        val fixture = FacadeFixture(maxInputCharacters = 5, maxJsonSchemaCharacters = 5)
        val prepared = fixture.facade.prepare(ConsumerPrepareRequest(fixture.useCaseId)) as ConsumerPrepareResult.Prepared
        val session = fixture.facade.createSession(prepared.selection.preparedId) as ConsumerSessionResult.Created

        val oversized = fixture.facade.generate(
            ConsumerGenerationRequest(
                RequestId("too-large"),
                session.sessionId,
                ConsumerGenerationInput.Text("123456"),
                ConsumerOutputConstraint.JsonSchema("{}"),
            ),
            ConsumerGenerationListener {},
        ) as ConsumerGenerationStartResult.Rejected
        val wrongOutput = fixture.facade.generate(
            ConsumerGenerationRequest(
                RequestId("wrong-output"),
                session.sessionId,
                ConsumerGenerationInput.Text("12345"),
                ConsumerOutputConstraint.Text,
            ),
            ConsumerGenerationListener {},
        ) as ConsumerGenerationStartResult.Rejected

        assertEquals(ConsumerErrorCode.INVALID_INPUT, oversized.failure.code)
        assertEquals(ConsumerErrorCode.OUTPUT_NOT_ALLOWED, wrongOutput.failure.code)
        assertEquals(0, fixture.delegate.generateCalls)
    }

    @Test
    fun `public consumer surface does not expose caller model artifact or raw tuning authority`() {
        val forbidden = listOf("application", "model", "digest", "path", "url", "temperature", "topk", "topp", "overrides")
        val publicTypes = listOf(
            ConsumerPreparedSelection::class.java,
            ConsumerGenerationRequest::class.java,
            ConsumerGenerationEvent.Started::class.java,
        )
        val fieldNames = publicTypes.flatMap { type -> type.declaredFields.map { it.name.lowercase() } }
        assertFalse(fieldNames.any { field -> forbidden.any(field::contains) })

        val parameterTypes = ConsumerLocalLlmClient::class.java.declaredMethods
            .flatMap { it.parameterTypes.toList() }
            .map(Class<*>::getSimpleName)
        assertFalse(parameterTypes.contains("ApplicationId"))
        assertFalse(parameterTypes.contains("ModelDigest"))
    }
}

private class FacadeFixture(
    maxInputCharacters: Int = 32_768,
    maxJsonSchemaCharacters: Int = 32_768,
) {
    val applicationId = ApplicationId("ombra")
    val useCaseId = UseCaseId("document-pii-detection")
    val digest = ModelDigest("b".repeat(64))
    val presetRef = InferencePresetRef(InferencePresetId("deterministic"), 1)
    private val preset = InferencePreset(
        ref = presetRef,
        generation = GenerationDefaults(
            maxOutputTokens = 256,
            temperature = 0f,
            topP = 1f,
            topK = 0,
            thinkingMode = ThinkingMode.DISABLED,
            seed = 42L,
            reasoningStreamProtocol = ReasoningStreamProtocol.NONE,
        ),
        systemPromptVersion = "v1",
        systemPrompt = "Return structured PII.",
        allowedOutputModes = setOf(OutputMode.JSON_SCHEMA),
    )
    private val resolved = resolvedUseCase()
    private val registry = FacadeProfileRegistry(resolved)
    val store = FacadeModelStore(digest)
    private val policy = InMemoryConsumerUseCasePolicyRegistry(
        listOf(
            ConsumerUseCasePolicy(
                applicationId = applicationId,
                useCaseId = useCaseId,
                revision = "ombra-pii-v1",
                exposedPresets = setOf(presetRef),
                defaultPreset = presetRef,
                reasoning = ConsumerReasoningCapability.NOT_SUPPORTED,
                outputConstraints = setOf(ConsumerOutputConstraintKind.JSON_SCHEMA),
                defaultOutputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
                sessionKinds = setOf(SessionKind.STATELESS),
                defaultSessionKind = SessionKind.STATELESS,
                limits = ConsumerLimits(maxInputCharacters, 1, maxJsonSchemaCharacters),
            ),
        ),
    )
    private val service = ConsumerCapabilityPolicyService(registry, store, policy)
    val delegate = FacadeLocalClient(digest, presetRef)
    val facade = ConsumerLocalLlmFacade(applicationId, service, delegate)

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
            generationDefaults = preset.generation,
            outputMode = OutputMode.JSON_SCHEMA,
            cachePolicy = UseCaseCachePolicy(
                retainModelWarmMs = 30_000,
                reuseStatelessContext = false,
                enablePrefixSnapshot = false,
                enableDeterministicResultCache = false,
            ),
            healthSuiteId = "pii-health-v1",
            systemPrompt = "Return structured PII.",
            presets = listOf(preset),
            defaultPreset = presetRef,
        )
        return ResolvedUseCase(
            AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase,
            model,
        )
    }
}

private class FacadeProfileRegistry(private val resolved: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        check(applicationId == resolved.binding.applicationId && useCaseId == resolved.binding.useCaseId)
        return resolved
    }
}

private class FacadeModelStore(digest: ModelDigest) : ModelStore {
    private val models = linkedMapOf<ModelDigest, StoredModel>()

    init {
        setVerified(digest, true)
    }

    fun setVerified(digest: ModelDigest, verified: Boolean) {
        models[digest] = StoredModel(
            digest,
            File("/private/models/${digest.sha256}/model.gguf"),
            7L,
            verified,
        )
    }

    override fun find(digest: ModelDigest): StoredModel? = models[digest]

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult = error("Not used")

    override fun remove(digest: ModelDigest): Boolean = models.remove(digest) != null

    override fun snapshot() = ModelStoreSnapshot(models.size, models.values.sumOf { it.sizeBytes }, models.values.toList())
}

private class FacadeLocalClient(
    private val digest: ModelDigest,
    private val presetRef: InferencePresetRef,
) : LocalLlmClient {
    var prepareCalls = 0
    var generateCalls = 0
    var lastRequest: GenerationRequest? = null
    val closedSessions = mutableListOf<SessionId>()
    private val sessionId = SessionId("consumer-session")

    override fun runtimeSnapshot() = RuntimeSnapshot(RuntimeState.READY, digest, 0, 0)

    override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
        prepareCalls += 1
        return PrepareResult(true, digest, "ready")
    }

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId = sessionId

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId {
        assertEquals(SessionKind.STATELESS, options.kind)
        return sessionId
    }

    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
        generateCalls += 1
        lastRequest = request
        listener.onEvent(GenerationEvent.Queued(request.requestId, 1))
        listener.onEvent(GenerationEvent.Prepared(request.requestId, digest, metadata()))
        listener.onEvent(GenerationEvent.Started(request.requestId, digest))
        listener.onEvent(GenerationEvent.TextDelta(request.requestId, "hidden", 1, GenerationContentType.REASONING))
        listener.onEvent(GenerationEvent.TextDelta(request.requestId, "{\"pii\":[]}", 2, GenerationContentType.ANSWER))
        listener.onEvent(
            GenerationEvent.Completed(
                requestId = request.requestId,
                output = "hidden{\"pii\":[]}",
                metrics = GenerationMetrics(0, 0, 0, 1, 1, 2, 2.0, stopReason = StopReason.END_OF_GENERATION),
                reasoningOutput = "hidden",
                answerOutput = "{\"pii\":[]}",
            ),
        )
        return object : GenerationHandle {
            override val requestId = request.requestId
            override fun cancel() = Unit
        }
    }

    override fun closeSession(sessionId: SessionId) {
        closedSessions += sessionId
    }

    private fun metadata() = EffectiveGenerationMetadata(
        preset = presetRef,
        temperature = 0f,
        topP = 1f,
        topK = 0,
        repeatPenalty = 1f,
        repeatLastN = 64,
        requestedSeedPolicy = SeedPolicyType.FIXED,
        effectiveSeed = 42L,
        maxOutputTokens = 256,
        contextSize = 4_096,
        promptTokenCount = 16,
        chatTemplateId = "qwen35",
        chatTemplateSource = ChatTemplateSource.GGUF,
        systemPromptVersion = "v1",
        thinkingMode = ThinkingMode.DISABLED,
    )
}
