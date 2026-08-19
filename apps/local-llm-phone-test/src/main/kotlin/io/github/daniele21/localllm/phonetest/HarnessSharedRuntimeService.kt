package io.github.daniele21.localllm.phonetest

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.daniele21.localllm.integration.servicehost.SharedRuntimeHostComposition
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.controlplane.room.RoomHostControlPlaneStoreOwner
import io.github.daniele21.localllm.runtime.ActivityManagerLowMemoryProbe
import io.github.daniele21.localllm.runtime.AndroidMemoryPressureCallbacks
import io.github.daniele21.localllm.runtime.RuntimeMemoryPressure

/** Bound-only proof host for the shared local-LLM runtime. */
class HarnessSharedRuntimeService : Service() {
    private lateinit var runtimeGraph: HarnessRuntimeGraph
    private lateinit var hostComposition: SharedRuntimeHostComposition
    private lateinit var memoryPressureCallbacks: AndroidMemoryPressureCallbacks
    private lateinit var warmIdleResidency: HarnessWarmIdleResidencyCoordinator
    private lateinit var controlPlaneStoreOwner: RoomHostControlPlaneStoreOwner

    override fun onCreate() {
        super.onCreate()
        runtimeGraph = HarnessRuntimeGraph.from(this)
        val resolvedWarmRetention = HarnessResolvedWarmRetentionCoordinator.from(this, runtimeGraph)
        val policies = HarnessSharedRuntimePolicy.authorizedClients(this)
        controlPlaneStoreOwner = RoomHostControlPlaneStoreOwner.open(this, CONTROL_PLANE_DATABASE_NAME)
        val controlPlaneHost = HarnessWarmRetentionAwareControlPlaneHost(
            delegate = HarnessConsumerControlPlaneHost(
                store = controlPlaneStoreOwner.store,
                modelStore = runtimeGraph.modelStore,
                runtimeGraph = runtimeGraph,
                applicationSeeds = applicationSeeds(policies),
                onWarmRetention = resolvedWarmRetention::schedule,
            ),
            warmRetention = resolvedWarmRetention,
        )
        hostComposition = SharedRuntimeHostComposition(
            context = this,
            client = runtimeGraph.sharedRuntimeClient,
            permissionName = BuildConfig.SHARED_RUNTIME_PERMISSION,
            policies = policies,
            hostBuildId = "phone-test-${BuildConfig.VERSION_NAME}",
            consumerClientFactory = runtimeGraph.consumerClientFactory,
            consumerControlPlaneHost = controlPlaneHost,
        )
        val warmIdleClock = WarmIdleEpochClock { System.currentTimeMillis() }
        warmIdleResidency = HarnessWarmIdleResidencyCoordinator(
            ttlMs = CANDIDATE_WARM_IDLE_TTL_MS,
            clock = warmIdleClock,
            scheduler = AndroidWarmIdleDeadlineScheduler(Handler(Looper.getMainLooper()), warmIdleClock),
            resourcesResident = {
                val digest = runtimeGraph.runtimeSnapshot()?.loadedModel
                digest != null && !runtimeGraph.activationResidency.protects(digest)
            },
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
        hostComposition.close()
        controlPlaneStoreOwner.close()
        warmIdleResidency.close()
        super.onDestroy()
    }

    private fun applicationSeeds(
        policies: List<io.github.daniele21.localllm.integration.servicehost.AuthorizedClientPolicy>,
    ): List<RegisteredApplication> {
        val now = System.currentTimeMillis()
        return policies
            .distinctBy { it.applicationId }
            .map { policy ->
                RegisteredApplication(
                    applicationId = policy.applicationId,
                    packageName = policy.packageName,
                    signerSha256 = policy.acceptedSigningCertificates.minOf { it.hex },
                    displayName = when (policy.applicationId) {
                        HarnessSharedRuntimeBindings.consoleApplicationId -> "Local LLM Console"
                        HarnessSharedRuntimeBindings.redactGuardApplicationId -> "RedactGuard"
                        else -> policy.applicationId.value
                    },
                    state = ApplicationRegistrationState.AUTHORIZED,
                    firstSeenAtEpochMs = now,
                    lastSeenAtEpochMs = now,
                )
            }
    }

    private companion object {
        const val CONTROL_PLANE_DATABASE_NAME = "harness-control-plane.db"

        // Compatibility-only policy for v1.1 consumers. v1.2 activations use resolved preset retention.
        const val CANDIDATE_WARM_IDLE_TTL_MS = 60_000L
    }
}
