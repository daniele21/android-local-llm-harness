package io.github.daniele21.localllm.phonetest

import android.content.Context

/**
 * Temporary construction adapters used while MainActivity is migrated to an application-level
 * composition root. Both adapters resolve the same process-scoped [HarnessRuntimeGraph], so the
 * legacy call sites no longer create independent stores or orchestrators.
 */
internal fun PhoneTestController(context: Context, listener: PhoneTestListener): PhoneTestController = PhoneTestController(
    context = context,
    runtimeGraph = HarnessRuntimeGraph.from(context),
    listener = listener,
)

internal fun PhonePlaygroundController(context: Context, listener: (PlaygroundState) -> Unit): PhonePlaygroundController =
    PhonePlaygroundController(
        runtimeGraph = HarnessRuntimeGraph.from(context),
        listener = listener,
    )
