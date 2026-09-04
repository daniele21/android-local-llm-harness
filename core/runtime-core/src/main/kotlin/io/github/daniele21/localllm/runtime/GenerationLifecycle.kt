package io.github.daniele21.localllm.runtime

import java.util.concurrent.atomic.AtomicReference

internal enum class GenerationLifecycleState {
    OPEN,
    CANCELLING,
    TERMINAL,
}

/**
 * Owns cancellation intent and terminal-once semantics for one accepted generation request.
 *
 * Queue position and active decode ownership remain in SingleDecodeScheduler. This lifecycle
 * deliberately does not model QUEUED/RUNNING so scheduling state has a single authoritative owner.
 */
internal class GenerationLifecycle {
    private val state = AtomicReference(GenerationLifecycleState.OPEN)

    fun state(): GenerationLifecycleState = state.get()

    /** Records cancellation intent once. Repeated or post-terminal cancellation is idempotent. */
    fun requestCancellation(): Boolean = state.compareAndSet(
        GenerationLifecycleState.OPEN,
        GenerationLifecycleState.CANCELLING,
    )

    fun isCancellationRequested(): Boolean = state.get() == GenerationLifecycleState.CANCELLING

    fun isTerminal(): Boolean = state.get() == GenerationLifecycleState.TERMINAL

    /** Reserves terminal delivery once from either the normal or cancelling path. */
    fun tryFinish(): Boolean {
        while (true) {
            when (val current = state.get()) {
                GenerationLifecycleState.OPEN,
                GenerationLifecycleState.CANCELLING,
                -> if (state.compareAndSet(current, GenerationLifecycleState.TERMINAL)) return true

                GenerationLifecycleState.TERMINAL -> return false
            }
        }
    }
}
