package io.github.daniele21.localllm.phonetest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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
    private lateinit var memoryPressureCallbacks: AndroidMemoryPressureCallbacks
    private lateinit var warmIdleResidency: HarnessWarmIdleResidencyCoordinator
    private val mainHandler = Handler(Looper.getMainLooper())
    private var logicalJobDemandActive = false
    private var destroying = false

    override fun onCreate() {
        super.onCreate()
        ensureLogicalJobNotificationChannel()
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
                onLogicalJobExecutionDemandChanged = ::onLogicalJobExecutionDemandChanged,
            )
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (logicalJobDemandActive) {
            enterLogicalJobForeground()
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
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
        destroying = true
        mainHandler.removeCallbacksAndMessages(null)
        hostComposition.close()
        warmIdleResidency.close()
        if (logicalJobDemandActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            logicalJobDemandActive = false
        }
        super.onDestroy()
    }

    private fun onLogicalJobExecutionDemandChanged(active: Boolean) {
        mainHandler.post {
            if (!destroying) updateLogicalJobServiceLifetime(active)
        }
    }

    private fun updateLogicalJobServiceLifetime(active: Boolean) {
        if (logicalJobDemandActive == active) return
        logicalJobDemandActive = active
        if (active) {
            val startIntent =
                Intent(this, HarnessSharedRuntimeService::class.java).setAction(ACTION_DURABLE_LOGICAL_JOB)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
            enterLogicalJobForeground()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun enterLogicalJobForeground() {
        val notification = logicalJobNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                LOGICAL_JOB_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(LOGICAL_JOB_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureLogicalJobNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                LOGICAL_JOB_NOTIFICATION_CHANNEL,
                getString(R.string.logical_job_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.logical_job_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun logicalJobNotification(): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Builder(this, LOGICAL_JOB_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.harness_launcher_monochrome)
            .setContentTitle(getString(R.string.logical_job_notification_title))
            .setContentText(getString(R.string.logical_job_notification_text))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private companion object {
        // Compatibility-only policy for v1.1 consumers. v1.2 activations use resolved preset retention.
        const val CANDIDATE_WARM_IDLE_TTL_MS = 60_000L
        const val LOGICAL_JOB_NOTIFICATION_ID = 4102
        const val LOGICAL_JOB_NOTIFICATION_CHANNEL = "local_ai_execution"
        const val ACTION_DURABLE_LOGICAL_JOB = "io.github.daniele21.localllm.action.DURABLE_LOGICAL_JOB"
    }
}
