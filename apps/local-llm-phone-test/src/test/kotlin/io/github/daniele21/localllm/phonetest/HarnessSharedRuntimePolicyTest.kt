package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.integration.servicehost.SigningCertificateSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessSharedRuntimePolicyTest {
    @Test
    fun ombraSpecCollapsesPackageAliasesIntoSingleApplicationRequirement() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val policies = listOf(
            policy(
                packageName = "io.github.daniele21.redactguard",
                applicationId = applicationId,
                signer = SIGNER_A,
            ),
            policy(
                packageName = "io.github.daniele21.redactguard.debug",
                applicationId = applicationId,
                signer = SIGNER_B,
            ),
        )

        val spec = HarnessSharedRuntimePolicy.builtInOmbraControlPlaneSpec(policies)
        val requirement = spec.applications.single()

        assertEquals(applicationId, requirement.applicationId)
        assertEquals(
            setOf("io.github.daniele21.redactguard", "io.github.daniele21.redactguard.debug"),
            requirement.acceptedPackageNames,
        )
        assertEquals(setOf(SIGNER_A, SIGNER_B), requirement.acceptedSignerSha256)
        assertEquals("RedactGuard", requirement.displayName)
    }

    @Test
    fun ombraSpecExcludesPoliciesThatDoNotAuthorizeOmbra() {
        val ombra = policy(
            packageName = "io.github.daniele21.redactguard",
            applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId,
            signer = SIGNER_A,
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

    private fun policy(packageName: String, applicationId: ApplicationId, signer: String) = AuthorizedClientPolicy(
        packageName = packageName,
        applicationId = applicationId,
        allowedUseCases = setOf(HarnessSharedRuntimeBindings.ombraUseCaseId),
        acceptedSigningCertificates = setOf(SigningCertificateSha256.parse(signer)),
    )

    private companion object {
        const val SIGNER_A = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
        const val SIGNER_B = "b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2"
    }
}
