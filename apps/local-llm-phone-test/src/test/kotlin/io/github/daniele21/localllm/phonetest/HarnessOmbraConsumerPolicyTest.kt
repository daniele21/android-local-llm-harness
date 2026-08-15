package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.models.OutputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessOmbraConsumerPolicyTest {
    @Test
    fun `ombra binding is host-owned json-schema use case`() {
        val model = curatedModel()
        val resolved = HarnessSharedRuntimeBindings.resolveOmbra(model)

        assertEquals(HarnessSharedRuntimeBindings.consoleApplicationId, resolved.binding.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.ombraUseCaseId, resolved.binding.useCaseId)
        assertEquals(model.digest, resolved.model.artifact.digest)
        assertEquals(OutputMode.JSON_SCHEMA, resolved.useCase.outputMode)
        assertEquals(HarnessSharedRuntimeBindings.ombraDefaultPreset, resolved.useCase.defaultPreset)
        val preset = resolved.useCase.presets.single { it.ref == HarnessSharedRuntimeBindings.ombraDefaultPreset }
        assertTrue(OutputMode.JSON_SCHEMA in preset.allowedOutputModes)
        assertEquals(io.github.daniele21.localllm.contracts.ThinkingMode.DISABLED, preset.generation.thinkingMode)
    }

    @Test
    fun `ombra capability policy exposes only constrained defaults`() {
        val policy = HarnessOmbraConsumerPolicy.create(HarnessSharedRuntimeBindings.consoleApplicationId)

        assertEquals(HarnessSharedRuntimeBindings.ombraUseCaseId, policy.useCaseId)
        assertEquals(setOf(HarnessSharedRuntimeBindings.ombraDefaultPreset), policy.exposedPresets)
        assertEquals(HarnessSharedRuntimeBindings.ombraDefaultPreset, policy.defaultPreset)
        assertEquals(ConsumerReasoningCapability.NOT_SUPPORTED, policy.reasoning)
        assertEquals(setOf(ConsumerOutputConstraintKind.JSON_SCHEMA), policy.outputConstraints)
        assertEquals(ConsumerOutputConstraintKind.JSON_SCHEMA, policy.defaultOutputConstraint)
        assertEquals(setOf(SessionKind.STATELESS), policy.sessionKinds)
        assertEquals(SessionKind.STATELESS, policy.defaultSessionKind)
        assertEquals(1, policy.limits.maxConversationMessages)
        assertEquals(HarnessOmbraConsumerPolicy.MAX_INPUT_CHARACTERS, policy.limits.maxInputCharacters)
        assertEquals(HarnessOmbraConsumerPolicy.MAX_JSON_SCHEMA_CHARACTERS, policy.limits.maxJsonSchemaCharacters)
    }

    @Test
    fun `phone binding registry resolves both legacy console and ombra without consumer model selection`() {
        val registry = HarnessPhoneBindingRegistry()
        val model = curatedModel()
        registry.selectedModel = model

        val legacy =
            registry.resolve(
                HarnessSharedRuntimeBindings.consoleApplicationId,
                HarnessSharedRuntimeBindings.consoleUseCaseId,
            )
        val ombra =
            registry.resolve(
                HarnessSharedRuntimeBindings.consoleApplicationId,
                HarnessSharedRuntimeBindings.ombraUseCaseId,
            )

        assertEquals(model.digest, legacy.model.artifact.digest)
        assertEquals(model.digest, ombra.model.artifact.digest)
        assertEquals(HarnessSharedRuntimeBindings.consoleUseCaseId, legacy.binding.useCaseId)
        assertEquals(HarnessSharedRuntimeBindings.ombraUseCaseId, ombra.binding.useCaseId)
    }

    private fun curatedModel(): ImportedPhoneModel {
        val artifact = CuratedModelCatalog.releases.first().artifact
        return ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )
    }
}
