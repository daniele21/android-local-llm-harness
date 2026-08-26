package io.github.daniele21.localllm.phonetest

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.integration.servicehost.SigningCertificateSha256
import java.security.MessageDigest

/** Exact package/use-case policy for the proof host and external same-signer clients. */
internal object HarnessSharedRuntimePolicy {
    fun authorizedClients(context: Context): List<AuthorizedClientPolicy> {
        val acceptedSigningCertificates = currentPackageSigningCertificates(context)
        val internal =
            AuthorizedClientPolicy(
                packageName = context.packageName,
                applicationId = HarnessRuntimeGraph.APPLICATION_ID,
                allowedUseCases = HarnessRuntimePurpose.entries.map(HarnessRuntimePurpose::useCaseId).toSet(),
                acceptedSigningCertificates = acceptedSigningCertificates,
            )
        val consoleClients =
            HarnessSharedRuntimeBindings.consolePackages(BuildConfig.DEBUG).map { packageName ->
                AuthorizedClientPolicy(
                    packageName = packageName,
                    applicationId = HarnessSharedRuntimeBindings.consoleApplicationId,
                    allowedUseCases = HarnessSharedRuntimeBindings.consoleUseCases,
                    acceptedSigningCertificates = acceptedSigningCertificates,
                )
            }
        val redactGuardClients =
            HarnessSharedRuntimeBindings.redactGuardPackages(BuildConfig.DEBUG).map { packageName ->
                AuthorizedClientPolicy(
                    packageName = packageName,
                    applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId,
                    allowedUseCases = HarnessSharedRuntimeBindings.redactGuardUseCases,
                    acceptedSigningCertificates = acceptedSigningCertificates,
                )
            }
        val releaseEvidenceClient =
            if (BuildConfig.DEBUG) {
                emptyList()
            } else {
                listOf(
                    AuthorizedClientPolicy(
                        packageName = HarnessSharedRuntimeBindings.SR6_RELEASE_CONSUMER_PACKAGE,
                        applicationId = HarnessSharedRuntimeBindings.consoleApplicationId,
                        allowedUseCases = HarnessSharedRuntimeBindings.consoleUseCases,
                        acceptedSigningCertificates = acceptedSigningCertificates,
                    ),
                )
            }
        return listOf(internal) + consoleClients + redactGuardClients + releaseEvidenceClient
    }

    fun builtInOmbraControlPlaneSpec(policies: Collection<AuthorizedClientPolicy>): HarnessBuiltInControlPlaneSpec {
        val applications =
            policies
                .filter { HarnessSharedRuntimeBindings.ombraUseCaseId in it.allowedUseCases }
                .groupBy(AuthorizedClientPolicy::applicationId)
                .map { (applicationId, applicationPolicies) ->
                    HarnessBuiltInApplicationRequirement(
                        applicationId = applicationId,
                        acceptedPackageNames = applicationPolicies.map(AuthorizedClientPolicy::packageName).toSet(),
                        acceptedSignerSha256 =
                        applicationPolicies
                            .flatMap(AuthorizedClientPolicy::acceptedSigningCertificates)
                            .map(SigningCertificateSha256::hex)
                            .toSet(),
                        displayName = displayName(applicationId),
                    )
                }
        return HarnessBuiltInControlPlaneSpec.ombra(applications)
    }

    @Suppress("DEPRECATION")
    private fun currentPackageSigningCertificates(context: Context): Set<SigningCertificateSha256> {
        val packageManager = context.packageManager
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        val packageInfo = packageManager.getPackageInfo(context.packageName, flags)
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = requireNotNull(packageInfo.signingInfo) { "Host signing information is unavailable" }
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners.orEmpty().toList()
                } else {
                    signingInfo.signingCertificateHistory.orEmpty().toList()
                }
            } else {
                packageInfo.signatures.orEmpty().toList()
            }
        check(signatures.isNotEmpty()) { "Host signing certificate is unavailable" }
        return signatures.map(::sha256).toSet()
    }

    private fun displayName(applicationId: ApplicationId): String = when (applicationId) {
        HarnessSharedRuntimeBindings.consoleApplicationId -> "Local LLM Console"
        HarnessSharedRuntimeBindings.redactGuardApplicationId -> "RedactGuard"
        else -> applicationId.value
    }

    private fun sha256(signature: Signature): SigningCertificateSha256 {
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        val hex =
            digest.joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        return SigningCertificateSha256.parse(hex)
    }
}
