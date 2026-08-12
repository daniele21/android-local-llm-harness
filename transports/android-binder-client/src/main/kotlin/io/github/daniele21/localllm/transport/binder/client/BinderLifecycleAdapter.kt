package io.github.daniele21.localllm.transport.binder.client

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
    private val blockingCallGuard: BlockingCallGuard = AndroidMainThreadBlockingCallGuard,
    private val timeouts: BinderLifecycleTimeouts = BinderLifecycleTimeouts(),
    private val correlationIds: CorrelationIdSource = CorrelationIdSource { UUID.randomUUID().toString() },
) {
    private val closedSessions = ConcurrentHashMap.newKeySet<String>()

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
        val waiter = CallbackWaiter<SessionResultParcel>()
        try {
            endpoint.service.openSession(request, waiter::complete)
        } catch (error: RemoteException) {
            throw IllegalStateException("Shared runtime transport failed", error)
        }
        val result = requireNotNull(waiter.await(timeouts.operationTimeoutMillis)) {
            "Shared runtime open-session timed out"
        }
        check(result.operationId == operationId) { "Shared runtime session correlation mismatch" }
        result.error?.let { throw IllegalStateException(it.safeMessage) }
        check(result.externalSessionId == externalSessionId) { "Shared runtime session identity mismatch" }
        return SessionId(externalSessionId)
    }

    fun closeSession(sessionId: SessionId) {
        if (!closedSessions.add(sessionId.value)) return
        val endpoint = endpointProvider() ?: return
        val request = CloseSessionRequestParcel(
            clientToken = endpoint.clientToken,
            externalSessionId = sessionId.value,
        )
        try {
            endpoint.service.closeSession(request)
        } catch (_: RemoteException) {
            // Oneway close is best effort. Disconnect cleanup remains host-owned.
        }
    }

    private fun performPrepare(endpoint: RegisteredSharedRuntimeEndpoint, useCaseId: UseCaseId): PrepareResult {
        val operationId = correlationIds.nextId()
        val request = PrepareRequestParcel(
            clientToken = endpoint.clientToken,
            operationId = operationId,
            useCaseId = useCaseId.value,
        )
        return when (val outcome = awaitPrepare(endpoint, request)) {
            PrepareCallbackOutcome.TransportFailure -> prepareFailure("Shared runtime transport failed")
            PrepareCallbackOutcome.Timeout -> prepareFailure("Shared runtime prepare timed out")
            is PrepareCallbackOutcome.Received -> mapPrepareResult(operationId, outcome.result)
        }
    }

    private fun awaitPrepare(
        endpoint: RegisteredSharedRuntimeEndpoint,
        request: PrepareRequestParcel,
    ): PrepareCallbackOutcome {
        val waiter = CallbackWaiter<PrepareResultParcel>()
        return try {
            endpoint.service.prepare(request, waiter::complete)
            waiter.await(timeouts.operationTimeoutMillis)?.let(PrepareCallbackOutcome::Received)
                ?: PrepareCallbackOutcome.Timeout
        } catch (_: RemoteException) {
            PrepareCallbackOutcome.TransportFailure
        }
    }

    private fun mapPrepareResult(operationId: String, result: PrepareResultParcel): PrepareResult = when {
        result.operationId != operationId -> prepareFailure("Shared runtime prepare correlation mismatch")
        result.error != null -> prepareFailure(result.error.safeMessage)
        else -> PrepareResult(
            ready = result.ready,
            modelDigest = result.modelDigestSha256?.let(::ModelDigest),
            detail = result.detail,
        )
    }
}

private sealed interface PrepareCallbackOutcome {
    data object TransportFailure : PrepareCallbackOutcome
    data object Timeout : PrepareCallbackOutcome
    data class Received(val result: PrepareResultParcel) : PrepareCallbackOutcome
}

private fun prepareFailure(detail: String): PrepareResult = PrepareResult(false, null, detail)

private object AndroidMainThreadBlockingCallGuard : BlockingCallGuard {
    override fun requireAllowed() {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Shared-runtime blocking lifecycle calls are not allowed on the Android main thread"
        }
    }
}

private class CallbackWaiter<T : Any> {
    private val latch = CountDownLatch(1)
    private val result = AtomicReference<T?>(null)

    fun complete(value: T) {
        if (result.compareAndSet(null, value)) {
            latch.countDown()
        }
    }

    fun await(timeoutMillis: Long): T? = if (latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) result.get() else null
}
