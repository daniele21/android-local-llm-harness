package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.integration.servicehost.SigningCertificateSha256
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.HostControlPlaneTransaction
import io.github.daniele21.localllm.models.OutputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessAuraImportUseCasesTest {
    @Test
    fun `aura package topology uses exact release and debug identities`() {
        assertEquals(
            setOf(HarnessSharedRuntimeBindings.AURA_RELEASE_PACKAGE),
            HarnessSharedRuntimeBindings.auraPackages(debugHost = false),
        )
        assertEquals(
            setOf(HarnessSharedRuntimeBindings.AURA_DEBUG_PACKAGE),
            HarnessSharedRuntimeBindings.auraPackages(debugHost = true),
        )
        assertEquals(
            setOf(
                HarnessSharedRuntimeBindings.auraSchemaInferenceUseCaseId,
                HarnessSharedRuntimeBindings.auraCategoryClassificationUseCaseId,
            ),
            HarnessSharedRuntimeBindings.auraUseCases,
        )
    }

    @Test
    fun `aura runtime bindings are host-owned json-schema use cases`() {
        val model = curatedModel()

        for (useCaseId in HarnessSharedRuntimeBindings.auraUseCases) {
            val resolved = HarnessSharedRuntimeBindings.resolveConsumerUseCase(
                model = model,
                applicationId = HarnessSharedRuntimeBindings.auraApplicationId,
                useCaseId = useCaseId,
            )

            assertEquals(HarnessSharedRuntimeBindings.auraApplicationId, resolved.binding.applicationId)
            assertEquals(useCaseId, resolved.binding.useCaseId)
            assertEquals(model.digest, resolved.model.artifact.digest)
            assertEquals(OutputMode.JSON_SCHEMA, resolved.useCase.outputMode)
            assertEquals(HarnessSharedRuntimeBindings.auraDefaultPreset, resolved.useCase.defaultPreset)
            val preset = resolved.useCase.presets.single { it.ref == HarnessSharedRuntimeBindings.auraDefaultPreset }
            assertTrue(OutputMode.JSON_SCHEMA in preset.allowedOutputModes)
            assertEquals(io.github.daniele21.localllm.contracts.ThinkingMode.DISABLED, preset.generation.thinkingMode)
        }

        val schema = HarnessSharedRuntimeBindings.resolveConsumerUseCase(
            model,
            HarnessSharedRuntimeBindings.auraApplicationId,
            HarnessSharedRuntimeBindings.auraSchemaInferenceUseCaseId,
        )
        val category = HarnessSharedRuntimeBindings.resolveConsumerUseCase(
            model,
            HarnessSharedRuntimeBindings.auraApplicationId,
            HarnessSharedRuntimeBindings.auraCategoryClassificationUseCaseId,
        )
        assertEquals(schema.model.id, category.model.id)
        assertFalse(schema.binding.useCaseId == category.binding.useCaseId)
    }

    @Test
    fun `aura capability policies expose only json-schema stateless defaults`() {
        for (useCaseId in HarnessSharedRuntimeBindings.auraUseCases) {
            val policy = HarnessAuraImportConsumerPolicy.create(
                HarnessSharedRuntimeBindings.auraApplicationId,
                useCaseId,
            )

            assertEquals(HarnessSharedRuntimeBindings.auraApplicationId, policy.applicationId)
            assertEquals(useCaseId, policy.useCaseId)
            assertEquals(setOf(HarnessSharedRuntimeBindings.auraDefaultPreset), policy.exposedPresets)
            assertEquals(HarnessSharedRuntimeBindings.auraDefaultPreset, policy.defaultPreset)
            assertEquals(ConsumerReasoningCapability.NOT_SUPPORTED, policy.reasoning)
            assertEquals(setOf(ConsumerOutputConstraintKind.JSON_SCHEMA), policy.outputConstraints)
            assertEquals(ConsumerOutputConstraintKind.JSON_SCHEMA, policy.defaultOutputConstraint)
            assertEquals(setOf(SessionKind.STATELESS), policy.sessionKinds)
            assertEquals(SessionKind.STATELESS, policy.defaultSessionKind)
            assertEquals(1, policy.limits.maxConversationMessages)
            assertEquals(HarnessAuraImportConsumerPolicy.MAX_INPUT_CHARACTERS, policy.limits.maxInputCharacters)
            assertEquals(HarnessAuraImportConsumerPolicy.MAX_JSON_SCHEMA_CHARACTERS, policy.limits.maxJsonSchemaCharacters)
        }
    }

    @Test
    fun `aura seed specs start pending and live trust uses persisted signer after authorization`() {
        val bootstrap = auraPolicy(HarnessSharedRuntimeBindings.AURA_RELEASE_PACKAGE, SIGNER_A)
        val specs = HarnessSharedRuntimePolicy.builtInAuraControlPlaneSpecs(listOf(bootstrap))
        assertEquals(2, specs.size)

        val seeded = specs.fold(HostControlPlaneState()) { state, spec ->
            when (val result = HarnessControlPlaneReconciler(spec).reconcile(state, 10)) {
                is HarnessControlPlaneReconciliationResult.Success -> result.state
                is HarnessControlPlaneReconciliationResult.Conflict -> error("Unexpected ${result.code}")
            }
        }
        val pending = seeded.applications.single()
        assertEquals(HarnessSharedRuntimeBindings.auraApplicationId, pending.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.AURA_RELEASE_PACKAGE, pending.packageName)
        assertEquals(SIGNER_A, pending.signerSha256)
        assertEquals(ApplicationRegistrationState.PENDING, pending.state)
        assertEquals(HarnessSharedRuntimeBindings.auraUseCases, seeded.useCases.map { it.useCaseId }.toSet())
        assertTrue(seeded.useCases.all { it.requirements.outputMode == OutputMode.JSON_SCHEMA })
        assertTrue(seeded.useCases.all { it.requirements.sessionKind == SessionKind.STATELESS })
        assertTrue(seeded.useCases.all { !it.requirements.reasoningSupported })
        val assignments = seeded.currentBindings(HarnessSharedRuntimeBindings.auraApplicationId).associateBy { it.useCaseId }
        assertTrue(requireNotNull(assignments[HarnessSharedRuntimeBindings.auraSchemaInferenceUseCaseId]).isDefault)
        assertFalse(requireNotNull(assignments[HarnessSharedRuntimeBindings.auraCategoryClassificationUseCaseId]).isDefault)

        val authorized = seeded.copy(
            applications = listOf(pending.copy(state = ApplicationRegistrationState.AUTHORIZED)),
        )
        val live = HarnessSharedRuntimePolicy.liveAuthorizedClients(listOf(bootstrap), authorized).single()
        assertEquals(HarnessSharedRuntimeBindings.AURA_RELEASE_PACKAGE, live.packageName)
        assertEquals(HarnessSharedRuntimeBindings.auraApplicationId, live.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.auraUseCases, live.allowedUseCases)
        assertEquals(setOf(SIGNER_A), live.acceptedSigningCertificates.map { it.hex }.toSet())
    }

    @Test
    fun `aura alias cannot reuse signer from another package and disabled registration loses trust`() {
        val release = auraPolicy(HarnessSharedRuntimeBindings.AURA_RELEASE_PACKAGE, SIGNER_A)
        val debug = auraPolicy(HarnessSharedRuntimeBindings.AURA_DEBUG_PACKAGE, SIGNER_B)
        val specs = HarnessSharedRuntimePolicy.builtInAuraControlPlaneSpecs(listOf(release, debug))
        val requirement = specs.first().applications.single()

        val mismatched = requirement.newRegistration(1).copy(
            packageName = HarnessSharedRuntimeBindings.AURA_RELEASE_PACKAGE,
            signerSha256 = SIGNER_B,
            state = ApplicationRegistrationState.AUTHORIZED,
        )
        val disabled = requirement.newRegistration(1).copy(
            packageName = HarnessSharedRuntimeBindings.AURA_RELEASE_PACKAGE,
            signerSha256 = SIGNER_A,
            state = ApplicationRegistrationState.DISABLED,
        )
        val state = HostControlPlaneState(applications = listOf(mismatched, disabled))

        assertTrue(HarnessSharedRuntimePolicy.liveAuthorizedClients(listOf(release, debug), state).isEmpty())
    }

    @Test
    fun `aura runtime policy rejects unknown use cases`() {
        val model = curatedModel()
        assertThrows(IllegalArgumentException::class.java) {
            HarnessSharedRuntimeBindings.resolveConsumerUseCase(
                model,
                HarnessSharedRuntimeBindings.auraApplicationId,
                HarnessSharedRuntimeBindings.consoleUseCaseId,
            )
        }
    }

    private fun auraPolicy(packageName: String, signer: String): AuthorizedClientPolicy =
        AuthorizedClientPolicy(
            applicationId = HarnessSharedRuntimeBindings.auraApplicationId,
            packageName = packageName,
            acceptedSigningCertificates = setOf(SigningCertificateSha256(signer)),
            allowedUseCases = HarnessSharedRuntimeBindings.auraUseCases,
        )

    private fun curatedModel() = CuratedModelCatalog.releases.first()

    private companion object {
        const val SIGNER_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SIGNER_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
