package io.github.daniele21.localllm.phonetest

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

internal data class HarnessInstalledApplicationIdentity(
    val packageName: String,
    val signerSha256: String,
)

/**
 * Source-backed Android package identity used only for packages the Host explicitly tracks.
 * Unknown packages are not inferred from user input and never become authorized implicitly.
 */
internal interface HarnessInstalledApplicationIdentitySource {
    fun tracks(packageName: String): Boolean

    fun resolve(packageName: String): HarnessInstalledApplicationIdentity?
}

internal object NoHarnessInstalledApplicationIdentitySource : HarnessInstalledApplicationIdentitySource {
    override fun tracks(packageName: String): Boolean = false

    override fun resolve(packageName: String): HarnessInstalledApplicationIdentity? = null
}

internal class AndroidHarnessInstalledApplicationIdentitySource(
    context: Context,
    trackedPackages: Set<String>,
) : HarnessInstalledApplicationIdentitySource {
    private val packageManager = context.applicationContext.packageManager
    private val trackedPackages = trackedPackages.toSet().also { packages ->
        require(packages.none(String::isBlank)) { "Tracked application packages must not be blank" }
    }

    override fun tracks(packageName: String): Boolean = packageName in trackedPackages

    @Suppress("DEPRECATION")
    override fun resolve(packageName: String): HarnessInstalledApplicationIdentity? {
        if (!tracks(packageName)) return null
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        val packageInfo = try {
            packageManager.getPackageInfo(packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val currentSigners =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners.orEmpty().toList()
            } else {
                packageInfo.signatures.orEmpty().toList()
            }
        val signer = currentSigners.singleOrNull() ?: return null
        return HarnessInstalledApplicationIdentity(
            packageName = packageName,
            signerSha256 = sha256(signer),
        )
    }

    private fun sha256(signature: Signature): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
