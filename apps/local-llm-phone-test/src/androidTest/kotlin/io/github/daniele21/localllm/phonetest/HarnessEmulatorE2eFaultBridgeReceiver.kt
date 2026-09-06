package io.github.daniele21.localllm.phonetest

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Shell-only bridge for cross-signer emulator E2E.
 *
 * The production-shaped consumer must never receive Harnex's signature-only fault-control
 * permission. Instead adb shell reaches this receiver in the co-signed androidTest APK, which then
 * relays only the bounded emulator actions to the real signature-protected Harnex receiver.
 */
class HarnessEmulatorE2eFaultBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action !in ALLOWED_ACTIONS) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "unsupported"
            return
        }

        val pendingResult = goAsync()
        val forwarded =
            Intent(action)
                .setComponent(ComponentName(HOST_PACKAGE, HOST_FAULT_RECEIVER))
                .apply {
                    intent.getStringExtra(EXTRA_VERIFIED_PACKAGE)?.let { verifiedPackage ->
                        putExtra(EXTRA_VERIFIED_PACKAGE, verifiedPackage)
                    }
                }

        runCatching {
            @Suppress("DEPRECATION")
            context.sendOrderedBroadcast(
                forwarded,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        pendingResult.setResultCode(resultCode)
                        pendingResult.setResultData(resultData)
                        pendingResult.finish()
                    }
                },
                null,
                Activity.RESULT_CANCELED,
                null,
                null,
            )
        }.onFailure { failure ->
            pendingResult.setResultCode(Activity.RESULT_CANCELED)
            pendingResult.setResultData("bridge_error=${failure.javaClass.simpleName}")
            pendingResult.finish()
        }
    }

    private companion object {
        const val HOST_PACKAGE = "io.github.daniele21.localllm.phonetest.debug"
        const val HOST_FAULT_RECEIVER = "io.github.daniele21.localllm.phonetest.EmulatorE2eFaultReceiver"
        const val EXTRA_VERIFIED_PACKAGE = "verified_package"

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
