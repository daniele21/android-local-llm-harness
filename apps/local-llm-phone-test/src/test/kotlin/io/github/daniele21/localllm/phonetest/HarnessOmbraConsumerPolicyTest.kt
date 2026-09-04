package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessOmbraConsumerPolicyTest {
    @Test
    fun `legacy ombra binding remains host-owned json-schema use case during migration`() {
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
    fun `redactguard binding uses independent application identity with same host-owned pii preset`() {
        val model = curatedModel()
        val resolved =
            HarnessSharedRuntimeBindings.resolveOmbra(
                model,
                HarnessSharedRuntimeBindings.redactGuardApplicationId,
            )

        assertEquals(HarnessSharedRuntimeBindings.redactGuardApplicationId, resolved.binding.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.ombraUseCaseId, resolved.binding.useCaseId)
        assertEquals(model.digest, resolved.model.artifact.digest)
        assertEquals(OutputMode.JSON_SCHEMA, resolved.useCase.outputMode)
        assertEquals(HarnessSharedRuntimeBindings.ombraDefaultPreset, resolved.useCase.defaultPreset)
    }

    @Test
    fun `pii capability policy exposes only constrained defaults for redactguard`() {
        val policy = HarnessOmbraConsumerPolicy.create(HarnessSharedRuntimeBindings.redactGuardApplicationId)

        assertEquals(HarnessSharedRuntimeBindings.redactGuardApplicationId, policy.applicationId)
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
    fun `phone binding registry resolves redactguard only through activated document pii binding`() {
        val registry = HarnessPhoneBindingRegistry()
        val model = curatedModel()
        val activated =
            HarnessSharedRuntimeBindings.resolveOmbra(
                model,
                HarnessSharedRuntimeBindings.redactGuardApplicationId,
            )
        registry.selectedModel = model
        registry.installActivationBinding(
            activationId = UseCaseActivationId("redactguard-pii-activation"),
            applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId,
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
            resolved = activated,
        )

        val redactGuard =
            registry.resolve(
                HarnessSharedRuntimeBindings.redactGuardApplicationId,
                HarnessSharedRuntimeBindings.ombraUseCaseId,
            )

        assertEquals(model.digest, redactGuard.model.artifact.digest)
        assertEquals(HarnessSharedRuntimeBindings.redactGuardApplicationId, redactGuard.binding.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.ombraUseCaseId, redactGuard.binding.useCaseId)
        val failure = assertThrows(IllegalStateException::class.java) {
            registry.resolve(
                HarnessSharedRuntimeBindings.redactGuardApplicationId,
                HarnessSharedRuntimeBindings.consoleUseCaseId,
            )
        }
        assertTrue(failure.message?.contains("control-plane activation") == true)
    }

    @Test
    fun `target package sets are exact by build type`() {
        assertEquals(
            setOf(HarnessSharedRuntimeBindings.REDACTGUARD_DEBUG_PACKAGE),
            HarnessSharedRuntimeBindings.redactGuardPackages(debugHost = true),
        )
        assertEquals(
            setOf(HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE),
            HarnessSharedRuntimeBindings.redactGuardPackages(debugHost = false),
        )
        assertEquals(setOf(HarnessSharedRuntimeBindings.ombraUseCaseId), HarnessSharedRuntimeBindings.redactGuardUseCases)
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
