package io.github.daniele21.localllm.phonetest

import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Host-owned proof that user authorization can promote an observed independent signer without co-signing. */
@RunWith(AndroidJUnit4::class)
class HarnessIndependentConsumerAuthorizationInstrumentationTest {
    @Test
    fun authorizeExactObservedConsumerIdentity() {
        val (context, identity) = observedIdentityArguments()
        val gateway = gateway(context)
        val observed = gateway.snapshot().applications.single { it.packageName == identity.packageName }

        assertEquals(identity.signerSha256, observed.signerSha256)
        assertEquals(HarnessApplicationStatus.PENDING, observed.status)

        val result =
            gateway.setApplicationConnectionEnabled(
                HarnessSetApplicationConnectionEnabledCommand(
                    applicationId = observed.applicationId,
                    enabled = true,
                ),
            )
        assertTrue(result is HarnessControlPlaneMutationResult.Success)

        val authorized = gateway.snapshot().applications.single { it.applicationId == observed.applicationId }
        assertEquals(identity.signerSha256, authorized.signerSha256)
        assertEquals(HarnessApplicationStatus.AUTHORIZED, authorized.status)
    }

    @Test
    fun observedSignerReplacementRemainsBlockedUntilReauthorization() {
        val (context, identity) = observedIdentityArguments()
        val observed = gateway(context).snapshot().applications.single { it.packageName == identity.packageName }

        assertEquals(identity.signerSha256, observed.signerSha256)
        assertEquals(HarnessApplicationStatus.IDENTITY_CHANGED, observed.status)
    }

    private fun observedIdentityArguments(): Pair<Context, ObservedIdentity> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        return instrumentation.targetContext to arguments.observedIdentity()
    }

    private fun Bundle.observedIdentity() = ObservedIdentity(
        packageName =
        requireNotNull(getString(ARG_CONSUMER_PACKAGE)) {
            "Missing $ARG_CONSUMER_PACKAGE instrumentation argument"
        },
        signerSha256 =
        requireNotNull(getString(ARG_CONSUMER_SIGNER_SHA256)) {
            "Missing $ARG_CONSUMER_SIGNER_SHA256 instrumentation argument"
        }.lowercase(),
    )

    private fun gateway(context: Context) = StoreHarnessApplicationsGateway(
        HarnessRuntimeGraph.from(context).controlPlaneStore,
    )

    private data class ObservedIdentity(val packageName: String, val signerSha256: String)

    private companion object {
        const val ARG_CONSUMER_PACKAGE = "consumerPackage"
        const val ARG_CONSUMER_SIGNER_SHA256 = "consumerSignerSha256"
    }
}
