package io.github.daniele21.localllm.phonetest

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.daniele21.localllm.integration.servicehost.SharedRuntimeHostComposition
import io.github.daniele21.localllm.runtime.ActivityManagerLowMemoryProbe
import io.github.daniele21.localllm.runtime.AndroidMemoryPressureCallbacks
import io.github.daniele21.localllm.runtime.RuntimeMemoryPressure

/** Bound-only proof host for the shared local-LLM runtime. */
class HarnessSharedRuntimeService : Service() {
    private lateinit var runtimeGraph: HarnessRuntimeGraph
    private lateinit var hostComposition: SharedRuntimeHostComposition
    private lateinit var memoryPressureCallbacks: AndroidMemoryPressureCallbacks
    private lateinit var warmIdleResidency: HarnessWarmIdleResidencyCoordinator

    override fun onCreate() {
        super.onCreate()
        runtimeGraph = HarnessRuntimeGraph.from(this)
        hostComposition = SharedRuntimeHostComposition(
            context = this,
            client = runtimeGraph.sharedRuntimeClient,
            permissionName = BuildConfig.SHARED_RUNTIME_PERMISSION,
            policies = HarnessSharedRuntimePolicy.authorizedClients(this),
            hostBuildId = "phone-test-${BuildConfig.VERSION_NAME}",
            consumerClientFactory = runtimeGraph.consumerClientFactory,
        )
        val warmIdleClock = WarmIdleEpochClock { System.currentTimeMillis() }
        warmIdleResidency = HarnessWarmIdleResidencyCoordinator(
            ttlMs = CANDIDATE_WARM_IDLE_TTL_MS,
            clock = warmIdleClock,
            scheduler = AndroidWarmIdleDeadlineScheduler(Handler(Looper.getMainLooper()), warmIdleClock),
            resourcesResident = { runtimeGraph.runtimeSnapshot()?.loadedModel != null },
            unloadIdleResources = runtimeGraph::unloadIdleModel,
        )
        memoryPressureCallbacks = AndroidMemoryPressureCallbacks(
            lowMemoryProbe = ActivityManagerLowMemoryProbe(this),
            onPressure = { pressure ->
                if (pressure == RuntimeMemoryPressure.LOW_MEMORY) {
                    warmIdleResidency.onCriticalPressure()
                }
                runtimeGraph.handleMemoryPressure(pressure)
            },
        )
    }

    override fun onBind(intent: Intent?): IBinder {
        warmIdleResidency.onDemandPresent()
        return hostComposition.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        warmIdleResidency.onDemandAbsent()
        return true
    }

    override fun onRebind(intent: Intent?) {
        warmIdleResidency.onDemandPresent()
        super.onRebind(intent)
    }

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
        warmIdleResidency.close()
        hostComposition.close()
        super.onDestroy()
    }

    private companion object {
        // Harness candidate only; physical shared-runtime evidence may tune this policy before release promotion.
        const val CANDIDATE_WARM_IDLE_TTL_MS = 60_000L
    }
}
