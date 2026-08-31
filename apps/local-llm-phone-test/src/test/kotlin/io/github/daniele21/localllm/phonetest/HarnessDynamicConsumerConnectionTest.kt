package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCaseRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessDynamicConsumerConnectionTest {
    @Test
    fun `dynamic ombra consumer requires authorized application and enabled current binding`() {
        val state = state()

        assertTrue(state.isAuthorizedOmbraConsumer(APPLICATION_ID))
        assertFalse(
            state.copy(
                applications = listOf(state.applications.single().copy(state = ApplicationRegistrationState.DISABLED)),
            ).isAuthorizedOmbraConsumer(APPLICATION_ID),
        )
        assertFalse(
            state.copy(
                bindings = listOf(state.bindings.single().copy(revision = 2, enabled = false, isDefault = false)),
            ).isAuthorizedOmbraConsumer(APPLICATION_ID),
        )
    }

    @Test
    fun `ombra runtime and policy preserve dynamic application identity`() {
        val artifact = CuratedModelCatalog.releases.first().artifact
        val imported = ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )

        val resolved = HarnessSharedRuntimeBindings.resolveOmbra(imported, APPLICATION_ID)
        val policy = HarnessOmbraConsumerPolicy.create(APPLICATION_ID)

        assertEquals(APPLICATION_ID, resolved.binding.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.ombraUseCaseId, resolved.binding.useCaseId)
        assertEquals(APPLICATION_ID, policy.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.ombraUseCaseId, policy.useCaseId)
    }

    private fun state(): HostControlPlaneState {
        val application = RegisteredApplication(
            applicationId = APPLICATION_ID,
            packageName = "com.example.dynamic",
            signerSha256 = "a".repeat(64),
            displayName = "Dynamic consumer",
            state = ApplicationRegistrationState.AUTHORIZED,
            firstSeenAtEpochMs = 1,
            lastSeenAtEpochMs = 1,
        )
        val useCase = UseCaseDefinition(
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
            displayName = "Document PII detection",
            description = "Detect PII locally",
            requirements = UseCaseRequirements(
                outputMode = OutputMode.JSON_SCHEMA,
                sessionKind = SessionKind.STATELESS,
                reasoningSupported = false,
                minimumContextTokens = 4_096,
            ),
            state = UseCaseDefinitionState.ACTIVE,
            revision = 1,
        )
        val binding = ApplicationUseCaseBinding(
            bindingId = "connection:${APPLICATION_ID.value}:${HarnessSharedRuntimeBindings.ombraUseCaseId.value}",
            applicationId = APPLICATION_ID,
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
            revision = 1,
            enabled = true,
            isDefault = true,
        )
        return HostControlPlaneState(
            applications = listOf(application),
            useCases = listOf(useCase),
            bindings = listOf(binding),
        )
    }

    private companion object {
        val APPLICATION_ID = ApplicationId("dynamic-consumer")
    }
}
