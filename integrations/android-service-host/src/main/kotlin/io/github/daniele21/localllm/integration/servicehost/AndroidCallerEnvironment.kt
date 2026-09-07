package io.github.daniele21.localllm.integration.servicehost

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

class AndroidCallerEnvironment(context: Context) : CallerEnvironment {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    override fun hasPermission(permissionName: String, callingProcess: CallingProcess): Boolean {
        if (callingProcess.pid > 0) {
            return appContext.checkPermission(
                permissionName,
                callingProcess.pid,
                callingProcess.uid,
            ) == PackageManager.PERMISSION_GRANTED
        }

        // Binder one-way transactions do not carry a reliable calling PID. Keep the permission
        // check bound to the kernel-provided UID by resolving exactly one package for that UID;
        // CallerAuthorizer independently repeats the UID/package uniqueness check and signer check.
        val packageName = packagesForUid(callingProcess.uid).distinct().singleOrNull() ?: return false
        return packageManager.checkPermission(permissionName, packageName) == PackageManager.PERMISSION_GRANTED
    }

    override fun packagesForUid(uid: Int): List<String> = packageManager.getPackagesForUid(uid)?.toList().orEmpty()

    /**
     * Matches only certificates signing the currently installed APK. Do not use PackageManager.hasSigningCertificate
     * here: that API may accept historical signing-lineage certificates, while independent-consumer authorization
     * is pinned to the exact current signer observed and approved by the Harnex Control Plane.
     */
    override fun hasSigningCertificate(packageName: String, certificate: SigningCertificateSha256): Boolean =
        currentSigningCertificates(packageName).any { signature ->
            sha256(signature).contentEquals(certificate.bytes)
        }

    @Suppress("DEPRECATION")
    private fun currentSigningCertificates(packageName: String): List<Signature> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
                .orEmpty()
                .toList()
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty().toList()
        }
    } catch (_: PackageManager.NameNotFoundException) {
        emptyList()
    }

    private fun sha256(signature: Signature): ByteArray = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
}
