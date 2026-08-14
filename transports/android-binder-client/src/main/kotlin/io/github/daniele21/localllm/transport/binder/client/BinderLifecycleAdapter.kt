package io.github.daniele21.localllm.transport.binder.client

import android.os.DeadObjectException
import android.os.Looper
import android.os.RemoteException
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel
import io.github.daniele21.localllm.transport.binder.contract.toWire
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal fun interface BlockingCallGuard {
    fun requireAllowed()
}

internal fun interface CorrelationIdSource {
    fun nextId(): String
}

internal data class BinderLifecycleTimeouts(val operationTimeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS) {
    init {
        require(operationTimeoutMillis > 0L) { "operationTimeoutMillis must be positive" }
    }

    private companion object {
        const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 120_000L
    }
}

internal class BinderLifecycleAdapter(
    private val endpointProvider: () -> RegisteredSharedRuntimeEndpoint?,
    private val endpointInvalidations: SharedRuntimeEndpointInvalidationSource? = null,
    private val blockingCallGuard: BlockingCallGuard = AndroidMainThreadBlockingCallGuard,
    private val timeouts: BinderLifecycleTimeouts = BinderLifecycleTimeouts(),
    private val correlationIds: CorrelationIdSource = CorrelationIdSource { UUID.randomUUID().toString() },
) : AutoCloseable {
    private val sessions = BinderSessionRegistry()
    private val invalidationSubscription = endpointInvalidations?.addListener { epoch, _ -> sessions.invalidate(epoch) }

    fun prepare(useCaseId: UseCaseId): PrepareResult {
        blockingCallGuard.requireAllowed()
        val endpoint = endpointProvider()
        return if (endpoint == null) {
            prepareFailure("Shared runtime is not connected")
        } else {
            performPrepare(endpoint, useCaseId)
        }
    }

    fun openSession(useCaseId: UseCaseId, options: SessionOptions): SessionId {
        blockingCallGuard.requireAllowed()
        val endpoint = requireNotNull(endpointProvider()) { "Shared runtime is not connected" }
        val operationId = correlationIds.nextId()
        val externalSessionId = correlationIds.nextId()
        val request = OpenSessionRequestParcel(
            clientToken = endpoint.clientToken,
            operationId = operationId,
            externalSessionId = externalSessionId,
            useCaseId = useCaseId.value,
            options = options.toWire(),
        )
        return when (
            val outcome = awaitRemoteCallback<SessionResultParcel>(endpoint) { callback ->
                endpoint.service.openSession(request, callback)
            }
        ) {
            RemoteCallbackOutcome.TransportFailure -> error("Shared runtime transport failed")

            RemoteCallbackOutcome.Timeout -> {
                closeRemoteSession(endpoint, externalSessionId)
                error("Shared runtime open-session timed out")
            }

            is RemoteCallbackOutcome.Disconnected -> error("SERVICE_DISCONNECTED: ${outcome.detail}")

            is RemoteCallbackOutcome.Received -> mapOpenSession(endpoint, operationId, externalSessionId, outcome.result)
        }
    }

    fun closeSession(sessionId: SessionId) {
        val endpoint = sessions.takeForClose(sessionId.value) ?: return
        closeRemoteSession(endpoint, sessionId.value)
    }

    override fun close() {
        sessions.drain().forEach { (sessionId, endpoint) -> closeRemoteSession(endpoint, sessionId) }
        invalidationSubscription?.close()
        sessions.clearClosed()
    }

    private fun performPrepare(endpoint: RegisteredSharedRuntimeEndpoint, useCaseId: UseCaseId): PrepareResult {
        val operationId = correlationIds.nextId()
        val request = PrepareRequestParcel(
            clientToken = endpoint.clientToken,
            operationId = operationId,
            useCaseId = useCaseId.value,
        )
        return when (
            val outcome = awaitRemoteCallback<PrepareResultParcel>(endpoint) { callback ->
                endpoint.service.prepare(request, callback)
            }
        ) {
            RemoteCallbackOutcome.TransportFailure -> prepareFailure("Shared runtime transport failed")

            RemoteCallbackOutcome.Timeout -> prepareFailure("Shared runtime prepare timed out")

            is RemoteCallbackOutcome.Disconnected -> prepareFailure("SERVICE_DISCONNECTED: ${outcome.detail}")

            is RemoteCallbackOutcome.Received -> {
                if (isCurrentEndpoint(endpoint)) {
                    mapPrepareResult(operationId, outcome.result)
                } else {
                    prepareFailure("SERVICE_DISCONNECTED: Shared-runtime registration changed")
                }
            }
        }
    }

    private fun mapOpenSession(
        endpoint: RegisteredSharedRuntimeEndpoint,
        operationId: String,
        externalSessionId: String,
        result: SessionResultParcel,
    ): SessionId {
        check(isCurrentEndpoint(endpoint)) { "SERVICE_DISCONNECTED: Shared-runtime registration changed" }
        check(result.operationId == operationId) { "Shared runtime session correlation mismatch" }
        result.error?.let { error(it.safeMessage) }
        check(result.externalSessionId == externalSessionId) { "Shared runtime session identity mismatch" }
        sessions.register(externalSessionId, endpoint)
        return SessionId(externalSessionId)
    }

    private fun <T : Any> awaitRemoteCallback(
        endpoint: RegisteredSharedRuntimeEndpoint,
        call: ((T) -> Unit) -> Unit,
    ): RemoteCallbackOutcome<T> {
        val waiter = CallbackWaiter<T>()
        val operationInvalidation = endpointInvalidations?.addListener { epoch, detail ->
            if (epoch == endpoint.connectionEpoch) waiter.disconnect(detail)
        }
        return try {
            call(waiter::complete)
            waiter.await(timeouts.operationTimeoutMillis)
        } catch (_: DeadObjectException) {
            RemoteCallbackOutcome.Disconnected("Host Binder object died")
        } catch (_: RemoteException) {
            RemoteCallbackOutcome.TransportFailure
        } finally {
            operationInvalidation?.close()
        }
    }

    private fun closeRemoteSession(endpoint: RegisteredSharedRuntimeEndpoint, externalSessionId: String) {
        if (!isCurrentEndpoint(endpoint)) return
        val request = CloseSessionRequestParcel(
            clientToken = endpoint.clientToken,
            externalSessionId = externalSessionId,
        )
        try {
            endpoint.service.closeSession(request)
        } catch (_: RemoteException) {
            // Oneway close is best effort. Disconnect cleanup remains host-owned.
        }
    }

    private fun isCurrentEndpoint(endpoint: RegisteredSharedRuntimeEndpoint): Boolean =
        endpointProvider()?.connectionEpoch == endpoint.connectionEpoch

    private fun mapPrepareResult(operationId: String, result: PrepareResultParcel): PrepareResult {
        val error = result.error
        return when {
            result.operationId != operationId -> prepareFailure("Shared runtime prepare correlation mismatch")

            error != null -> prepareFailure(error.safeMessage)

            else -> PrepareResult(
                ready = result.ready,
                modelDigest = result.modelDigestSha256?.let(::ModelDigest),
                detail = result.detail,
            )
        }
    }
}

private class BinderSessionRegistry {
    private val closed = ConcurrentHashMap.newKeySet<String>()
    private val open = ConcurrentHashMap<String, RegisteredSharedRuntimeEndpoint>()

    fun register(sessionId: String, endpoint: RegisteredSharedRuntimeEndpoint) {
        closed.remove(sessionId)
        open[sessionId] = endpoint
    }

    fun takeForClose(sessionId: String): RegisteredSharedRuntimeEndpoint? {
        if (!closed.add(sessionId)) return null
        return open.remove(sessionId)
    }

    fun invalidate(connectionEpoch: Long) {
        open.forEach { (sessionId, endpoint) ->
            if (endpoint.connectionEpoch == connectionEpoch) {
                open.remove(sessionId, endpoint)
            }
        }
    }

    fun drain(): List<Pair<String, RegisteredSharedRuntimeEndpoint>> {
        val snapshot = open.entries.map { it.key to it.value }
        snapshot.forEach { (sessionId, endpoint) ->
            if (open.remove(sessionId, endpoint)) closed.add(sessionId)
        }
        return snapshot
    }

    fun clearClosed() {
        closed.clear()
    }
}

private sealed interface RemoteCallbackOutcome<out T : Any> {
    data object TransportFailure : RemoteCallbackOutcome<Nothing>

    data object Timeout : RemoteCallbackOutcome<Nothing>

    data class Disconnected(val detail: String) : RemoteCallbackOutcome<Nothing>

    data class Received<T : Any>(val result: T) : RemoteCallbackOutcome<T>
}

private fun prepareFailure(detail: String): PrepareResult = PrepareResult(false, null, detail)

internal object AndroidMainThreadBlockingCallGuard : BlockingCallGuard {
    override fun requireAllowed() {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Shared-runtime blocking lifecycle calls are not allowed on the Android main thread"
        }
    }
}

private class CallbackWaiter<T : Any> {
    private val latch = CountDownLatch(1)
    private val outcome = AtomicReference<RemoteCallbackOutcome<T>?>(null)

    fun complete(value: T) {
        complete(RemoteCallbackOutcome.Received(value))
    }

    fun disconnect(detail: String) {
        complete(RemoteCallbackOutcome.Disconnected(detail))
    }

    fun await(timeoutMillis: Long): RemoteCallbackOutcome<T> = if (latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
        requireNotNull(outcome.get())
    } else {
        RemoteCallbackOutcome.Timeout
    }

    private fun complete(value: RemoteCallbackOutcome<T>) {
        if (outcome.compareAndSet(null, value)) {
            latch.countDown()
        }
    }
}
