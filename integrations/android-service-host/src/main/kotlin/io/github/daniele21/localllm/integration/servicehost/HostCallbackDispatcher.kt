package io.github.daniele21.localllm.integration.servicehost

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun interface HostCallbackDispatcher : AutoCloseable {
    fun dispatch(task: () -> Unit): Boolean

    override fun close() = Unit
}

fun interface HostCallbackDispatcherFactory {
    fun create(): HostCallbackDispatcher
}

class BoundedSerialHostCallbackDispatcher(
    queueCapacity: Int = DEFAULT_CALLBACK_QUEUE_CAPACITY,
) : HostCallbackDispatcher {
    private val executor: ThreadPoolExecutor

    init {
        require(queueCapacity > 0) { "Callback queue capacity must be positive" }
        executor =
            ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(queueCapacity),
                { runnable -> Thread(runnable, CALLBACK_THREAD_NAME).apply { isDaemon = true } },
                ThreadPoolExecutor.AbortPolicy(),
            )
    }

    override fun dispatch(task: () -> Unit): Boolean = try {
        executor.execute(task)
        true
    } catch (_: RejectedExecutionException) {
        false
    }

    override fun close() {
        executor.shutdownNow()
    }

    private companion object {
        const val DEFAULT_CALLBACK_QUEUE_CAPACITY = 512
        const val CALLBACK_THREAD_NAME = "local-llm-host-callback"
    }
}
