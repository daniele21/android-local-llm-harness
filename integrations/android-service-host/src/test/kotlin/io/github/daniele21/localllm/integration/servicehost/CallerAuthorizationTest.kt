package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerAuthorizationTest {
    private val digest = SigningCertificateSha256.parse("11".repeat(32))
    private val useCase = UseCaseId("summarize")
    private val policy =
        AuthorizedClientPolicy(
            packageName = "io.example.client",
            applicationId = ApplicationId("example-client"),
            allowedUseCases = setOf(useCase),
            acceptedSigningCertificates = setOf(digest),
        )

    @Test
    fun authorizedCallerIsDerivedFromHostPolicy() {
        val environment = FakeCallerEnvironment(packages = listOf(policy.packageName), acceptedCertificate = digest)
        val result = authorizer(environment).authorize(CallingProcess(uid = 10001, pid = 123), useCase)

        assertTrue(result is AuthorizationResult.Allowed)
        val caller = (result as AuthorizationResult.Allowed).caller
        assertEquals(policy.applicationId, caller.applicationId)
        assertEquals(policy.packageName, caller.packageName)
        assertTrue(caller.allows(useCase))
    }

    @Test
    fun onewayCallerWithUnavailablePidStillUsesAuthorizedUidPolicy() {
        val environment = FakeCallerEnvironment(packages = listOf(policy.packageName), acceptedCertificate = digest)
        val result = authorizer(environment).authorize(CallingProcess(uid = 10001, pid = 0), useCase)

        assertTrue(result is AuthorizationResult.Allowed)
        assertEquals(0, requireNotNull(environment.lastPermissionProcess).pid)
        assertEquals(policy.packageName, (result as AuthorizationResult.Allowed).caller.packageName)
    }

    @Test
    fun onewayCallerWithUnavailablePidStillRequiresPermission() {
        val environment =
            FakeCallerEnvironment(
                permissionGranted = false,
                packages = listOf(policy.packageName),
                acceptedCertificate = digest,
            )

        assertDenied(
            AuthorizationFailure.PERMISSION_DENIED,
            authorizer(environment).authorize(CallingProcess(uid = 10001, pid = 0)),
        )
        assertEquals(0, environment.packageLookupCount)
        assertEquals(0, environment.signerLookupCount)
    }

    @Test
    fun permissionDenialFailsBeforePackageOrSignerLookup() {
        val environment = FakeCallerEnvironment(permissionGranted = false, packages = listOf(policy.packageName))

        assertDenied(
            AuthorizationFailure.PERMISSION_DENIED,
            authorizer(environment).authorize(CallingProcess(uid = 10001, pid = 123)),
        )
        assertEquals(0, environment.packageLookupCount)
        assertEquals(0, environment.signerLookupCount)
    }

    @Test
    fun ambiguousUidFailsClosed() {
        val environment = FakeCallerEnvironment(packages = listOf(policy.packageName, "io.example.shared"))

        assertDenied(
            AuthorizationFailure.AMBIGUOUS_UID,
            authorizer(environment).authorize(CallingProcess(uid = 10001, pid = 123)),
        )
        assertEquals(0, environment.signerLookupCount)
    }

    @Test
    fun signerMismatchFailsClosed() {
        val environment = FakeCallerEnvironment(packages = listOf(policy.packageName))

        assertDenied(
            AuthorizationFailure.SIGNATURE_MISMATCH,
            authorizer(environment).authorize(CallingProcess(uid = 10001, pid = 123)),
        )
    }

    @Test
    fun unauthorizedUseCaseFailsClosed() {
        val environment = FakeCallerEnvironment(packages = listOf(policy.packageName), acceptedCertificate = digest)

        assertDenied(
            AuthorizationFailure.USE_CASE_DENIED,
            authorizer(environment).authorize(
                CallingProcess(uid = 10001, pid = 123),
                UseCaseId("not-authorized"),
            ),
        )
    }

    private fun authorizer(environment: FakeCallerEnvironment) = CallerAuthorizer(
        permissionName = "io.example.permission.USE_LOCAL_LLM",
        policies = listOf(policy),
        environment = environment,
    )

    private fun assertDenied(expected: AuthorizationFailure, result: AuthorizationResult) {
        assertTrue(result is AuthorizationResult.Denied)
        assertEquals(expected, (result as AuthorizationResult.Denied).failure)
    }

    private class FakeCallerEnvironment(
        private val permissionGranted: Boolean = true,
        private val packages: List<String> = emptyList(),
        private val acceptedCertificate: SigningCertificateSha256? = null,
    ) : CallerEnvironment {
        var packageLookupCount = 0
        var signerLookupCount = 0
        var lastPermissionProcess: CallingProcess? = null

        override fun hasPermission(permissionName: String, callingProcess: CallingProcess): Boolean {
            lastPermissionProcess = callingProcess
            return permissionGranted
        }

        override fun packagesForUid(uid: Int): List<String> {
            packageLookupCount += 1
            return packages
        }

        override fun hasSigningCertificate(packageName: String, certificate: SigningCertificateSha256): Boolean {
            signerLookupCount += 1
            return certificate == acceptedCertificate
        }
    }
}
