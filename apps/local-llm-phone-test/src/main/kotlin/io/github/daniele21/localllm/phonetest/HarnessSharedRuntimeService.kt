package io.github.daniele21.localllm.phonetest

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.daniele21.localllm.integration.servicehost.SharedRuntimeHostComposition
import io.github.daniele21.localllm.runtime.ActivityManagerLowMemoryProbe
import io.github.daniele21.localllm.runtime.AndroidMemoryPressureCallbacks

/** Bound-only proof host for the shared local-LLM runtime. */
class HarnessSharedRuntimeService : Service() {
    private lateinit var runtimeGraph: HarnessRuntimeGraph
    private lateinit var hostComposition: SharedRuntimeHostComposition
    private lateinit var memoryPressureCallbacks: AndroidMemoryPressureCallbacks

    override fun onCreate() {
        super.onCreate()
        runtimeGraph = HarnessRuntimeGraph.from(this)
        hostComposition = SharedRuntimeHostComposition(
            context = this,
            client = runtimeGraph.sharedRuntimeClient,
            permissionName = BuildConfig.SHARED_RUNTIME_PERMISSION,
            policies = HarnessSharedRuntimePolicy.authorizedClients(this),
            hostBuildId = "phone-test-${BuildConfig.VERSION_NAME}",
        )
        memoryPressureCallbacks = AndroidMemoryPressureCallbacks(
            lowMemoryProbe = ActivityManagerLowMemoryProbe(this),
            onPressure = runtimeGraph::handleMemoryPressure,
        )
    }

    override fun onBind(intent: Intent?): IBinder = hostComposition.binder

    override fun onTrimMemory(level: Int) {
        memoryPressureCallbacks.onTrimMemory(level)
        super.onTrimMemory(level)
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        memoryPressureCallbacks.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        hostComposition.close()
        super.onDestroy()
    }
}
