package io.github.daniele21.localllm.integration.servicehost

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

class AndroidCallerEnvironment(context: Context) : CallerEnvironment {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    override fun hasPermission(permissionName: String, callingProcess: CallingProcess): Boolean =
        appContext.checkPermission(permissionName, callingProcess.pid, callingProcess.uid) == PackageManager.PERMISSION_GRANTED

    override fun packagesForUid(uid: Int): List<String> = packageManager.getPackagesForUid(uid)?.toList().orEmpty()

    override fun hasSigningCertificate(packageName: String, certificate: SigningCertificateSha256): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.hasSigningCertificate(
                packageName,
                certificate.bytes,
                PackageManager.CERT_INPUT_SHA256,
            )
        } else {
            hasLegacySigningCertificate(packageName, certificate)
        }

    @Suppress("DEPRECATION")
    private fun hasLegacySigningCertificate(packageName: String, certificate: SigningCertificateSha256): Boolean = try {
        val signatures = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        signatures.any { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).contentEquals(certificate.bytes)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
