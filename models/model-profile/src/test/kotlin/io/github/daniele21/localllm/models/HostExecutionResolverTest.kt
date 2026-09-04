package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostExecutionResolverTest {
    @Test
    fun `default exposed preset resolves exact pinned execution identity`() {
        val profile = profile("qwen-2b", "a", 900, 8_192)
        val resolver = resolver(
            preset = preset("balanced", 3, modelProfileId = profile.id),
            exposure = exposure("balanced", 3, isDefault = true),
        )

        val result = resolver.resolve(request(), environment(profile)) as HostExecutionResolution.Success
        val execution = result.execution

        assertEquals(2, execution.useCaseRevision)
        assertEquals(7, execution.bindingRevision)
        assertEquals("balanced", execution.presetId)
        assertEquals(3, execution.presetRevision)
        assertEquals(profile.id, execution.modelProfileId)
        assertEquals(profile.artifact.digest, execution.modelDigest)
        assertEquals(4_096, execution.contextTokens)
    }

    @Test
    fun `stale requested preset revision fails with exposed revision evidence`() {
        val profile = profile("qwen-2b", "a", 900, 8_192)
        val resolver = resolver(
            preset = preset("balanced", 3, modelProfileId = profile.id),
            exposure = exposure("balanced", 3, isDefault = true),
        )

        val result = resolver.resolve(
            request(presetId = "balanced", presetRevision = 2),
            environment(profile),
        ) as HostExecutionResolution.Failure

        assertEquals(HostExecutionFailureCode.STALE_PRESET_REVISION, result.code)
        assertEquals(3, result.evidence.exposedPresetRevision)
        assertEquals(7, result.evidence.bindingRevision)
    }

    @Test
    fun `automatic model selection is deterministic and skips incompatible candidates`() {
        val tooSmall = profile("small", "a", 100, 2_048)
        val compatible = profile("medium", "b", 500, 8_192)
        val larger = profile("large", "c", 900, 16_384)
        val resolver = resolver(
            preset = preset("automatic", 1, modelProfileId = null),
            exposure = exposure("automatic", 1, isDefault = true),
        )

        val forward = resolver.resolve(
            request(),
            environment(tooSmall, compatible, larger),
        ) as HostExecutionResolution.Success
        val reversed = resolver.resolve(
            request(),
            environment(larger, compatible, tooSmall),
        ) as HostExecutionResolution.Success

        assertEquals("medium", forward.execution.modelProfileId)
        assertEquals(forward.execution.modelProfileId, reversed.execution.modelProfileId)
        assertEquals(
            setOf(ModelCandidateRejectionReason.INSUFFICIENT_CONTEXT),
            forward.execution.evidence.candidateRejections.single().reasons,
        )
    }

    @Test
    fun `explicit assigned model missing installation returns evidence`() {
        val profile = profile("qwen-2b", "a", 900, 8_192)
        val resolver = resolver(
            preset = preset("balanced", 1, modelProfileId = profile.id),
            exposure = exposure("balanced", 1, isDefault = true),
        )

        val result = resolver.resolve(
            request(),
            environment(profile, installed = emptySet()),
        ) as HostExecutionResolution.Failure

        assertEquals(HostExecutionFailureCode.MODEL_NOT_INSTALLED, result.code)
        assertEquals(profile.id, result.evidence.requestedModelProfileId)
        assertEquals(
            setOf(ModelCandidateRejectionReason.NOT_INSTALLED),
            result.evidence.candidateRejections.single().reasons,
        )
    }

    @Test
    fun `unsupported prefix snapshot rejects assigned model before runtime`() {
        val profile = profile(
            id = "qwen-2b",
            digestSeed = "a",
            sizeBytes = 900,
            contextSize = 8_192,
            supportsPrefixSnapshot = false,
        )
        val resolver = resolver(
            preset = preset(
                id = "cached",
                revision = 1,
                modelProfileId = profile.id,
                enablePrefixSnapshot = true,
            ),
            exposure = exposure("cached", 1, isDefault = true),
        )

        val result = resolver.resolve(request(), environment(profile)) as HostExecutionResolution.Failure

        assertEquals(HostExecutionFailureCode.MODEL_INCOMPATIBLE, result.code)
        assertTrue(
            ModelCandidateRejectionReason.PREFIX_SNAPSHOT_UNSUPPORTED in
                result.evidence.candidateRejections.single().reasons,
        )
    }

    @Test
    fun `disabled latest binding does not fall back to older enabled revision`() {
        val profile = profile("qwen-2b", "a", 900, 8_192)
        val state = baseState(
            preset = preset("balanced", 1, modelProfileId = profile.id),
            exposure = exposure("balanced", 1, isDefault = true, bindingRevision = 7),
            bindings = listOf(binding(6, enabled = true), binding(7, enabled = false)),
        )
        val resolver = HostExecutionResolver(InMemoryHostControlPlaneStore(state))

        val result = resolver.resolve(request(), environment(profile)) as HostExecutionResolution.Failure

        assertEquals(HostExecutionFailureCode.USE_CASE_NOT_BOUND, result.code)
        assertEquals(7, result.evidence.bindingRevision)
    }

    private fun resolver(preset: UseCasePresetDefinition, exposure: StoredPresetExposure): HostExecutionResolver = HostExecutionResolver(
        InMemoryHostControlPlaneStore(baseState(preset, exposure)),
    )

    private fun baseState(
        preset: UseCasePresetDefinition,
        exposure: StoredPresetExposure,
        bindings: List<ApplicationUseCaseBinding> = listOf(binding(7)),
    ): HostControlPlaneState = HostControlPlaneState(
        applications = listOf(application()),
        useCases = listOf(useCase()),
        presets = listOf(preset),
        bindings = bindings,
        exposures = listOf(exposure),
    )

    private fun request(presetId: String? = null, presetRevision: Int? = null): HostExecutionRequest =
        HostExecutionRequest(APP_ID, USE_CASE_ID, presetId, presetRevision)

    private fun environment(
        vararg profiles: GgufModelProfile,
        installed: Set<ModelDigest> = profiles.map { it.artifact.digest }.toSet(),
    ): HostExecutionEnvironment = HostExecutionEnvironment(
        modelProfiles = profiles.toList(),
        installedModelDigests = installed,
        backendId = "llama.cpp",
        backendRevision = "1",
    )

    private fun application(): RegisteredApplication = RegisteredApplication(
        applicationId = APP_ID,
        packageName = "io.github.redactguard",
        signerSha256 = "f".repeat(64),
        displayName = "RedactGuard",
        state = ApplicationRegistrationState.AUTHORIZED,
        firstSeenAtEpochMs = 10,
        lastSeenAtEpochMs = 20,
    )

    private fun useCase(): UseCaseDefinition = UseCaseDefinition(
        useCaseId = USE_CASE_ID,
        displayName = "Document PII detection",
        description = "Detect PII",
        requirements = UseCaseRequirements(
            outputMode = OutputMode.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
            reasoningSupported = false,
            minimumContextTokens = 4_096,
        ),
        state = UseCaseDefinitionState.ACTIVE,
        revision = 2,
    )

    private fun binding(revision: Int, enabled: Boolean = true): ApplicationUseCaseBinding = ApplicationUseCaseBinding(
        bindingId = BINDING_ID,
        applicationId = APP_ID,
        useCaseId = USE_CASE_ID,
        revision = revision,
        enabled = enabled,
    )

    private fun exposure(presetId: String, presetRevision: Int, isDefault: Boolean, bindingRevision: Int = 7): StoredPresetExposure =
        StoredPresetExposure(
            bindingId = BINDING_ID,
            bindingRevision = bindingRevision,
            presetId = presetId,
            presetRevision = presetRevision,
            isDefault = isDefault,
        )

    private fun preset(
        id: String,
        revision: Int,
        modelProfileId: String?,
        enablePrefixSnapshot: Boolean = false,
    ): UseCasePresetDefinition = UseCasePresetDefinition(
        useCaseId = USE_CASE_ID,
        metadata = PresetConsumerMetadata(id, revision, id, "Preset $id"),
        creationSource = PresetCreationSource.CUSTOM,
        state = PresetLifecycleState.PUBLISHED,
        execution = PresetExecutionPolicy(
            modelProfileId = modelProfileId,
            inferencePreset = InferencePresetRef(InferencePresetId("$id-generation"), revision),
            contextTokens = 4_096,
            cachePolicy = UseCaseCachePolicy(
                retainModelWarmMs = 60_000,
                reuseStatelessContext = false,
                enablePrefixSnapshot = enablePrefixSnapshot,
                enableDeterministicResultCache = false,
            ),
        ),
    )

    private fun profile(
        id: String,
        digestSeed: String,
        sizeBytes: Long,
        contextSize: Int,
        supportsPrefixSnapshot: Boolean = false,
    ): GgufModelProfile = GgufModelProfile(
        id = id,
        artifact = GgufArtifact(
            digest = ModelDigest(digestSeed.repeat(64)),
            fileName = "$id.gguf",
            sizeBytes = sizeBytes,
            architecture = "qwen2",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported(id),
        ),
        contextSize = contextSize,
        batchSize = 128,
        microBatchSize = 64,
        cpuThreads = 4,
        batchThreads = 4,
        gpuLayers = 0,
        runtimeCapabilities = RuntimeCapabilityProfile(
            requiredBackendId = "llama.cpp",
            requiredBackendRevision = "1",
            supportsPrefixSnapshot = supportsPrefixSnapshot,
        ),
    )

    private companion object {
        val APP_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
        const val BINDING_ID = "binding-redactguard-pii"
    }
}
