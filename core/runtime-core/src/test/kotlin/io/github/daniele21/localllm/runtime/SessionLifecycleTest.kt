package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLifecycleTest {
    @Test
    fun `open session acquires and releases request without closing`() {
        val lifecycle = SessionLifecycle()

        assertTrue(lifecycle.tryAcquireRequest())
        assertEquals(SessionLifecycleSnapshot(SessionLifecycleState.OPEN, 1), lifecycle.snapshot())
        assertFalse(lifecycle.releaseRequest())
        assertEquals(SessionLifecycleSnapshot(SessionLifecycleState.OPEN, 0), lifecycle.snapshot())
    }

    @Test
    fun `close waits for active request before release becomes ready`() {
        val lifecycle = SessionLifecycle()
        assertTrue(lifecycle.tryAcquireRequest())

        assertTrue(lifecycle.beginClose())

        assertEquals(SessionLifecycleSnapshot(SessionLifecycleState.CLOSING, 1), lifecycle.snapshot())
        assertFalse(lifecycle.isReleaseReady())
        assertTrue(lifecycle.releaseRequest())
        assertTrue(lifecycle.isReleaseReady())
    }

    @Test
    fun `closing rejects new request and close is idempotent`() {
        val lifecycle = SessionLifecycle()

        assertTrue(lifecycle.beginClose())

        assertFalse(lifecycle.beginClose())
        assertFalse(lifecycle.tryAcquireRequest())
        assertEquals(SessionLifecycleSnapshot(SessionLifecycleState.CLOSING, 0), lifecycle.snapshot())
    }

    @Test
    fun `release reservation is exclusive and success closes session`() {
        val lifecycle = SessionLifecycle()
        lifecycle.beginClose()

        assertTrue(lifecycle.tryBeginRelease())
        assertFalse(lifecycle.tryBeginRelease())
        assertEquals(SessionLifecycleState.RELEASING, lifecycle.snapshot().state)

        lifecycle.releaseSucceeded()

        assertEquals(SessionLifecycleSnapshot(SessionLifecycleState.CLOSED, 0), lifecycle.snapshot())
        assertFalse(lifecycle.beginClose())
        assertFalse(lifecycle.tryAcquireRequest())
    }

    @Test
    fun `failed release rolls back to closing and can be retried`() {
        val lifecycle = SessionLifecycle()
        lifecycle.beginClose()
        assertTrue(lifecycle.tryBeginRelease())

        lifecycle.releaseFailed()

        assertEquals(SessionLifecycleSnapshot(SessionLifecycleState.CLOSING, 0), lifecycle.snapshot())
        assertTrue(lifecycle.tryBeginRelease())
        lifecycle.releaseSucceeded()
        assertEquals(SessionLifecycleState.CLOSED, lifecycle.snapshot().state)
    }

    @Test
    fun `release cannot begin while request is active`() {
        val lifecycle = SessionLifecycle()
        lifecycle.tryAcquireRequest()
        lifecycle.beginClose()

        assertFalse(lifecycle.tryBeginRelease())
        assertEquals(SessionLifecycleSnapshot(SessionLifecycleState.CLOSING, 1), lifecycle.snapshot())
    }

    @Test(expected = IllegalStateException::class)
    fun `request count cannot underflow`() {
        SessionLifecycle().releaseRequest()
    }
}
