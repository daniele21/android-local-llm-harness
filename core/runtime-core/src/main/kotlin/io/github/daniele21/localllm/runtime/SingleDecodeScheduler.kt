package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.RequestId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class DecodePriority(val rank: Int) {
    USER_INTERACTIVE(0),
    FOREGROUND(1),
    BACKGROUND(2),
    MAINTENANCE(3),
}

data class DecodeSchedulerSnapshot(
    val activeRequest: RequestId?,
    val queuedRequests: Int,
    val closed: Boolean,
)

data class DecodeSubmission(
    val queuePosition: Int,
    val handle: DecodeTaskHandle,
)

interface DecodeTaskHandle {
    val requestId: RequestId
    fun cancel(): Boolean
}

class SingleDecodeScheduler(
    threadFactory: ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, "local-llm-decode").apply { isDaemon = true }
    },
) : AutoCloseable {
    private val queue = PriorityBlockingQueue<ScheduledWork>()
    private val works = ConcurrentHashMap<RequestId, ScheduledWork>()
    private val sequence = AtomicLong(0)
    private val closed = AtomicBoolean(false)
    private val activeRequest = AtomicReference<RequestId?>(null)
    private val worker = threadFactory.newThread(::runLoop).apply { start() }

    fun submit(
        requestId: RequestId,
        priority: DecodePriority,
        task: () -> Unit,
        onQueuedCancellation: () -> Unit,
        onRunningCancellation: () -> Unit,
        onQueued: (position: Int) -> Unit = {},
    ): DecodeSubmission {
        check(!closed.get()) { "Decode scheduler is closed" }
        val work = ScheduledWork(
            requestId = requestId,
            priority = priority,
            sequence = sequence.getAndIncrement(),
            task = task,
            onQueuedCancellation = onQueuedCancellation,
            onRunningCancellation = onRunningCancellation,
        )
        check(works.putIfAbsent(requestId, work) == null) {
            "A decode request with ID ${requestId.value} is already scheduled"
        }

        val position = queue.count { candidate -> candidate.compareTo(work) <= 0 } + 1
        runCatching { onQueued(position) }
            .onFailure { works.remove(requestId, work) }
            .getOrThrow()
        queue.put(work)
        return DecodeSubmission(
            queuePosition = position,
            handle = SchedulerHandle(requestId, this),
        )
    }

    fun cancel(requestId: RequestId): Boolean {
        val work = works[requestId] ?: return false
        if (!work.cancelled.compareAndSet(false, true)) {
            return false
        }

        when {
            work.started.get() -> work.onRunningCancellation()
            queue.remove(work) -> {
                works.remove(requestId, work)
                work.notifyQueuedCancellation()
            }
            work.started.get() -> work.onRunningCancellation()
        }
        return true
    }

    fun cancelAll(): Int = works.keys.count { requestId -> cancel(requestId) }

    fun snapshot(): DecodeSchedulerSnapshot = DecodeSchedulerSnapshot(
        activeRequest = activeRequest.get(),
        queuedRequests = queue.count { !it.cancelled.get() },
        closed = closed.get(),
    )

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        queue.toList().forEach { work -> cancel(work.requestId) }
        worker.interrupt()
    }

    private fun runLoop() {
        while (!closed.get()) {
            val work = try {
                queue.take()
            } catch (_: InterruptedException) {
                if (closed.get()) return
                continue
            }

            if (work.cancelled.get()) {
                works.remove(work.requestId, work)
                work.notifyQueuedCancellation()
                continue
            }
            if (!work.started.compareAndSet(false, true)) {
                continue
            }

            activeRequest.set(work.requestId)
            try {
                if (work.cancelled.get()) {
                    work.onRunningCancellation()
                } else {
                    work.task()
                }
            } finally {
                activeRequest.set(null)
                works.remove(work.requestId, work)
            }
        }
    }

    private class ScheduledWork(
        val requestId: RequestId,
        val priority: DecodePriority,
        val sequence: Long,
        val task: () -> Unit,
        val onQueuedCancellation: () -> Unit,
        val onRunningCancellation: () -> Unit,
    ) : Comparable<ScheduledWork> {
        val cancelled = AtomicBoolean(false)
        val started = AtomicBoolean(false)
        private val queuedCancellationNotified = AtomicBoolean(false)

        override fun compareTo(other: ScheduledWork): Int {
            val priorityComparison = priority.rank.compareTo(other.priority.rank)
            return if (priorityComparison != 0) priorityComparison else sequence.compareTo(other.sequence)
        }

        fun notifyQueuedCancellation() {
            if (queuedCancellationNotified.compareAndSet(false, true)) {
                onQueuedCancellation()
            }
        }
    }

    private class SchedulerHandle(
        override val requestId: RequestId,
        private val scheduler: SingleDecodeScheduler,
    ) : DecodeTaskHandle {
        override fun cancel(): Boolean = scheduler.cancel(requestId)
    }
}
