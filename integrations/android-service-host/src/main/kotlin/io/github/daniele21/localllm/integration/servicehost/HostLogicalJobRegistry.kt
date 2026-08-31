package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId

@JvmInline
internal value class HostLogicalJobId(val value: String) {
    init {
        require(SAFE_ID.matches(value)) { "Logical job ID must be a privacy-safe identifier" }
    }

    private companion object {
        val SAFE_ID = Regex("^[A-Za-z0-9._:-]{1,96}$")
    }
}

@JvmInline
internal value class HostClientRequestId(val value: String) {
    init {
        require(SAFE_ID.matches(value)) { "Client request ID must be a privacy-safe identifier" }
    }

    private companion object {
        val SAFE_ID = Regex("^[A-Za-z0-9._:-]{1,128}$")
    }
}

@JvmInline
internal value class HostRuntimeSessionId(val value: String) {
    init {
        require(SAFE_ID.matches(value)) { "Runtime session ID must be a privacy-safe identifier" }
    }

    private companion object {
        val SAFE_ID = Regex("^[A-Za-z0-9._:-]{1,96}$")
    }
}

internal data class HostLogicalJobScope(val applicationId: ApplicationId, val useCaseId: UseCaseId)

internal enum class HostLogicalJobState {
    QUEUED,
    PREPARING,
    RUNNING,
    SUCCEEDED,
    CANCEL_REQUESTED,
    CANCELLED,
    FAILED_RETRYABLE,
    RECOVERING,
    INTERRUPTED,
    FAILED_FINAL,
}

/** Privacy-safe job state only; inference payload/result content is deliberately absent. */
internal data class HostLogicalJobSnapshot(
    val jobId: HostLogicalJobId,
    val clientRequestId: HostClientRequestId,
    val scope: HostLogicalJobScope,
    val state: HostLogicalJobState,
    val revision: Long,
    val attempt: Int,
    val runtimeSessionId: HostRuntimeSessionId,
) {
    init {
        require(revision >= 0) { "Logical job revision must be non-negative" }
        require(attempt >= 1) { "Logical job attempt must be positive" }
    }

    val isTerminal: Boolean
        get() = state in TERMINAL_STATES

    private companion object {
        val TERMINAL_STATES =
            setOf(
                HostLogicalJobState.SUCCEEDED,
                HostLogicalJobState.CANCELLED,
                HostLogicalJobState.FAILED_FINAL,
            )
    }
}

internal data class HostLogicalJobTransition(
    val state: HostLogicalJobState,
    val revision: Long,
    val attempt: Int,
    val runtimeSessionId: HostRuntimeSessionId,
)

internal object HostLogicalJobLifecycle {
    fun apply(
        current: HostLogicalJobSnapshot,
        transition: HostLogicalJobTransition,
    ): HostLogicalJobSnapshot {
        if (transition.revision <= current.revision) return current
        require(transition.attempt >= current.attempt) { "Logical job attempt cannot move backwards" }

        if (transition.attempt == current.attempt) {
            require(transition.runtimeSessionId == current.runtimeSessionId) {
                "Runtime session cannot change inside one logical job attempt"
            }
            require(transition.state in allowedNextStates(current.state)) {
                "Illegal logical job transition ${current.state} -> ${transition.state}"
            }
        } else {
            require(transition.attempt == current.attempt + 1) {
                "Logical job attempt must advance one step at a time"
            }
            require(current.state in setOf(HostLogicalJobState.INTERRUPTED, HostLogicalJobState.FAILED_RETRYABLE)) {
                "Only interrupted or retryable jobs can start another attempt"
            }
            require(transition.state == HostLogicalJobState.RECOVERING) {
                "A new logical job attempt must begin in RECOVERING"
            }
            require(transition.runtimeSessionId != current.runtimeSessionId) {
                "A recovered logical job attempt must identify its runtime session"
            }
        }

        return current.copy(
            state = transition.state,
            revision = transition.revision,
            attempt = transition.attempt,
            runtimeSessionId = transition.runtimeSessionId,
        )
    }

    fun interruptStaleRuntime(
        current: HostLogicalJobSnapshot,
        activeRuntimeSessionId: HostRuntimeSessionId,
    ): HostLogicalJobSnapshot {
        if (current.isTerminal || current.runtimeSessionId == activeRuntimeSessionId) return current
        return current.copy(
            state = HostLogicalJobState.INTERRUPTED,
            revision = current.revision + 1,
        )
    }

    private fun allowedNextStates(state: HostLogicalJobState): Set<HostLogicalJobState> =
        when (state) {
            HostLogicalJobState.QUEUED -> {
                setOf(HostLogicalJobState.PREPARING, HostLogicalJobState.CANCEL_REQUESTED, HostLogicalJobState.FAILED_FINAL)
            }

            HostLogicalJobState.PREPARING -> {
                setOf(
                    HostLogicalJobState.RUNNING,
                    HostLogicalJobState.CANCEL_REQUESTED,
                    HostLogicalJobState.FAILED_RETRYABLE,
                    HostLogicalJobState.INTERRUPTED,
                    HostLogicalJobState.FAILED_FINAL,
                )
            }

            HostLogicalJobState.RUNNING -> {
                setOf(
                    HostLogicalJobState.SUCCEEDED,
                    HostLogicalJobState.CANCEL_REQUESTED,
                    HostLogicalJobState.FAILED_RETRYABLE,
                    HostLogicalJobState.INTERRUPTED,
                    HostLogicalJobState.FAILED_FINAL,
                )
            }

            HostLogicalJobState.CANCEL_REQUESTED -> {
                setOf(HostLogicalJobState.CANCELLED, HostLogicalJobState.SUCCEEDED, HostLogicalJobState.FAILED_FINAL)
            }

            HostLogicalJobState.FAILED_RETRYABLE,
            HostLogicalJobState.INTERRUPTED,
            -> {
                setOf(HostLogicalJobState.RECOVERING, HostLogicalJobState.CANCEL_REQUESTED, HostLogicalJobState.FAILED_FINAL)
            }

            HostLogicalJobState.RECOVERING -> {
                setOf(HostLogicalJobState.PREPARING, HostLogicalJobState.CANCEL_REQUESTED, HostLogicalJobState.FAILED_FINAL)
            }

            HostLogicalJobState.SUCCEEDED,
            HostLogicalJobState.CANCELLED,
            HostLogicalJobState.FAILED_FINAL,
            -> {
                emptySet()
            }
        }
}

internal data class HostLogicalJobSubmission(
    val snapshot: HostLogicalJobSnapshot,
    val created: Boolean,
)

/**
 * Bounded process-local identity/state registry. It stores no prompt or generated content. Binder
 * integration is intentionally separate so transport death cannot become registry ownership.
 */
internal class HostLogicalJobRegistry(
    private val maxJobs: Int,
    private val runtimeSessionId: HostRuntimeSessionId,
    private val idFactory: () -> HostLogicalJobId,
) {
    private data class RequestKey(
        val scope: HostLogicalJobScope,
        val clientRequestId: HostClientRequestId,
    )

    private val jobsById = LinkedHashMap<HostLogicalJobId, HostLogicalJobSnapshot>()
    private val jobsByRequest = LinkedHashMap<RequestKey, HostLogicalJobId>()

    init {
        require(maxJobs > 0) { "Logical job registry capacity must be positive" }
    }

    @Synchronized
    fun submit(
        scope: HostLogicalJobScope,
        clientRequestId: HostClientRequestId,
    ): HostLogicalJobSubmission {
        val requestKey = RequestKey(scope, clientRequestId)
        jobsByRequest[requestKey]?.let { existingId ->
            return HostLogicalJobSubmission(checkNotNull(jobsById[existingId]), created = false)
        }

        ensureCapacity()
        val jobId = idFactory()
        require(jobId !in jobsById) { "Logical job ID factory returned a duplicate ID" }
        val snapshot =
            HostLogicalJobSnapshot(
                jobId = jobId,
                clientRequestId = clientRequestId,
                scope = scope,
                state = HostLogicalJobState.QUEUED,
                revision = 0,
                attempt = 1,
                runtimeSessionId = runtimeSessionId,
            )
        jobsById[jobId] = snapshot
        jobsByRequest[requestKey] = jobId
        return HostLogicalJobSubmission(snapshot, created = true)
    }

    @Synchronized
    fun snapshot(
        scope: HostLogicalJobScope,
        jobId: HostLogicalJobId,
    ): HostLogicalJobSnapshot? = jobsById[jobId]?.takeIf { it.scope == scope }

    @Synchronized
    fun transition(
        scope: HostLogicalJobScope,
        jobId: HostLogicalJobId,
        transition: HostLogicalJobTransition,
    ): HostLogicalJobSnapshot? {
        val current = snapshot(scope, jobId) ?: return null
        val next = HostLogicalJobLifecycle.apply(current, transition)
        jobsById[jobId] = next
        return next
    }

    @Synchronized
    fun size(): Int = jobsById.size

    private fun ensureCapacity() {
        if (jobsById.size < maxJobs) return
        val evictable =
            jobsById.values.firstOrNull(HostLogicalJobSnapshot::isTerminal)
                ?: throw HostLogicalJobCapacityException()
        jobsById.remove(evictable.jobId)
        jobsByRequest.remove(RequestKey(evictable.scope, evictable.clientRequestId))
    }
}

internal class HostLogicalJobCapacityException : IllegalStateException("Logical job registry is at capacity")
