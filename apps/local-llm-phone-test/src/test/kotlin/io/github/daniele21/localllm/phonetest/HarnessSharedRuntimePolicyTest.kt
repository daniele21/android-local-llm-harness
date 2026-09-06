package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.integration.servicehost.SigningCertificateSha256
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

class HarnessSharedRuntimePolicyTest {
    @Test
    fun ombraSpecCollapsesPackageAliasesIntoSinglePendingExternalRequirement() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val policies = listOf(
            policy("io.github.daniele21.redactguard", applicationId, SIGNER_A),
            policy("io.github.daniele21.redactguard.debug", applicationId, SIGNER_B),
        )

        val requirement = HarnessSharedRuntimePolicy.builtInOmbraControlPlaneSpec(policies).applications.single()

        assertEquals(applicationId, requirement.applicationId)
        assertEquals(
            setOf("io.github.daniele21.redactguard", "io.github.daniele21.redactguard.debug"),
            requirement.acceptedPackageNames,
        )
        assertEquals(setOf(SIGNER_A, SIGNER_B), requirement.acceptedSignerSha256)
        assertEquals(
            mapOf(
                "io.github.daniele21.redactguard" to setOf(SIGNER_A),
                "io.github.daniele21.redactguard.debug" to setOf(SIGNER_B),
            ),
            requirement.acceptedSignerSha256ByPackage,
        )
        assertEquals("RedactGuard", requirement.displayName)
        assertEquals(ApplicationRegistrationState.PENDING, requirement.initialState)
        assertTrue(requirement.allowObservedSignerChange)
    }

    @Test
    fun ombraSpecRejectsSignerFromDifferentPackageAlias() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val requirement = HarnessSharedRuntimePolicy
            .builtInOmbraControlPlaneSpec(
                listOf(
                    policy("io.github.daniele21.redactguard", applicationId, SIGNER_A),
                    policy("io.github.daniele21.redactguard.debug", applicationId, SIGNER_B),
                ),
            ).applications
            .single()

        val mismatched = registration("io.github.daniele21.redactguard", applicationId, SIGNER_B)
        assertFalse(requirement.accepts(mismatched))

        val reconciled = requireNotNull(requirement.identityChange(mismatched, observedAtEpochMs = 3))
        assertEquals("io.github.daniele21.redactguard", reconciled.packageName)
        assertEquals(SIGNER_A, reconciled.signerSha256)
        assertEquals(ApplicationRegistrationState.SIGNATURE_CHANGED, reconciled.state)
    }

    @Test
    fun ombraSpecExcludesPoliciesThatDoNotAuthorizeOmbra() {
        val ombra = policy(
            "io.github.daniele21.redactguard",
            HarnessSharedRuntimeBindings.redactGuardApplicationId,
            SIGNER_A,
        )
        val unrelated = AuthorizedClientPolicy(
            packageName = "io.github.example.internal",
            applicationId = ApplicationId("internal-only"),
            allowedUseCases = setOf(HarnessRuntimePurpose.PLAYGROUND.useCaseId),
            acceptedSigningCertificates = setOf(SigningCertificateSha256.parse(SIGNER_A)),
        )

        val spec = HarnessSharedRuntimePolicy.builtInOmbraControlPlaneSpec(listOf(unrelated, ombra))

        assertEquals(listOf(HarnessSharedRuntimeBindings.redactGuardApplicationId), spec.applications.map { it.applicationId })
        assertTrue(spec.applications.none { it.applicationId == unrelated.applicationId })
    }

    @Test
    fun `live policy uses persisted RedactGuard signer instead of bootstrap signer`() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId
        val bootstrap = policy(HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE, applicationId, SIGNER_A)
        val application = RegisteredApplication(
            applicationId = applicationId,
            packageName = HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE,
            signerSha256 = SIGNER_B,
            displayName = "RedactGuard",
            state = ApplicationRegistrationState.AUTHORIZED,
            firstSeenAtEpochMs = 1,
            lastSeenAtEpochMs = 2,
        )
        val state = HostControlPlaneState(
            applications = listOf(application),
            useCases = listOf(activeUseCase(useCaseId)),
            bindings = listOf(ApplicationUseCaseBinding("rg-binding", applicationId, useCaseId, 1, enabled = true)),
        )

        val live = HarnessSharedRuntimePolicy.liveAuthorizedClients(listOf(bootstrap), state).single()

        assertEquals(application.packageName, live.packageName)
        assertEquals(setOf(SIGNER_B), live.acceptedSigningCertificates.map { it.hex }.toSet())
        assertFalse(SIGNER_A in live.acceptedSigningCertificates.map { it.hex })
    }

    @Test
    fun `live policy exposes manual authorized connection and removes it when disabled`() {
        val applicationId = ApplicationId("manual-consumer")
        val useCaseId = UseCaseId("manual-use-case")
        val application = RegisteredApplication(
            applicationId = applicationId,
            packageName = "com.example.manual",
            signerSha256 = SIGNER_A,
            displayName = "Manual consumer",
            state = ApplicationRegistrationState.AUTHORIZED,
            firstSeenAtEpochMs = 1,
            lastSeenAtEpochMs = 1,
        )
        val useCase = activeUseCase(useCaseId)
        val binding = ApplicationUseCaseBinding("manual-binding", applicationId, useCaseId, 1, enabled = true)
        val enabledState = HostControlPlaneState(
            applications = listOf(application),
            useCases = listOf(useCase),
            bindings = listOf(binding),
        )

        val live = HarnessSharedRuntimePolicy.liveAuthorizedClients(emptyList(), enabledState).single()
        assertEquals("com.example.manual", live.packageName)
        assertEquals(setOf(useCaseId), live.allowedUseCases)
        assertEquals(setOf(SIGNER_A), live.acceptedSigningCertificates.map { it.hex }.toSet())

        val disabledState = enabledState.copy(
            applications = listOf(application.copy(state = ApplicationRegistrationState.DISABLED)),
        )
        assertTrue(HarnessSharedRuntimePolicy.liveAuthorizedClients(emptyList(), disabledState).isEmpty())
    }

    private fun activeUseCase(useCaseId: UseCaseId) = UseCaseDefinition(
        useCaseId = useCaseId,
        displayName = "Use case",
        description = "Test use case",
        requirements = UseCaseRequirements(OutputMode.TEXT, SessionKind.STATELESS, false, 1),
        state = UseCaseDefinitionState.ACTIVE,
        revision = 1,
    )

    private fun policy(packageName: String, applicationId: ApplicationId, signer: String) = AuthorizedClientPolicy(
        packageName = packageName,
        applicationId = applicationId,
        allowedUseCases = setOf(HarnessSharedRuntimeBindings.ombraUseCaseId),
        acceptedSigningCertificates = setOf(SigningCertificateSha256.parse(signer)),
    )

    private fun registration(packageName: String, applicationId: ApplicationId, signer: String) = RegisteredApplication(
        applicationId = applicationId,
        packageName = packageName,
        signerSha256 = signer,
        displayName = "RedactGuard",
        state = ApplicationRegistrationState.PENDING,
        firstSeenAtEpochMs = 1,
        lastSeenAtEpochMs = 2,
    )

    private companion object {
        const val SIGNER_A = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
        const val SIGNER_B = "b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2"
    }
}
