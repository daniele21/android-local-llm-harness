package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.RequestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SingleDecodeSchedulerTest {
    @Test
    fun `decode tasks never overlap`() {
        val scheduler = SingleDecodeScheduler()
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)
        val completed = CountDownLatch(3)

        repeat(3) { index ->
            scheduler.submit(
                requestId = RequestId("request-$index"),
                priority = DecodePriority.FOREGROUND,
                task = {
                    val current = active.incrementAndGet()
                    maximumActive.accumulateAndGet(current, ::maxOf)
                    Thread.sleep(20)
                    active.decrementAndGet()
                    completed.countDown()
                },
                onQueuedCancellation = {},
                onRunningCancellation = {},
            )
        }

        assertTrue(completed.await(3, TimeUnit.SECONDS))
        assertEquals(1, maximumActive.get())
        scheduler.close()
    }

    @Test
    fun `priority is honored while preserving fifo within priority`() {
        val scheduler = SingleDecodeScheduler()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val completed = CountDownLatch(4)
        val order = Collections.synchronizedList(mutableListOf<String>())

        scheduler.submit(
            RequestId("active"),
            DecodePriority.FOREGROUND,
            task = {
                firstStarted.countDown()
                releaseFirst.await()
                order += "active"
                completed.countDown()
            },
            onQueuedCancellation = {},
            onRunningCancellation = {},
        )
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

        fun enqueue(id: String, priority: DecodePriority) {
            scheduler.submit(
                RequestId(id),
                priority,
                task = {
                    order += id
                    completed.countDown()
                },
                onQueuedCancellation = {},
                onRunningCancellation = {},
            )
        }

        enqueue("background", DecodePriority.BACKGROUND)
        enqueue("interactive-1", DecodePriority.USER_INTERACTIVE)
        enqueue("interactive-2", DecodePriority.USER_INTERACTIVE)
        releaseFirst.countDown()

        assertTrue(completed.await(3, TimeUnit.SECONDS))
        assertEquals(
            listOf("active", "interactive-1", "interactive-2", "background"),
            order,
        )
        scheduler.close()
    }

    @Test
    fun `queued task can be cancelled without execution`() {
        val scheduler = SingleDecodeScheduler()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val queuedCancelled = CountDownLatch(1)
        var queuedRan = false

        scheduler.submit(
            RequestId("active"),
            DecodePriority.FOREGROUND,
            task = {
                firstStarted.countDown()
                releaseFirst.await()
            },
            onQueuedCancellation = {},
            onRunningCancellation = {},
        )
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

        val queued = scheduler.submit(
            RequestId("queued"),
            DecodePriority.BACKGROUND,
            task = { queuedRan = true },
            onQueuedCancellation = { queuedCancelled.countDown() },
            onRunningCancellation = {},
        )

        assertTrue(queued.handle.cancel())
        assertTrue(queuedCancelled.await(2, TimeUnit.SECONDS))
        releaseFirst.countDown()
        Thread.sleep(50)
        assertFalse(queuedRan)
        scheduler.close()
    }

    @Test
    fun `running cancellation delegates to active backend`() {
        val scheduler = SingleDecodeScheduler()
        val started = CountDownLatch(1)
        val cancellation = CountDownLatch(1)
        val release = CountDownLatch(1)

        val submission = scheduler.submit(
            RequestId("running"),
            DecodePriority.FOREGROUND,
            task = {
                started.countDown()
                release.await()
            },
            onQueuedCancellation = {},
            onRunningCancellation = {
                cancellation.countDown()
                release.countDown()
            },
        )

        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertTrue(submission.handle.cancel())
        assertTrue(cancellation.await(2, TimeUnit.SECONDS))
        scheduler.close()
    }

    @Test
    fun `snapshot distinguishes active and queued work`() {
        val scheduler = SingleDecodeScheduler()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        scheduler.submit(
            RequestId("active"),
            DecodePriority.FOREGROUND,
            task = {
                started.countDown()
                release.await()
            },
            onQueuedCancellation = {},
            onRunningCancellation = {},
        )
        assertTrue(started.await(2, TimeUnit.SECONDS))
        scheduler.submit(
            RequestId("queued"),
            DecodePriority.BACKGROUND,
            task = {},
            onQueuedCancellation = {},
            onRunningCancellation = {},
        )

        val snapshot = scheduler.snapshot()
        assertEquals(RequestId("active"), snapshot.activeRequest)
        assertEquals(1, snapshot.queuedRequests)
        assertFalse(snapshot.closed)

        release.countDown()
        assertTrue(
            awaitCondition(2, TimeUnit.SECONDS) {
                scheduler.snapshot().activeRequest == null
            },
        )
        scheduler.close()
        assertTrue(scheduler.snapshot().closed)
        assertNull(scheduler.snapshot().activeRequest)
    }

    private fun awaitCondition(timeout: Long, unit: TimeUnit, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return true
            }
            Thread.sleep(10)
        }
        return condition()
    }
}
