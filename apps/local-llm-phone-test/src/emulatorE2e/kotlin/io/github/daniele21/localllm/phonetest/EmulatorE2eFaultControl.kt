package io.github.daniele21.localllm.phonetest

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Emulator-only control surface used by cross-APK lifecycle tests. */
internal class EmulatorE2eFaultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("UNUSED_VARIABLE")
        val unusedContext = context
        when (intent.action) {
            EmulatorE2eFaultActions.PAUSE_GENERATION -> EmulatorE2eGenerationGate.pause()

            EmulatorE2eFaultActions.RELEASE_GENERATION -> EmulatorE2eGenerationGate.release()

            EmulatorE2eFaultActions.RESET -> EmulatorE2eGenerationGate.reset()

            EmulatorE2eFaultActions.QUERY -> Unit

            else -> {
                resultCode = Activity.RESULT_CANCELED
                resultData = "unsupported"
                return
            }
        }
        resultCode = Activity.RESULT_OK
        resultData = EmulatorE2eGenerationGate.status()
    }
}

internal object EmulatorE2eFaultActions {
    const val PAUSE_GENERATION = "io.github.daniele21.localllm.phonetest.emulatorE2e.PAUSE_GENERATION"
    const val RELEASE_GENERATION = "io.github.daniele21.localllm.phonetest.emulatorE2e.RELEASE_GENERATION"
    const val RESET = "io.github.daniele21.localllm.phonetest.emulatorE2e.RESET"
    const val QUERY = "io.github.daniele21.localllm.phonetest.emulatorE2e.QUERY"
}

internal object EmulatorE2eGenerationGate {
    private val monitor = Object()
    private var paused = false
    private var waitingRequests = 0

    fun pause() {
        synchronized(monitor) {
            paused = true
        }
    }

    fun release() {
        synchronized(monitor) {
            paused = false
            monitor.notifyAll()
        }
    }

    fun reset() = release()

    fun awaitRelease(isCancelled: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + MAX_WAIT_MILLIS
        synchronized(monitor) {
            if (!paused) return !isCancelled()
            waitingRequests += 1
            monitor.notifyAll()
            try {
                while (paused) {
                    if (isCancelled()) return false
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    check(remaining > 0L) { "Emulator E2E generation gate timed out" }
                    try {
                        monitor.wait(minOf(remaining, CANCEL_POLL_MILLIS))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
            } finally {
                waitingRequests -= 1
                monitor.notifyAll()
            }
        }
        return !isCancelled()
    }

    fun status(): String = synchronized(monitor) {
        "paused=$paused;waiting=$waitingRequests"
    }

    private const val CANCEL_POLL_MILLIS = 100L
    private const val MAX_WAIT_MILLIS = 30_000L
}
