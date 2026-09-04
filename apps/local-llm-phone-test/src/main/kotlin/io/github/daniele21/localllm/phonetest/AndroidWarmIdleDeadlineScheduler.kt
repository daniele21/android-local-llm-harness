package io.github.daniele21.localllm.phonetest

import android.os.Handler

internal class AndroidWarmIdleDeadlineScheduler(private val handler: Handler, private val clock: WarmIdleEpochClock) :
    WarmIdleDeadlineScheduler {
    private var pending: Runnable? = null

    override fun schedule(deadlineEpochMs: Long, task: () -> Unit) {
        cancel()
        val runnable = Runnable {
            pending = null
            task()
        }
        pending = runnable
        val nowEpochMs = clock.nowEpochMs()
        val delayMs = if (deadlineEpochMs <= nowEpochMs) 0L else deadlineEpochMs - nowEpochMs
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}
