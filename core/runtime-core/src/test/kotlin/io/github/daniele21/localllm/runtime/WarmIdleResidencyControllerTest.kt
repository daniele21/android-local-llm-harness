package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarmIdleResidencyControllerTest {
    @Test
    fun `absent demand schedules one release deadline when resources are resident`() {
        val controller = WarmIdleResidencyController(WarmIdleResidencyPolicy(ttlMs = 1_000))

        assertEquals(
            WarmIdleResidencyAction.ScheduleRelease(2_000),
            controller.onDemandAbsent(nowEpochMs = 1_000, resourcesResident = true),
        )
        assertEquals(WarmIdleResidencyAction.None, controller.onDemandAbsent(1_100, resourcesResident = true))
        assertEquals(2_000L, controller.scheduledDeadline())
    }

    @Test
    fun `present demand cancels pending release`() {
        val controller = WarmIdleResidencyController(WarmIdleResidencyPolicy(ttlMs = 1_000))
        controller.onDemandAbsent(1_000, resourcesResident = true)

        assertEquals(WarmIdleResidencyAction.CancelScheduledRelease, controller.onDemandPresent())
        assertNull(controller.scheduledDeadline())
        assertEquals(WarmIdleResidencyAction.None, controller.onDemandPresent())
    }

    @Test
    fun `deadline releases resources only after expiry`() {
        val controller = WarmIdleResidencyController(WarmIdleResidencyPolicy(ttlMs = 1_000))
        controller.onDemandAbsent(1_000, resourcesResident = true)

        assertEquals(WarmIdleResidencyAction.None, controller.onDeadline(1_999))
        assertEquals(WarmIdleResidencyAction.ReleaseIdleResources, controller.onDeadline(2_000))
        assertNull(controller.scheduledDeadline())
    }

    @Test
    fun `zero ttl requests immediate idle release`() {
        val controller = WarmIdleResidencyController(WarmIdleResidencyPolicy(ttlMs = 0))

        assertEquals(
            WarmIdleResidencyAction.ReleaseIdleResources,
            controller.onDemandAbsent(nowEpochMs = 1_000, resourcesResident = true),
        )
        assertNull(controller.scheduledDeadline())
    }

    @Test
    fun `no resident resources do not schedule expiry`() {
        val controller = WarmIdleResidencyController(WarmIdleResidencyPolicy(ttlMs = 1_000))

        assertEquals(
            WarmIdleResidencyAction.None,
            controller.onDemandAbsent(nowEpochMs = 1_000, resourcesResident = false),
        )
        assertNull(controller.scheduledDeadline())
    }

    @Test
    fun `critical pressure clears ttl and requests immediate release`() {
        val controller = WarmIdleResidencyController(WarmIdleResidencyPolicy(ttlMs = 1_000))
        controller.onDemandAbsent(1_000, resourcesResident = true)

        assertEquals(WarmIdleResidencyAction.ReleaseIdleResources, controller.onCriticalPressure())
        assertNull(controller.scheduledDeadline())
    }

    @Test
    fun `deadline overflow fails safe to immediate release`() {
        val controller = WarmIdleResidencyController(WarmIdleResidencyPolicy(ttlMs = 10))

        assertEquals(
            WarmIdleResidencyAction.ReleaseIdleResources,
            controller.onDemandAbsent(nowEpochMs = Long.MAX_VALUE - 5, resourcesResident = true),
        )
        assertNull(controller.scheduledDeadline())
    }
}
