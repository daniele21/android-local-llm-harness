package io.github.daniele21.localllm.consumerfixture

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerDeactivationResult
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.client.BinderConsumerLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeHostConfig
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SharedRuntimeControlPlaneReleaseEvidenceTest {
    private lateinit var client: BinderConsumerLocalLlmClient

    @Before
    fun connectToReleaseHost() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        client = BinderConsumerLocalLlmClient.create(
            context = context,
            hostConfig = SharedRuntimeHostConfig.create(
                BuildConfig.SHARED_RUNTIME_HOST_PACKAGE,
                BuildConfig.SHARED_RUNTIME_HOST_SERVICE,
            ),
            clientBuildId = "hcp21-control-plane-${BuildConfig.VERSION_NAME}",
        )
        client.connect()
        assertTrue(
            "Packaged consumer client did not connect to the release host.",
            awaitConnection(SharedRuntimeConnectionState.CONNECTED),
        )
    }

    @After
    fun closeClient() {
        if (::client.isInitialized) client.close()
    }

    @Test
    fun packagedClientDiscoversActivatesRejectsDuplicateAndDeactivates() {
        val connection = client.connectionSnapshot
        assertEquals(BinderProtocolV1.MINOR, connection.negotiatedMinor)
        assertTrue(
            BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1 in connection.enabledFeatures,
        )

        val assigned = client.assignedUseCases() as ConsumerAssignedUseCasesResult.Available
        val piiAssignment = assigned.assignments.single { it.useCaseId == PII_USE_CASE_ID }
        assertTrue(piiAssignment.useCaseRevision > 0)
        assertTrue(piiAssignment.bindingRevision > 0)

        val published = client.publishedPresets(PII_USE_CASE_ID) as ConsumerPublishedPresetsResult.Available
        assertEquals(piiAssignment.bindingRevision, published.bindingRevision)
        val preset = published.presets.single { it.isDefault }

        val request = ConsumerActivationRequest(
            useCaseId = PII_USE_CASE_ID,
            useCaseRevision = piiAssignment.useCaseRevision,
            bindingRevision = piiAssignment.bindingRevision,
            preset = preset.preset,
        )
        val activated = client.activate(request) as ConsumerActivationResult.Activated
        assertEquals(PII_USE_CASE_ID, activated.activation.useCaseId)
        assertEquals(piiAssignment.useCaseRevision, activated.activation.useCaseRevision)
        assertEquals(piiAssignment.bindingRevision, activated.activation.bindingRevision)
        assertEquals(preset.preset, activated.activation.preset)

        val duplicate = client.activate(request) as ConsumerActivationResult.Rejected
        assertEquals(ConsumerControlPlaneErrorCode.ACTIVATION_ALREADY_ACTIVE, duplicate.failure.code)

        assertEquals(
            ConsumerDeactivationResult.Released,
            client.deactivate(activated.activation.activationId),
        )
        assertEquals(
            ConsumerDeactivationResult.Released,
            client.deactivate(activated.activation.activationId),
        )

        val reactivated = client.activate(request) as ConsumerActivationResult.Activated
        assertEquals(
            ConsumerDeactivationResult.Released,
            client.deactivate(reactivated.activation.activationId),
        )
        println(
            "HCP21_CONTROL_PLANE negotiatedMinor=${connection.negotiatedMinor} " +
                "feature=${BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1} " +
                "useCaseRevision=${piiAssignment.useCaseRevision} " +
                "bindingRevision=${piiAssignment.bindingRevision} " +
                "preset=${preset.preset.id.value}:${preset.preset.version} " +
                "duplicate=${duplicate.failure.code.name} deactivate=IDEMPOTENT",
        )
    }

    private fun awaitConnection(expected: SharedRuntimeConnectionState): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONNECTION_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (client.connectionSnapshot.state == expected) return true
            val current = client.connectionSnapshot.state
            if (current in TERMINAL_CONNECTION_FAILURES && current != expected) return false
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return client.connectionSnapshot.state == expected
    }

    private companion object {
        val PII_USE_CASE_ID = UseCaseId("document-pii-detection")
        val TERMINAL_CONNECTION_FAILURES = setOf(
            SharedRuntimeConnectionState.HOST_NOT_INSTALLED,
            SharedRuntimeConnectionState.PERMISSION_DENIED,
            SharedRuntimeConnectionState.INCOMPATIBLE,
            SharedRuntimeConnectionState.CONNECTION_LOST,
            SharedRuntimeConnectionState.CLOSED,
        )
        const val CONNECTION_TIMEOUT_SECONDS = 20L
        const val POLL_INTERVAL_MS = 50L
    }
}
