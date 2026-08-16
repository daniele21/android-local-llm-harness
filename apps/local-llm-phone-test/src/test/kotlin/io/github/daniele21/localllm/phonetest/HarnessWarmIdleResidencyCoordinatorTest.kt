package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessWarmIdleResidencyCoordinatorTest {
    @Test
    fun `last demand schedules release only when resources are resident`() {
        val fixture = WarmIdleFixture(resourcesResident = true)

        fixture.coordinator.onDemandAbsent()

        assertEquals(1_100L, fixture.scheduler.deadlineEpochMs)
        assertEquals(0, fixture.unloadCalls)
    }

    @Test
    fun `demand return cancels pending release`() {
        val fixture = WarmIdleFixture(resourcesResident = true)
        fixture.coordinator.onDemandAbsent()

        fixture.coordinator.onDemandPresent()

        assertNull(fixture.scheduler.deadlineEpochMs)
        assertFalse(fixture.scheduler.hasTask())
    }

    @Test
    fun `deadline unloads idle resources`() {
        val fixture = WarmIdleFixture(resourcesResident = true, unloadSucceeds = true)
        fixture.coordinator.onDemandAbsent()
        fixture.nowEpochMs = 1_100

        fixture.scheduler.fire()

        assertEquals(1, fixture.unloadCalls)
        assertNull(fixture.scheduler.deadlineEpochMs)
    }

    @Test
    fun `busy runtime reschedules after failed idle release`() {
        val fixture = WarmIdleFixture(resourcesResident = true, unloadSucceeds = false)
        fixture.coordinator.onDemandAbsent()
        fixture.nowEpochMs = 1_100

        fixture.scheduler.fire()

        assertEquals(1, fixture.unloadCalls)
        assertEquals(1_200L, fixture.scheduler.deadlineEpochMs)
        assertTrue(fixture.scheduler.hasTask())
    }

    @Test
    fun `no resident resources do not schedule release`() {
        val fixture = WarmIdleFixture(resourcesResident = false)

        fixture.coordinator.onDemandAbsent()

        assertNull(fixture.scheduler.deadlineEpochMs)
        assertEquals(0, fixture.unloadCalls)
    }

    @Test
    fun `critical pressure cancels normal ttl timer`() {
        val fixture = WarmIdleFixture(resourcesResident = true)
        fixture.coordinator.onDemandAbsent()

        fixture.coordinator.onCriticalPressure()

        assertNull(fixture.scheduler.deadlineEpochMs)
        assertFalse(fixture.scheduler.hasTask())
    }
}

private class WarmIdleFixture(
    resourcesResident: Boolean,
    private val unloadSucceeds: Boolean = true,
) {
    var nowEpochMs = 1_000L
    private var resident = resourcesResident
    var unloadCalls = 0
    val scheduler = FakeWarmIdleDeadlineScheduler()
    val coordinator = HarnessWarmIdleResidencyCoordinator(
        ttlMs = 100,
        clock = WarmIdleEpochClock { nowEpochMs },
        scheduler = scheduler,
        resourcesResident = { resident },
        unloadIdleResources = {
            unloadCalls += 1
            if (unloadSucceeds) resident = false
            unloadSucceeds
        },
    )
}

private class FakeWarmIdleDeadlineScheduler : WarmIdleDeadlineScheduler {
    var deadlineEpochMs: Long? = null
    private var task: (() -> Unit)? = null

    override fun schedule(deadlineEpochMs: Long, task: () -> Unit) {
        this.deadlineEpochMs = deadlineEpochMs
        this.task = task
    }

    override fun cancel() {
        deadlineEpochMs = null
        task = null
    }

    fun hasTask(): Boolean = task != null

    fun fire() {
        val pending = task ?: error("No warm-idle task scheduled")
        task = null
        deadlineEpochMs = null
        pending()
    }
}
