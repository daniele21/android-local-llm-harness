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
import android.os.Looper

/** Android adapter that keeps the shared-runtime service started/foreground only while durable work exists. */
internal class HarnessLogicalJobServiceLifetime(private val service: HarnessSharedRuntimeService) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var demandActive = false
    private var closed = false

    init {
        ensureNotificationChannel()
    }

    fun onDemandChanged(active: Boolean) {
        mainHandler.post {
            if (!closed) update(active)
        }
    }

    fun onStartCommand(startId: Int): Int {
        if (demandActive) {
            enterForeground()
        } else {
            service.stopSelf(startId)
        }
        return Service.START_NOT_STICKY
    }

    override fun close() {
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        if (demandActive) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            demandActive = false
        }
    }

    private fun update(active: Boolean) {
        if (demandActive == active) return
        demandActive = active
        if (active) {
            val startIntent =
                Intent(service, HarnessSharedRuntimeService::class.java).setAction(ACTION_DURABLE_LOGICAL_JOB)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                service.startForegroundService(startIntent)
            } else {
                service.startService(startIntent)
            }
            enterForeground()
        } else {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            service.stopSelf()
        }
    }

    private fun enterForeground() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(
                LOGICAL_JOB_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            service.startForeground(LOGICAL_JOB_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                LOGICAL_JOB_NOTIFICATION_CHANNEL,
                service.getString(R.string.logical_job_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = service.getString(R.string.logical_job_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun notification(): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                service,
                0,
                Intent(service, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Builder(service, LOGICAL_JOB_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.harness_launcher_monochrome)
            .setContentTitle(service.getString(R.string.logical_job_notification_title))
            .setContentText(service.getString(R.string.logical_job_notification_text))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private companion object {
        const val LOGICAL_JOB_NOTIFICATION_ID = 4102
        const val LOGICAL_JOB_NOTIFICATION_CHANNEL = "local_ai_execution"
        const val ACTION_DURABLE_LOGICAL_JOB = "io.github.daniele21.localllm.action.DURABLE_LOGICAL_JOB"
    }
}
