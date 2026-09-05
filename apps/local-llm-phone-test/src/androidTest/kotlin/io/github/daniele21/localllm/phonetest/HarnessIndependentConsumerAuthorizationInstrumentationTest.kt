package io.github.daniele21.localllm.phonetest

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
    fun `authorize exact observed consumer identity`() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val consumerPackage = requireNotNull(arguments.getString(ARG_CONSUMER_PACKAGE)) {
            "Missing $ARG_CONSUMER_PACKAGE instrumentation argument"
        }
        val expectedSigner = requireNotNull(arguments.getString(ARG_CONSUMER_SIGNER_SHA256)) {
            "Missing $ARG_CONSUMER_SIGNER_SHA256 instrumentation argument"
        }.lowercase()

        val gateway =
            StoreHarnessApplicationsGateway(
                HarnessRuntimeGraph.from(context).controlPlaneStore,
            )
        val observed = gateway.snapshot().applications.single { it.packageName == consumerPackage }

        assertEquals(expectedSigner, observed.signerSha256)
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
        assertEquals(expectedSigner, authorized.signerSha256)
        assertEquals(HarnessApplicationStatus.AUTHORIZED, authorized.status)
    }

    private companion object {
        const val ARG_CONSUMER_PACKAGE = "consumerPackage"
        const val ARG_CONSUMER_SIGNER_SHA256 = "consumerSignerSha256"
    }
}
