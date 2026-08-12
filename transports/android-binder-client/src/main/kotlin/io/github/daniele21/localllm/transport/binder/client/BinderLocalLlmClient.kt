package io.github.daniele21.localllm.transport.binder.client

import android.content.Context
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-level LocalLlmClient backed by the authenticated shared-runtime Binder connection.
 *
 * Consumers explicitly connect before using lifecycle or generation operations. The Binder/AIDL
 * implementation, registration token and connection epoch remain private to this module.
 */
class BinderLocalLlmClient internal constructor(
    private val connection: SharedRuntimeConnection,
    private val applicationId: ApplicationId,
    private val lifecycle: BinderLifecycleAdapter,
    private val generation: BinderGenerationAdapter,
) : LocalLlmClient, AutoCloseable {
    private val closed = AtomicBoolean(false)

    val connectionSnapshot: SharedRuntimeConnectionSnapshot
        get() = connection.snapshot

    fun connect() {
        check(!closed.get()) { "Shared-runtime client is closed" }
        connection.connect()
    }

    override fun runtimeSnapshot(): RuntimeSnapshot {
        throw UnsupportedOperationException(RUNTIME_SNAPSHOT_UNAVAILABLE)
    }

    override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
        requireApplication(applicationId)
        checkOpen()
        return lifecycle.prepare(useCaseId)
    }

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId =
        createSession(applicationId, useCaseId, SessionOptions())

    override fun createSession(
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        options: SessionOptions,
    ): SessionId {
        requireApplication(applicationId)
        checkOpen()
        return lifecycle.openSession(useCaseId, options)
    }

    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
        requireApplication(request.applicationId)
        checkOpen()
        return generation.generate(request, listener::onEvent)
    }

    override fun closeSession(sessionId: SessionId) {
        if (closed.get()) return
        lifecycle.closeSession(sessionId)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        generation.close()
        lifecycle.close()
        connection.close()
    }

    private fun requireApplication(requested: ApplicationId) {
        require(requested == applicationId) {
            "Binder client is configured for applicationId ${applicationId.value}"
        }
    }

    private fun checkOpen() {
        check(!closed.get()) { "Shared-runtime client is closed" }
    }

    companion object {
        fun create(
            context: Context,
            hostConfig: SharedRuntimeHostConfig,
            applicationId: ApplicationId,
            clientBuildId: String,
            requiredFeatures: Set<String> = emptySet(),
            observer: SharedRuntimeConnectionObserver = SharedRuntimeConnectionObserver {},
        ): BinderLocalLlmClient {
            val connection = SharedRuntimeConnection.create(
                context = context,
                hostConfig = hostConfig,
                clientBuildId = clientBuildId,
                requiredFeatures = requiredFeatures,
                observer = observer,
            )
            return BinderLocalLlmClient(
                connection = connection,
                applicationId = applicationId,
                lifecycle = BinderLifecycleAdapter(
                    endpointProvider = { connection.endpoint },
                    endpointInvalidations = connection.endpointInvalidations,
                ),
                generation = BinderGenerationAdapter(
                    endpointProvider = { connection.endpoint },
                    endpointInvalidations = connection.endpointInvalidations,
                ),
            )
        }

        private const val RUNTIME_SNAPSHOT_UNAVAILABLE =
            "Shared-runtime protocol v1 does not expose host runtime snapshots"
    }
}
