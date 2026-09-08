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
        assertFalse(schema.model.id == category.model.id)
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
        )
        assertFalse(requirement.accepts(mismatched))

        val seeded = specs.fold(HostControlPlaneState()) { state, spec ->
            val result = HarnessControlPlaneReconciler(spec).reconcile(state, 10)
            require(result is HarnessControlPlaneReconciliationResult.Success)
            result.state
        }
        val disabled = seeded.copy(
            applications = listOf(seeded.applications.single().copy(state = ApplicationRegistrationState.DISABLED)),
        )
        assertTrue(HarnessSharedRuntimePolicy.liveAuthorizedClients(listOf(release, debug), disabled).isEmpty())
    }

    @Test
    fun `aura schema and category specs reconcile atomically in one startup transaction`() {
        val bootstrap = auraPolicy(HarnessSharedRuntimeBindings.AURA_RELEASE_PACKAGE, SIGNER_A)
        val specs = HarnessSharedRuntimePolicy.builtInAuraControlPlaneSpecs(listOf(bootstrap))
        val store = RecordingStore(HostControlPlaneState())
        val startup = HarnessControlPlaneStartup(
            store = store,
            reconciler = HarnessControlPlaneReconciler(specs.first()),
            additionalReconcilers = specs.drop(1).map(::HarnessControlPlaneReconciler),
            epochClock = { 100 },
        )

        val state = startup.reconcile()

        assertEquals(1, store.transactionCount)
        assertEquals(HarnessSharedRuntimeBindings.auraUseCases, state.useCases.map { it.useCaseId }.toSet())
        assertEquals(2, state.bindings.size)
        assertEquals(2, state.presets.size)
    }

    @Test
    fun `generic consumer resolver rejects an unreviewed use case`() {
        assertThrows(IllegalStateException::class.java) {
            HarnessSharedRuntimeBindings.resolveConsumerUseCase(
                curatedModel(),
                HarnessSharedRuntimeBindings.auraApplicationId,
                io.github.daniele21.localllm.contracts.UseCaseId("unreviewed-aura-use-case"),
            )
        }
    }

    private fun auraPolicy(packageName: String, signer: String) = AuthorizedClientPolicy(
        packageName = packageName,
        applicationId = HarnessSharedRuntimeBindings.auraApplicationId,
        allowedUseCases = HarnessSharedRuntimeBindings.auraUseCases,
        acceptedSigningCertificates = setOf(SigningCertificateSha256.parse(signer)),
    )

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

    private class RecordingStore(initial: HostControlPlaneState) : HostControlPlaneStore {
        private var state = initial
        var transactionCount: Int = 0
            private set

        override fun snapshot(): HostControlPlaneState = state

        override fun replace(state: HostControlPlaneState) {
            this.state = state
        }

        override fun transact(transaction: HostControlPlaneTransaction): HostControlPlaneState {
            transactionCount += 1
            val updated = transaction.apply(state)
            state = updated
            return updated
        }
    }

    private companion object {
        const val SIGNER_A = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
        const val SIGNER_B = "b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2"
    }
}
