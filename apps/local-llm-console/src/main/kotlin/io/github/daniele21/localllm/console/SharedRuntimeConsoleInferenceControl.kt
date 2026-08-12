package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.transport.binder.client.BinderLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionSnapshot
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState

class SharedRuntimeConsoleInferenceControl(
    private val client: BinderLocalLlmClient,
    private val targets: List<ConsoleInferenceTarget>,
    private val source: String = SOURCE,
) : ConsoleInferenceControl {
    private val lock = Any()
    private var delegate = newDelegate()

    fun connect(): ConsoleInferenceOperationOutcome {
        val before = client.connectionSnapshot.state
        if (before == SharedRuntimeConnectionState.CLOSED) {
            return unavailable("Shared-runtime client is closed")
        }
        if (before in RESET_BEFORE_RECONNECT_STATES) {
            resetDelegate()
        }
        val failure = runCatching { client.connect() }.exceptionOrNull()
        if (failure != null) {
            return unavailable(CONNECTION_ERROR)
        }
        val current = snapshot()
        val success = current.connectionState in setOf(
            ConsoleInferenceConnectionState.CONNECTING,
            ConsoleInferenceConnectionState.CONNECTED,
        )
        return ConsoleInferenceOperationOutcome(
            success = success,
            state = current,
            sourceError = if (success) null else current.sourceError,
        )
    }

    override fun snapshot(): ConsoleInferenceState {
        val connection = client.connectionSnapshot
        return if (connection.state == SharedRuntimeConnectionState.CONNECTED) {
            synchronized(lock) { delegate.snapshot() }.copy(
                connectionState = ConsoleInferenceConnectionState.CONNECTED,
                source = source,
            )
        } else {
            disconnectedState(connection)
        }
    }

    override fun start(request: ConsoleInferenceRequest, listener: ConsoleInferenceListener): ConsoleInferenceOperationOutcome {
        if (client.connectionSnapshot.state != SharedRuntimeConnectionState.CONNECTED) {
            return unavailable(CONNECTION_REQUIRED)
        }
        val outcome = synchronized(lock) {
            delegate.start(
                request,
                ConsoleInferenceListener { state ->
                    listener.onStateChanged(
                        state.copy(
                            connectionState = ConsoleInferenceConnectionState.CONNECTED,
                            source = source,
                        ),
                    )
                },
            )
        }
        return outcome.copy(
            state = outcome.state.copy(
                connectionState = ConsoleInferenceConnectionState.CONNECTED,
                source = source,
            ),
        )
    }

    override fun cancel(): ConsoleInferenceOperationOutcome = synchronized(lock) {
        if (client.connectionSnapshot.state != SharedRuntimeConnectionState.CONNECTED) {
            unavailable(CONNECTION_REQUIRED)
        } else {
            connected(delegate.cancel())
        }
    }

    override fun clear(): ConsoleInferenceOperationOutcome = synchronized(lock) {
        val outcome = delegate.clear()
        if (client.connectionSnapshot.state == SharedRuntimeConnectionState.CONNECTED) {
            connected(outcome)
        } else {
            outcome.copy(state = snapshot())
        }
    }

    override fun close() {
        val current = synchronized(lock) { delegate }
        current.close()
        client.close()
    }

    private fun connected(outcome: ConsoleInferenceOperationOutcome): ConsoleInferenceOperationOutcome = outcome.copy(
        state = outcome.state.copy(
            connectionState = ConsoleInferenceConnectionState.CONNECTED,
            source = source,
        ),
    )

    private fun unavailable(detail: String): ConsoleInferenceOperationOutcome {
        val current = snapshot().copy(sourceError = detail)
        return ConsoleInferenceOperationOutcome(false, current, detail)
    }

    private fun resetDelegate() {
        synchronized(lock) {
            delegate.close()
            delegate = newDelegate()
        }
    }

    private fun newDelegate(): LocalLlmConsoleInferenceControl = LocalLlmConsoleInferenceControl(
        client = client,
        targets = targets,
        source = source,
    )

    private fun disconnectedState(connection: SharedRuntimeConnectionSnapshot): ConsoleInferenceState {
        val connectionState = connection.state.toConsoleConnectionState()
        val detail = connection.detail ?: connectionState.defaultDetail()
        val sourceError = if (connectionState in ERROR_CONNECTION_STATES) detail else null
        return ConsoleInferenceState(
            available = false,
            source = source,
            targets = emptyList(),
            phase = ConsoleInferencePhase.DISCONNECTED,
            connectionState = connectionState,
            detail = detail,
            sourceError = sourceError,
        )
    }

    private companion object {
        const val SOURCE = "Shared Android runtime (Binder)"
        const val CONNECTION_ERROR = "Shared-runtime connection failed"
        const val CONNECTION_REQUIRED = "Connect the shared runtime before starting inference"

        val RESET_BEFORE_RECONNECT_STATES = setOf(
            SharedRuntimeConnectionState.HOST_NOT_INSTALLED,
            SharedRuntimeConnectionState.PERMISSION_DENIED,
            SharedRuntimeConnectionState.INCOMPATIBLE,
            SharedRuntimeConnectionState.CONNECTION_LOST,
        )
        val ERROR_CONNECTION_STATES = setOf(
            ConsoleInferenceConnectionState.HOST_NOT_INSTALLED,
            ConsoleInferenceConnectionState.PERMISSION_DENIED,
            ConsoleInferenceConnectionState.INCOMPATIBLE,
            ConsoleInferenceConnectionState.CONNECTION_LOST,
            ConsoleInferenceConnectionState.CLOSED,
        )
    }
}

private fun SharedRuntimeConnectionState.toConsoleConnectionState(): ConsoleInferenceConnectionState = when (this) {
    SharedRuntimeConnectionState.DISCONNECTED -> ConsoleInferenceConnectionState.DISCONNECTED

    SharedRuntimeConnectionState.BINDING,
    SharedRuntimeConnectionState.NEGOTIATING,
    -> ConsoleInferenceConnectionState.CONNECTING

    SharedRuntimeConnectionState.CONNECTED -> ConsoleInferenceConnectionState.CONNECTED

    SharedRuntimeConnectionState.HOST_NOT_INSTALLED -> ConsoleInferenceConnectionState.HOST_NOT_INSTALLED

    SharedRuntimeConnectionState.PERMISSION_DENIED -> ConsoleInferenceConnectionState.PERMISSION_DENIED

    SharedRuntimeConnectionState.INCOMPATIBLE -> ConsoleInferenceConnectionState.INCOMPATIBLE

    SharedRuntimeConnectionState.CONNECTION_LOST -> ConsoleInferenceConnectionState.CONNECTION_LOST

    SharedRuntimeConnectionState.CLOSED -> ConsoleInferenceConnectionState.CLOSED
}

private fun ConsoleInferenceConnectionState.defaultDetail(): String = when (this) {
    ConsoleInferenceConnectionState.DISCONNECTED -> "Shared runtime is not connected"
    ConsoleInferenceConnectionState.CONNECTING -> "Connecting to shared runtime"
    ConsoleInferenceConnectionState.CONNECTED -> "Shared runtime connected"
    ConsoleInferenceConnectionState.HOST_NOT_INSTALLED -> "Configured shared-runtime host is not installed"
    ConsoleInferenceConnectionState.PERMISSION_DENIED -> "Shared-runtime host rejected this application"
    ConsoleInferenceConnectionState.INCOMPATIBLE -> "Shared-runtime protocol is incompatible"
    ConsoleInferenceConnectionState.CONNECTION_LOST -> "Shared-runtime connection was lost"
    ConsoleInferenceConnectionState.CLOSED -> "Shared-runtime client is closed"
}
