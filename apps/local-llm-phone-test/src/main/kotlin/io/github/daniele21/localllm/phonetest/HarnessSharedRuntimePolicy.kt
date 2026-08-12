package io.github.daniele21.localllm.phonetest

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy
import io.github.daniele21.localllm.integration.servicehost.SigningCertificateSha256
import java.security.MessageDigest

/** Exact package/use-case policy for the proof host and first external same-signer client. */
internal object HarnessSharedRuntimePolicy {
    fun authorizedClients(context: Context): List<AuthorizedClientPolicy> {
        val acceptedSigningCertificates = currentPackageSigningCertificates(context)
        val internal = AuthorizedClientPolicy(
            packageName = context.packageName,
            applicationId = HarnessRuntimeGraph.APPLICATION_ID,
            allowedUseCases = HarnessRuntimePurpose.entries.map(HarnessRuntimePurpose::useCaseId).toSet(),
            acceptedSigningCertificates = acceptedSigningCertificates,
        )
        val console = HarnessSharedRuntimeBindings.consolePackages(BuildConfig.DEBUG).map { packageName ->
            AuthorizedClientPolicy(
                packageName = packageName,
                applicationId = HarnessSharedRuntimeBindings.consoleApplicationId,
                allowedUseCases = setOf(HarnessSharedRuntimeBindings.consoleUseCaseId),
                acceptedSigningCertificates = acceptedSigningCertificates,
            )
        }
        return listOf(internal) + console
    }

    @Suppress("DEPRECATION")
    private fun currentPackageSigningCertificates(context: Context): Set<SigningCertificateSha256> {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = packageManager.getPackageInfo(context.packageName, flags)
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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

    private fun sha256(signature: Signature): SigningCertificateSha256 {
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        val hex = digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return SigningCertificateSha256.parse(hex)
    }
}
