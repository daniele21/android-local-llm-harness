package io.github.daniele21.localllm.runtime

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal enum class SessionLifecycleState {
    OPEN,
    CLOSING,
    RELEASING,
    CLOSED,
}

internal data class SessionLifecycleSnapshot(val state: SessionLifecycleState, val activeRequests: Int)

/**
 * Owns the request-count and close/release transition contract for one runtime session.
 *
 * Resource release itself remains owned by RuntimeOrchestrator. The RELEASING state is a
 * reservation that prevents duplicate release attempts while allowing a failed release to
 * return deterministically to CLOSING for retry.
 */
internal class SessionLifecycle {
    private val state = AtomicReference(SessionLifecycleState.OPEN)
    private val activeRequests = AtomicInteger(0)

    fun snapshot(): SessionLifecycleSnapshot = SessionLifecycleSnapshot(
        state = state.get(),
        activeRequests = activeRequests.get(),
    )

    fun tryAcquireRequest(): Boolean {
        if (state.get() != SessionLifecycleState.OPEN) return false

        activeRequests.incrementAndGet()
        if (state.get() == SessionLifecycleState.OPEN) return true

        val remaining = activeRequests.decrementAndGet()
        check(remaining >= 0) { "Session request count became negative" }
        return false
    }

    /** Returns true when closing can proceed to resource release after this request drains. */
    fun releaseRequest(): Boolean {
        val remaining = activeRequests.decrementAndGet()
        check(remaining >= 0) { "Session request count became negative" }
        return remaining == 0 && state.get() == SessionLifecycleState.CLOSING
    }

    /** Starts close intent once. Repeated close requests are intentionally idempotent. */
    fun beginClose(): Boolean {
        while (true) {
            when (state.get()) {
                SessionLifecycleState.OPEN -> {
                    if (state.compareAndSet(SessionLifecycleState.OPEN, SessionLifecycleState.CLOSING)) {
                        return true
                    }
                }

                SessionLifecycleState.CLOSING,
                SessionLifecycleState.RELEASING,
                SessionLifecycleState.CLOSED,
                -> return false
            }
        }
    }

    fun isReleaseReady(): Boolean = state.get() == SessionLifecycleState.CLOSING && activeRequests.get() == 0

    /** Reserves the one resource-release attempt allowed while the session is drained. */
    fun tryBeginRelease(): Boolean {
        if (activeRequests.get() != 0) return false
        return state.compareAndSet(SessionLifecycleState.CLOSING, SessionLifecycleState.RELEASING)
    }

    fun releaseSucceeded() {
        check(state.compareAndSet(SessionLifecycleState.RELEASING, SessionLifecycleState.CLOSED)) {
            "Session release can complete only from RELEASING"
        }
    }

    fun releaseFailed() {
        check(state.compareAndSet(SessionLifecycleState.RELEASING, SessionLifecycleState.CLOSING)) {
            "Session release can roll back only from RELEASING"
        }
    }
}
