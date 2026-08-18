package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetSuggestionServiceTest {
    private val service = PresetSuggestionService()

    @Test
    fun `zero compatible profiles returns no suggestions with rejection evidence`() {
        val result = service.suggest(
            useCase = useCase(minimumContextTokens = 4_096),
            installedProfiles = listOf(profile("tiny", sizeBytes = 100, contextSize = 2_048)),
            runtimeCapabilities = runtimeCapabilities(),
        )

        assertTrue(result.suggestions.isEmpty())
        assertTrue(result.globalBlockers.isEmpty())
        assertEquals(
            setOf(PresetSuggestionProfileRejection.INSUFFICIENT_CONTEXT),
            result.rejectedProfiles.single().reasons,
        )
    }

    @Test
    fun `one compatible profile produces one balanced draft suggestion`() {
        val result = service.suggest(
            useCase = useCase(),
            installedProfiles = listOf(profile("only", sizeBytes = 200, contextSize = 8_192)),
            runtimeCapabilities = runtimeCapabilities(),
        )

        val suggestion = result.suggestions.single()
        assertEquals(PresetSuggestionOptimization.BALANCED, suggestion.optimization)
        assertEquals("only", suggestion.modelProfileId)
        assertEquals(PresetCreationSource.SUGGESTED, suggestion.creationSource)
        assertEquals(PresetLifecycleState.DRAFT, suggestion.lifecycleState)
    }

    @Test
    fun `multiple compatible profiles produce deterministic fast balanced quality ordering`() {
        val profiles = listOf(
            profile("large", sizeBytes = 900, contextSize = 16_384),
            profile("small", sizeBytes = 100, contextSize = 8_192),
            profile("medium", sizeBytes = 500, contextSize = 8_192),
            profile("larger", sizeBytes = 700, contextSize = 8_192),
        )

        val forward = service.suggest(useCase(), profiles, runtimeCapabilities())
        val reversed = service.suggest(useCase(), profiles.reversed(), runtimeCapabilities())

        assertEquals(
            listOf(
                PresetSuggestionOptimization.FAST,
                PresetSuggestionOptimization.BALANCED,
                PresetSuggestionOptimization.QUALITY,
            ),
            forward.suggestions.map { it.optimization },
        )
        assertEquals(listOf("small", "larger", "large"), forward.suggestions.map { it.modelProfileId })
        assertEquals(forward, reversed)
        assertTrue(forward.suggestions.all { it.lifecycleState == PresetLifecycleState.DRAFT })
    }

    @Test
    fun `two compatible profiles do not fabricate a distinct balanced model`() {
        val result = service.suggest(
            useCase = useCase(),
            installedProfiles = listOf(
                profile("small", sizeBytes = 100, contextSize = 8_192),
                profile("large", sizeBytes = 900, contextSize = 8_192),
            ),
            runtimeCapabilities = runtimeCapabilities(),
        )

        assertEquals(
            listOf(PresetSuggestionOptimization.FAST, PresetSuggestionOptimization.QUALITY),
            result.suggestions.map { it.optimization },
        )
    }

    @Test
    fun `unsupported runtime requirements block all suggestions deterministically`() {
        val result = service.suggest(
            useCase = useCase(reasoningSupported = true, minimumContextTokens = 8_192),
            installedProfiles = listOf(profile("large", sizeBytes = 900, contextSize = 16_384)),
            runtimeCapabilities = PresetSuggestionRuntimeCapabilities(
                supportedOutputModes = setOf(OutputMode.TEXT),
                supportedSessionKinds = setOf(SessionKind.STATEFUL),
                reasoningSupported = false,
                maximumContextTokens = 4_096,
            ),
        )

        assertTrue(result.suggestions.isEmpty())
        assertEquals(
            setOf(
                PresetSuggestionGlobalBlocker.OUTPUT_MODE_UNSUPPORTED,
                PresetSuggestionGlobalBlocker.SESSION_KIND_UNSUPPORTED,
                PresetSuggestionGlobalBlocker.REASONING_UNSUPPORTED,
                PresetSuggestionGlobalBlocker.CONTEXT_LIMIT_UNSUPPORTED,
            ),
            result.globalBlockers,
        )
    }

    private fun useCase(
        minimumContextTokens: Int = 4_096,
        reasoningSupported: Boolean = false,
    ): UseCaseDefinition = UseCaseDefinition(
        useCaseId = UseCaseId("document-pii-detection"),
        displayName = "Document PII detection",
        description = "Detect configured PII in documents",
        requirements = UseCaseRequirements(
            outputMode = OutputMode.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
            reasoningSupported = reasoningSupported,
            minimumContextTokens = minimumContextTokens,
        ),
        state = UseCaseDefinitionState.ACTIVE,
        revision = 1,
    )

    private fun runtimeCapabilities(): PresetSuggestionRuntimeCapabilities = PresetSuggestionRuntimeCapabilities(
        supportedOutputModes = setOf(OutputMode.JSON_SCHEMA),
        supportedSessionKinds = setOf(SessionKind.STATELESS),
        reasoningSupported = false,
        maximumContextTokens = 32_768,
    )

    private fun profile(id: String, sizeBytes: Long, contextSize: Int): GgufModelProfile = GgufModelProfile(
        id = id,
        artifact = GgufArtifact(
            digest = ModelDigest(id.padEnd(64, 'a').take(64)),
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
    )
}
