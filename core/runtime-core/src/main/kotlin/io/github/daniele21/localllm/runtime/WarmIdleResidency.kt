package io.github.daniele21.localllm.runtime

data class WarmIdleResidencyPolicy(val ttlMs: Long) {
    init {
        require(ttlMs >= 0L) { "Warm-idle TTL must not be negative" }
    }
}

sealed interface WarmIdleResidencyAction {
    data object None : WarmIdleResidencyAction

    data class ScheduleRelease(val deadlineEpochMs: Long) : WarmIdleResidencyAction

    data object CancelScheduledRelease : WarmIdleResidencyAction

    data object ReleaseIdleResources : WarmIdleResidencyAction
}

class WarmIdleResidencyController(private val policy: WarmIdleResidencyPolicy) {
    private var scheduledDeadlineEpochMs: Long? = null

    fun onDemandAbsent(nowEpochMs: Long, resourcesResident: Boolean): WarmIdleResidencyAction {
        require(nowEpochMs >= 0L) { "Warm-idle timestamp must not be negative" }
        return when {
            !resourcesResident -> {
                scheduledDeadlineEpochMs = null
                WarmIdleResidencyAction.None
            }

            scheduledDeadlineEpochMs != null -> WarmIdleResidencyAction.None

            policy.ttlMs == 0L -> WarmIdleResidencyAction.ReleaseIdleResources

            else -> scheduleRelease(nowEpochMs)
        }
    }

    fun onDemandPresent(): WarmIdleResidencyAction {
        if (scheduledDeadlineEpochMs == null) {
            return WarmIdleResidencyAction.None
        }
        scheduledDeadlineEpochMs = null
        return WarmIdleResidencyAction.CancelScheduledRelease
    }

    fun onDeadline(nowEpochMs: Long): WarmIdleResidencyAction {
        require(nowEpochMs >= 0L) { "Warm-idle timestamp must not be negative" }
        val deadline = scheduledDeadlineEpochMs ?: return WarmIdleResidencyAction.None
        if (nowEpochMs < deadline) {
            return WarmIdleResidencyAction.None
        }
        scheduledDeadlineEpochMs = null
        return WarmIdleResidencyAction.ReleaseIdleResources
    }

    fun onCriticalPressure(): WarmIdleResidencyAction {
        scheduledDeadlineEpochMs = null
        return WarmIdleResidencyAction.ReleaseIdleResources
    }

    fun clear(): WarmIdleResidencyAction {
        if (scheduledDeadlineEpochMs == null) {
            return WarmIdleResidencyAction.None
        }
        scheduledDeadlineEpochMs = null
        return WarmIdleResidencyAction.CancelScheduledRelease
    }

    fun scheduledDeadline(): Long? = scheduledDeadlineEpochMs

    private fun scheduleRelease(nowEpochMs: Long): WarmIdleResidencyAction {
        val deadline = safeDeadline(nowEpochMs) ?: return WarmIdleResidencyAction.ReleaseIdleResources
        scheduledDeadlineEpochMs = deadline
        return WarmIdleResidencyAction.ScheduleRelease(deadline)
    }

    private fun safeDeadline(nowEpochMs: Long): Long? = try {
        Math.addExact(nowEpochMs, policy.ttlMs)
    } catch (_: ArithmeticException) {
        null
    }
}
