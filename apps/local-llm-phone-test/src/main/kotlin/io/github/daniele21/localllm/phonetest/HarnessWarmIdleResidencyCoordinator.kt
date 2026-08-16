package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.runtime.WarmIdleResidencyAction
import io.github.daniele21.localllm.runtime.WarmIdleResidencyController
import io.github.daniele21.localllm.runtime.WarmIdleResidencyPolicy

internal fun interface WarmIdleEpochClock {
    fun nowEpochMs(): Long
}

internal interface WarmIdleDeadlineScheduler {
    fun schedule(deadlineEpochMs: Long, task: () -> Unit)

    fun cancel()
}

internal class HarnessWarmIdleResidencyCoordinator(
    ttlMs: Long,
    private val clock: WarmIdleEpochClock,
    private val scheduler: WarmIdleDeadlineScheduler,
    private val resourcesResident: () -> Boolean,
    private val unloadIdleResources: () -> Boolean,
) : AutoCloseable {
    init {
        require(ttlMs > 0L) { "Shared-runtime warm-idle TTL must be positive" }
    }

    private val controller = WarmIdleResidencyController(WarmIdleResidencyPolicy(ttlMs))

    fun onDemandPresent() {
        apply(controller.onDemandPresent())
    }

    fun onDemandAbsent() {
        apply(controller.onDemandAbsent(clock.nowEpochMs(), resourcesResident()))
    }

    fun onCriticalPressure() {
        controller.onCriticalPressure()
        scheduler.cancel()
    }

    override fun close() {
        controller.clear()
        scheduler.cancel()
    }

    private fun onDeadline() {
        apply(controller.onDeadline(clock.nowEpochMs()))
    }

    private fun apply(action: WarmIdleResidencyAction) {
        when (action) {
            WarmIdleResidencyAction.None -> Unit
            WarmIdleResidencyAction.CancelScheduledRelease -> scheduler.cancel()
            WarmIdleResidencyAction.ReleaseIdleResources -> releaseOrReschedule()
            is WarmIdleResidencyAction.ScheduleRelease -> scheduler.schedule(action.deadlineEpochMs, ::onDeadline)
        }
    }

    private fun releaseOrReschedule() {
        scheduler.cancel()
        if (!unloadIdleResources() && resourcesResident()) {
            apply(controller.onDemandAbsent(clock.nowEpochMs(), resourcesResident = true))
        }
    }
}
