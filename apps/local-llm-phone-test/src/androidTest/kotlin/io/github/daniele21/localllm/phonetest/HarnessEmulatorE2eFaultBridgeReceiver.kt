package io.github.daniele21.localllm.phonetest

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Shell-only bridge for cross-signer emulator E2E.
 *
 * The production-shaped consumer must never receive Harnex's signature-only fault-control
 * permission. Instead adb shell reaches this receiver in the co-signed androidTest APK, which then
 * relays only the bounded emulator actions to the real signature-protected Harnex receiver.
 *
 * The outer `am broadcast` expects the final ordered-broadcast result synchronously. The relay's
 * final receiver therefore runs on a dedicated HandlerThread while this receiver waits for the
 * bounded result; relying on `goAsync()` here can let ActivityManager complete the outer shell
 * broadcast before the nested result code/data are observable.
 */
class HarnessEmulatorE2eFaultBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in ALLOWED_ACTIONS) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "unsupported"
            return
        }

        val forwarded =
            Intent(action)
                .setComponent(ComponentName(HOST_PACKAGE, HOST_FAULT_RECEIVER))
                .apply {
                    intent.getStringExtra(EXTRA_VERIFIED_PACKAGE)?.let { verifiedPackage ->
                        putExtra(EXTRA_VERIFIED_PACKAGE, verifiedPackage)
                    }
                }
        val completed = CountDownLatch(1)
        val forwardedResultCode = AtomicInteger(Activity.RESULT_CANCELED)
        val forwardedResultData = AtomicReference<String?>(null)
        val callbackThread = HandlerThread("harnex-emulator-e2e-fault-bridge").apply { start() }

        try {
            @Suppress("DEPRECATION")
            context.sendOrderedBroadcast(
                forwarded,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        forwardedResultCode.set(resultCode)
                        forwardedResultData.set(resultData)
                        completed.countDown()
                    }
                },
                Handler(callbackThread.looper),
                Activity.RESULT_CANCELED,
                null,
                null,
            )
            if (!completed.await(BRIDGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                resultCode = Activity.RESULT_CANCELED
                resultData = "bridge_timeout"
                return
            }
            resultCode = forwardedResultCode.get()
            resultData = forwardedResultData.get()
        } catch (failure: RuntimeException) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "bridge_error=${failure.javaClass.simpleName}"
        } finally {
            callbackThread.quitSafely()
        }
    }

    private companion object {
        const val HOST_PACKAGE = "io.github.daniele21.localllm.phonetest.debug"
        const val HOST_FAULT_RECEIVER = "io.github.daniele21.localllm.phonetest.EmulatorE2eFaultReceiver"
        const val EXTRA_VERIFIED_PACKAGE = "verified_package"
        const val BRIDGE_TIMEOUT_SECONDS = 3L

        val ALLOWED_ACTIONS =
            setOf(
                "io.github.daniele21.localllm.phonetest.emulatorE2e.PAUSE_GENERATION",
                "io.github.daniele21.localllm.phonetest.emulatorE2e.RELEASE_GENERATION",
                "io.github.daniele21.localllm.phonetest.emulatorE2e.FAIL_NEXT_GENERATION",
                "io.github.daniele21.localllm.phonetest.emulatorE2e.RUN_INTERNAL_ACTIVITY_PROBE",
                "io.github.daniele21.localllm.phonetest.emulatorE2e.RESET",
                "io.github.daniele21.localllm.phonetest.emulatorE2e.QUERY",
                "io.github.daniele21.localllm.phonetest.emulatorE2e.QUERY_ACTIVITY",
            )
    }
}
