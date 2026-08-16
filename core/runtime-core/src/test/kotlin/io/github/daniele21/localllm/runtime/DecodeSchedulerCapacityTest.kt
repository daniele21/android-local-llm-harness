package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.RequestId
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DecodeSchedulerCapacityTest {
    @Test
    fun `rejects work beyond outstanding capacity and releases slot after queued cancellation`() {
        val scheduler = SingleDecodeScheduler(maxOutstandingRequests = 2)
        val activeStarted = CountDownLatch(1)
        val releaseActive = CountDownLatch(1)

        scheduler.submit(
            requestId = RequestId("active"),
            priority = DecodePriority.FOREGROUND,
            task = {
                activeStarted.countDown()
                releaseActive.await()
            },
            onQueuedCancellation = {},
            onRunningCancellation = {},
        )
        assertTrue(activeStarted.await(2, TimeUnit.SECONDS))

        val queued = scheduler.submit(
            requestId = RequestId("queued"),
            priority = DecodePriority.BACKGROUND,
            task = {},
            onQueuedCancellation = {},
            onRunningCancellation = {},
        )

        val rejection = runCatching {
            scheduler.submit(
                requestId = RequestId("rejected"),
                priority = DecodePriority.BACKGROUND,
                task = {},
                onQueuedCancellation = {},
                onRunningCancellation = {},
            )
        }.exceptionOrNull()
        assertTrue(rejection is DecodeQueueCapacityExceededException)

        assertTrue(queued.handle.cancel())

        val admitted = runCatching {
            scheduler.submit(
                requestId = RequestId("admitted-after-cancel"),
                priority = DecodePriority.BACKGROUND,
                task = {},
                onQueuedCancellation = {},
                onRunningCancellation = {},
            )
        }
        assertTrue(admitted.isSuccess)

        releaseActive.countDown()
        scheduler.close()
    }

    @Test
    fun `releases outstanding capacity after completed work`() {
        val scheduler = SingleDecodeScheduler(maxOutstandingRequests = 1)
        val completed = CountDownLatch(1)

        scheduler.submit(
            requestId = RequestId("first"),
            priority = DecodePriority.FOREGROUND,
            task = { completed.countDown() },
            onQueuedCancellation = {},
            onRunningCancellation = {},
        )
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertTrue(awaitCondition { scheduler.snapshot().activeRequest == null })

        val second = runCatching {
            scheduler.submit(
                requestId = RequestId("second"),
                priority = DecodePriority.FOREGROUND,
                task = {},
                onQueuedCancellation = {},
                onRunningCancellation = {},
            )
        }
        assertTrue(second.isSuccess)
        scheduler.close()
    }

    private fun awaitCondition(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}
