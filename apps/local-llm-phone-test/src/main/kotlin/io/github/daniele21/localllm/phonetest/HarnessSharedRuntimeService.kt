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

/** Shared-runtime proof host with execution-aware started/foreground lifetime for durable jobs. */
class HarnessSharedRuntimeService : Service() {
    private lateinit var runtimeGraph: HarnessRuntimeGraph
    private lateinit var hostComposition: SharedRuntimeHostComposition
    private lateinit var logicalJobLifetime: HarnessLogicalJobServiceLifetime
    private lateinit var memoryPressureCallbacks: AndroidMemoryPressureCallbacks
    private lateinit var warmIdleResidency: HarnessWarmIdleResidencyCoordinator

    override fun onCreate() {
        super.onCreate()
        logicalJobLifetime = HarnessLogicalJobServiceLifetime(this)
        runtimeGraph = HarnessRuntimeGraph.from(this)
        val resolvedWarmRetention = HarnessResolvedWarmRetentionCoordinator.from(runtimeGraph)
        val policies = runtimeGraph.authorizedClientPolicies
        val controlPlaneHost =
            HarnessWarmRetentionAwareControlPlaneHost(
                delegate =
                HarnessConsumerControlPlaneHost(
                    store = runtimeGraph.controlPlaneStore,
                    modelStore = runtimeGraph.modelStore,
                    runtimeGraph = runtimeGraph,
                    onWarmRetention = resolvedWarmRetention::schedule,
                ),
                warmRetention = resolvedWarmRetention,
            )
        val runtimeReadinessHost =
            HarnessConsumerRuntimeReadinessHost(
                activationResidency = runtimeGraph.activationResidency,
                snapshot = runtimeGraph::runtimeSnapshot,
            )
        hostComposition =
            SharedRuntimeHostComposition(
                context = this,
                client = runtimeGraph.sharedRuntimeClient,
                permissionName = BuildConfig.SHARED_RUNTIME_PERMISSION,
                policies = policies,
                policySource = runtimeGraph::liveAuthorizedClientPolicies,
                hostBuildId = "phone-test-${BuildConfig.VERSION_NAME}",
                consumerClientFactory = runtimeGraph.consumerClientFactory,
                consumerControlPlaneHost = controlPlaneHost,
                consumerRuntimeReadinessHost = runtimeReadinessHost,
            )
        hostComposition.setLogicalJobExecutionDemandListener(logicalJobLifetime::onDemandChanged)
        val warmIdleClock = WarmIdleEpochClock { System.currentTimeMillis() }
        warmIdleResidency =
            HarnessWarmIdleResidencyCoordinator(
                ttlMs = CANDIDATE_WARM_IDLE_TTL_MS,
                clock = warmIdleClock,
                scheduler = AndroidWarmIdleDeadlineScheduler(Handler(Looper.getMainLooper()), warmIdleClock),
                resourcesResident = {
                    val digest = runtimeGraph.runtimeSnapshot()?.loadedModel
                    digest != null && !runtimeGraph.activationResidency.protects(digest)
                },
                unloadIdleResources = runtimeGraph::unloadIdleModel,
            )
        memoryPressureCallbacks =
            AndroidMemoryPressureCallbacks(
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = logicalJobLifetime.onStartCommand(startId)

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
        logicalJobLifetime.close()
        warmIdleResidency.close()
        super.onDestroy()
    }

    private companion object {
        // Compatibility-only policy for v1.1 consumers. v1.2 activations use resolved preset retention.
        const val CANDIDATE_WARM_IDLE_TTL_MS = 60_000L
    }
}
