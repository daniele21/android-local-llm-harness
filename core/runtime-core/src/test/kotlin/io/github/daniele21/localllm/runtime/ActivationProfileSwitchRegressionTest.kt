package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseProfile
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationProfileSwitchRegressionTest {
    private val applicationId = ApplicationId("aura-finance")
    private val schemaUseCaseId = UseCaseId("aura-transaction-schema-inference")
    private val categoryUseCaseId = UseCaseId("aura-transaction-category-classification")
    private val digest = ModelDigest("a".repeat(64))

    @Test
    fun `active same-digest second profile currently blocks resident profile switch`() {
        val modelFile = File.createTempFile("activation-profile-switch", ".gguf").apply {
            writeText("model")
            deleteOnExit()
        }
        val schema = resolved(schemaUseCaseId, "qwen35-aura-import-schema", modelFile)
        val category = resolved(categoryUseCaseId, "qwen35-aura-import-category", modelFile)
        val registry = object : ModelProfileRegistry {
            override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase = when (useCaseId) {
                schemaUseCaseId -> schema
                categoryUseCaseId -> category
                else -> error("Unexpected use case ${useCaseId.value}")
            }
        }
        val activationResidency = ActivationResidencyCoordinator(
            UseCaseActivationLeaseRegistry(ActivationIdFactory { UseCaseActivationId("activation-${System.nanoTime()}") }),
        )
        val delegate = ProfileSwitchBackend()
        val runtime = RuntimeOrchestrator(
            registry = registry,
            modelStore = ProfileSwitchModelStore(modelFile, digest),
            backend = ActivationResidencyInferenceBackend(delegate, activationResidency),
        )

        val schemaActivation = activationResidency.acquire(
            activationRequest("schema-owner", schemaUseCaseId),
            retainModelWarmMs = 30_000,
        ) as ActivationResidencyResult.Success
        assertTrue(runtime.prepare(applicationId, schemaUseCaseId).ready)
        activationResidency.release(schemaActivation.value.activationId, schemaActivation.value.ownerId)

        val categoryActivation = activationResidency.acquire(
            activationRequest("category-owner", categoryUseCaseId),
            retainModelWarmMs = 30_000,
        ) as ActivationResidencyResult.Success
        val categoryPrepare = runtime.prepare(applicationId, categoryUseCaseId)

        assertFalse(categoryPrepare.ready)
        assertTrue(categoryPrepare.detail.contains("protected by an active use-case activation"))
        assertTrue(delegate.loadedProfiles == listOf("qwen35-aura-import-schema"))
        assertFalse(delegate.unloaded)

        activationResidency.release(categoryActivation.value.activationId, categoryActivation.value.ownerId)
        runtime.close()
        modelFile.delete()
    }

    private fun activationRequest(owner: String, useCaseId: UseCaseId) = UseCaseActivationRequest(
        ownerId = ActivationOwnerId(owner),
        applicationId = applicationId,
        useCaseId = useCaseId,
        preset = InferencePresetRef(InferencePresetId("qwen35-json"), 1),
        modelDigest = digest,
        acquiredAtEpochMs = 1_000,
        useCaseRevision = 1,
        bindingRevision = 1,
    )

    private fun resolved(useCaseId: UseCaseId, profileId: String, modelFile: File): ResolvedUseCase {
        val model = GgufModelProfile(
            id = profileId,
            artifact = GgufArtifact(
                digest = digest,
                fileName = "model.gguf",
                sizeBytes = modelFile.length(),
                architecture = "qwen3.5",
                quantization = "Q4_K_M",
                source = ArtifactSource.Imported("diagnostic"),
            ),
            contextSize = 4_096,
            batchSize = 512,
            microBatchSize = 128,
            cpuThreads = 4,
            batchThreads = 4,
            gpuLayers = 0,
            useMmap = true,
            useMlock = false,
        )
        val useCase = UseCaseProfile(
            id = "use-case-${useCaseId.value}",
            modelProfileId = profileId,
            systemPromptVersion = "v1",
            generationDefaults = GenerationDefaults(
                maxOutputTokens = 32,
                temperature = 0f,
                topP = 1f,
                topK = 0,
                seed = 42,
            ),
            outputMode = OutputMode.JSON_SCHEMA,
            cachePolicy = UseCaseCachePolicy(30_000, false, false, false),
            healthSuiteId = "diagnostic",
        )
        return ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = model,
        )
    }
}

private class ProfileSwitchModelStore(private val file: File, private val digest: ModelDigest) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? =
        digest.takeIf { it == this.digest }?.let { StoredModel(it, file, file.length(), verified = false) }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(
        valid = digest == this.digest,
        actualDigest = digest.takeIf { it == this.digest },
        detail = "diagnostic",
    )

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(1, file.length(), emptyList())
}

private data class ProfileSwitchModelHandle(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle

private class ProfileSwitchBackend : InferenceBackend {
    override val id: String = "profile-switch-diagnostic"
    val loadedProfiles = mutableListOf<String>()
    var unloaded = false

    override fun initialize() = Unit

    override fun shutdown() = Unit

    override fun loadModel(source: BackendModelSource, profile: GgufModelProfile): BackendModelHandle =
        ProfileSwitchModelHandle(source.digest, profile.id).also { loadedProfiles += profile.id }

    override fun unloadModel(model: BackendModelHandle) {
        unloaded = true
    }

    override fun modelCapabilities(model: BackendModelHandle): BackendModelCapabilities = error("Not used")

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest): BackendPromptPlan = error("Not used")

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = error("Not used")

    override fun releaseContext(context: BackendContextHandle) = Unit

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome = error("Not used")

    override fun cancel(requestId: String): Boolean = false
}
