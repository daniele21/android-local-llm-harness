package io.github.daniele21.localllm.integration.servicehost

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun interface HostControlExecutor {
    fun execute(task: () -> Unit): Boolean
}

class BoundedSerialHostControlExecutor(queueCapacity: Int = DEFAULT_CONTROL_QUEUE_CAPACITY) :
    HostControlExecutor,
    AutoCloseable {
    private val executor: ThreadPoolExecutor

    init {
        require(queueCapacity > 0) { "Control queue capacity must be positive" }
        executor =
            ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(queueCapacity),
                { runnable -> Thread(runnable, CONTROL_THREAD_NAME).apply { isDaemon = true } },
                ThreadPoolExecutor.AbortPolicy(),
            )
    }

    override fun execute(task: () -> Unit): Boolean = try {
        executor.execute(task)
        true
    } catch (_: RejectedExecutionException) {
        false
    }

    override fun close() {
        executor.shutdownNow()
    }

    private companion object {
        const val DEFAULT_CONTROL_QUEUE_CAPACITY = 64
        const val CONTROL_THREAD_NAME = "local-llm-host-control"
    }
}

internal fun HostControlExecutor.submitOrReject(onRejected: () -> Unit, task: () -> Unit) {
    if (!execute(task)) onRejected()
}
