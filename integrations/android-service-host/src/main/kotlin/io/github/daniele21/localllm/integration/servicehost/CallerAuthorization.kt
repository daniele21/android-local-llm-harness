package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId

internal const val SHA256_HEX_LENGTH = 64

data class SigningCertificateSha256 private constructor(val hex: String) {
    val bytes: ByteArray
        get() = ByteArray(SHA256_HEX_LENGTH / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }

    companion object {
        fun parse(value: String): SigningCertificateSha256 {
            val normalized = value.trim().lowercase()
            require(normalized.length == SHA256_HEX_LENGTH && normalized.all { it in "0123456789abcdef" }) {
                "Signing certificate SHA-256 must be 64 hexadecimal characters"
            }
            return SigningCertificateSha256(normalized)
        }
    }
}

data class AuthorizedClientPolicy(
    val packageName: String,
    val applicationId: ApplicationId,
    val allowedUseCases: Set<UseCaseId>,
    val acceptedSigningCertificates: Set<SigningCertificateSha256>,
) {
    init {
        require(packageName.isNotBlank()) { "Authorized package name must not be blank" }
        require(applicationId.value.isNotBlank()) { "ApplicationId must not be blank" }
        require(allowedUseCases.isNotEmpty()) { "At least one use case must be authorized" }
        require(acceptedSigningCertificates.isNotEmpty()) { "At least one signing certificate must be accepted" }
    }
}

data class CallingProcess(val uid: Int, val pid: Int) {
    init {
        require(uid >= 0) { "Calling UID must be non-negative" }
        require(pid > 0) { "Calling PID must be positive" }
    }
}

data class AuthorizedCaller internal constructor(
    val uid: Int,
    val packageName: String,
    val applicationId: ApplicationId,
    val allowedUseCases: Set<UseCaseId>,
) {
    fun allows(useCaseId: UseCaseId): Boolean = useCaseId in allowedUseCases
}

interface CallerEnvironment {
    fun hasPermission(permissionName: String, callingProcess: CallingProcess): Boolean

    fun packagesForUid(uid: Int): List<String>

    fun hasSigningCertificate(packageName: String, certificate: SigningCertificateSha256): Boolean
}

enum class AuthorizationFailure {
    PERMISSION_DENIED,
    UNKNOWN_UID,
    AMBIGUOUS_UID,
    PACKAGE_NOT_AUTHORIZED,
    SIGNATURE_MISMATCH,
    USE_CASE_DENIED,
}

sealed interface AuthorizationResult {
    data class Allowed(val caller: AuthorizedCaller) : AuthorizationResult

    data class Denied(val failure: AuthorizationFailure) : AuthorizationResult
}

class CallerAuthorizer(
    private val permissionName: String,
    policies: Collection<AuthorizedClientPolicy>,
    private val environment: CallerEnvironment,
) {
    private val policiesByPackage: Map<String, AuthorizedClientPolicy>

    init {
        require(permissionName.isNotBlank()) { "Permission name must not be blank" }
        val grouped = policies.groupBy(AuthorizedClientPolicy::packageName)
        require(grouped.values.none { it.size > 1 }) { "Authorized package policies must be unique" }
        policiesByPackage = grouped.mapValues { (_, values) -> values.single() }
    }

    fun authorize(callingProcess: CallingProcess): AuthorizationResult = if (environment.hasPermission(permissionName, callingProcess)) {
        authorizePermittedCaller(callingProcess)
    } else {
        AuthorizationResult.Denied(AuthorizationFailure.PERMISSION_DENIED)
    }

    fun authorize(callingProcess: CallingProcess, useCaseId: UseCaseId): AuthorizationResult =
        when (val result = authorize(callingProcess)) {
            is AuthorizationResult.Denied -> result

            is AuthorizationResult.Allowed -> {
                if (result.caller.allows(useCaseId)) {
                    result
                } else {
                    AuthorizationResult.Denied(AuthorizationFailure.USE_CASE_DENIED)
                }
            }
        }

    private fun authorizePermittedCaller(callingProcess: CallingProcess): AuthorizationResult {
        val packages = environment.packagesForUid(callingProcess.uid).distinct()
        return when {
            packages.isEmpty() -> AuthorizationResult.Denied(AuthorizationFailure.UNKNOWN_UID)
            packages.size != 1 -> AuthorizationResult.Denied(AuthorizationFailure.AMBIGUOUS_UID)
            else -> authorizePackage(callingProcess.uid, packages.single())
        }
    }

    private fun authorizePackage(uid: Int, packageName: String): AuthorizationResult {
        val policy = policiesByPackage[packageName]
            ?: return AuthorizationResult.Denied(AuthorizationFailure.PACKAGE_NOT_AUTHORIZED)
        val signerMatches = policy.acceptedSigningCertificates.any { certificate ->
            environment.hasSigningCertificate(packageName, certificate)
        }
        return if (signerMatches) {
            AuthorizationResult.Allowed(
                AuthorizedCaller(
                    uid = uid,
                    packageName = packageName,
                    applicationId = policy.applicationId,
                    allowedUseCases = policy.allowedUseCases.toSet(),
                ),
            )
        } else {
            AuthorizationResult.Denied(AuthorizationFailure.SIGNATURE_MISMATCH)
        }
    }
}
