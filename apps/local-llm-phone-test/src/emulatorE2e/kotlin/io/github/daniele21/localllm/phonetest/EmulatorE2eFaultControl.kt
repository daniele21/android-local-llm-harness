package io.github.daniele21.localllm.phonetest

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Emulator-only control surface used by cross-APK lifecycle tests. */
internal class EmulatorE2eFaultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        applyResult(EmulatorE2eFaultCommandHandler.handle(context, intent))
    }

    private fun applyResult(result: EmulatorE2eFaultCommandResult) {
        resultCode = result.code
        resultData = result.data
    }
}

/**
 * Shell-only emulator bridge for independently signed Consumer E2E.
 *
 * This receiver lives in the Host emulatorE2e APK, is reachable only to callers holding the
 * platform DUMP permission, and exposes only the same bounded test commands owned by
 * [EmulatorE2eFaultCommandHandler]. Keeping the bridge in the Host process avoids a nested ordered
 * broadcast from the androidTest APK while the outer shell broadcast is still being delivered.
 */
internal class HarnessEmulatorE2eShellBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in EmulatorE2eFaultActions.SHELL_ALLOWED_ACTIONS) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "unsupported"
            return
        }
        val result = EmulatorE2eFaultCommandHandler.handle(context, intent)
        resultCode = result.code
        resultData = result.data
    }
}

internal data class EmulatorE2eFaultCommandResult(val code: Int, val data: String?)

/** Canonical emulator-only command owner shared by the protected receiver and shell bridge. */
internal object EmulatorE2eFaultCommandHandler {
    fun handle(context: Context, intent: Intent): EmulatorE2eFaultCommandResult {
        if (intent.action == EmulatorE2eFaultActions.QUERY_ACTIVITY) {
            val verifiedPackageName = intent.getStringExtra(EmulatorE2eFaultActions.EXTRA_VERIFIED_PACKAGE)
            if (verifiedPackageName.isNullOrBlank()) {
                return EmulatorE2eFaultCommandResult(Activity.RESULT_CANCELED, "invalid_request")
            }
            return EmulatorE2eFaultCommandResult(
                Activity.RESULT_OK,
                EmulatorE2eActivityAuditStatus.query(context, verifiedPackageName),
            )
        }
        if (intent.action == EmulatorE2eFaultActions.RUN_INTERNAL_ACTIVITY_PROBE) {
            return EmulatorE2eFaultCommandResult(
                Activity.RESULT_OK,
                EmulatorE2eInternalActivityProbe.run(context),
            )
        }

        when (intent.action) {
            EmulatorE2eFaultActions.PAUSE_GENERATION -> EmulatorE2eGenerationGate.pause()

            EmulatorE2eFaultActions.RELEASE_GENERATION -> EmulatorE2eGenerationGate.release()

            EmulatorE2eFaultActions.FAIL_NEXT_GENERATION -> EmulatorE2eBackendFailureGate.arm()

            EmulatorE2eFaultActions.RESET -> {
                EmulatorE2eGenerationGate.reset()
                EmulatorE2eBackendFailureGate.reset()
            }

            EmulatorE2eFaultActions.QUERY -> Unit

            else -> return EmulatorE2eFaultCommandResult(Activity.RESULT_CANCELED, "unsupported")
        }
        return EmulatorE2eFaultCommandResult(
            Activity.RESULT_OK,
            EmulatorE2eGenerationGate.status(),
        )
    }
}

internal object EmulatorE2eFaultActions {
    const val PAUSE_GENERATION = "io.github.daniele21.localllm.phonetest.emulatorE2e.PAUSE_GENERATION"
    const val RELEASE_GENERATION = "io.github.daniele21.localllm.phonetest.emulatorE2e.RELEASE_GENERATION"
    const val FAIL_NEXT_GENERATION = "io.github.daniele21.localllm.phonetest.emulatorE2e.FAIL_NEXT_GENERATION"
    const val RUN_INTERNAL_ACTIVITY_PROBE = "io.github.daniele21.localllm.phonetest.emulatorE2e.RUN_INTERNAL_ACTIVITY_PROBE"
    const val RESET = "io.github.daniele21.localllm.phonetest.emulatorE2e.RESET"
    const val QUERY = "io.github.daniele21.localllm.phonetest.emulatorE2e.QUERY"
    const val QUERY_ACTIVITY = "io.github.daniele21.localllm.phonetest.emulatorE2e.QUERY_ACTIVITY"
    const val EXTRA_VERIFIED_PACKAGE = "verified_package"

    val SHELL_ALLOWED_ACTIONS =
        setOf(
            PAUSE_GENERATION,
            RELEASE_GENERATION,
            FAIL_NEXT_GENERATION,
            RUN_INTERNAL_ACTIVITY_PROBE,
            RESET,
            QUERY,
            QUERY_ACTIVITY,
        )
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

/** One-shot emulator-only fault used to prove a genuine FAILED audit terminal without production hooks. */
internal object EmulatorE2eBackendFailureGate {
    private val monitor = Any()
    private var armed = false

    fun arm() {
        synchronized(monitor) {
            armed = true
        }
    }

    fun consume(): Boolean = synchronized(monitor) {
        val shouldFail = armed
        armed = false
        shouldFail
    }

    fun reset() {
        synchronized(monitor) {
            armed = false
        }
    }
}
