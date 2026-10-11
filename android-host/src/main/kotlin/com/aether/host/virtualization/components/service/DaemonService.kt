package com.aether.host.virtualization.components.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.aether.host.AetherApplication
import com.aether.host.MainActivity
import com.aether.host.R
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger

/**
 * Host keep-alive service.
 *
 * Default behaviour is unchanged from the inert version: explicitly started, in-process,
 * `START_NOT_STICKY`, no foreground notification, no self-restart. Callers own its lifetime.
 *
 * With [HostFeature.DAEMON_KEEPALIVE] enabled it additionally:
 * - promotes itself to a foreground service, and
 * - restarts itself after `onTaskRemoved`, bounded by [DaemonRestartPolicy].
 *
 * The restart budget is the point. An unbounded `onTaskRemoved` restart is the classic keep-alive
 * anti-pattern: it fights the user's task swipe, burns battery, and gets the process killed by
 * the platform watchdog. The policy caps restarts inside a sliding window and applies exponential
 * backoff, so a user who keeps swiping eventually wins.
 *
 * `minSdk` is 24, so every notification-channel and foreground-type call is version-guarded. The
 * pre-O path still promotes to foreground, just without a channel, which the platform ignores.
 */
open class DaemonService : Service() {
    private val localBinder by lazy(LazyThreadSafetyMode.NONE) { LocalBinder() }

    /** Restart budget; process-local, never persisted. */
    val restartPolicy = DaemonRestartPolicy()

    private var foreground = false

    inner class LocalBinder : Binder() {
        fun service(): DaemonService = this@DaemonService
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        dispatch("created")
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatch("started", intent)
        if (flagger.isEnabled(HostFeature.DAEMON_KEEPALIVE)) {
            promoteToForeground()
            return START_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        dispatch("task-removed", rootIntent)
        if (flagger.isEnabled(HostFeature.DAEMON_KEEPALIVE)) {
            when (val decision = restartPolicy.onTaskRemoved(SystemClock.elapsedRealtime())) {
                is DaemonRestartPolicy.Decision.Restart -> scheduleRestart(decision.delayMillis)
                DaemonRestartPolicy.Decision.Exhausted ->
                    Log.w(TAG, "keep-alive restart budget exhausted; the host will not fight the task swipe")
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        dispatch("destroyed")
        if (foreground) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
            foreground = false
        }
        super.onDestroy()
    }

    private fun promoteToForeground() {
        if (foreground) return
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            }
        } else {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
        foreground = true
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.daemon_notification_title))
                .setContentText(getString(R.string.daemon_notification_text))
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.daemon_notification_title))
                .setContentText(getString(R.string.daemon_notification_text))
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.daemon_channel_name), NotificationManager.IMPORTANCE_MIN),
        )
    }

    private fun scheduleRestart(delayMillis: Long) {
        val restart = Intent(this, DaemonService::class.java)
        val pending = PendingIntent.getService(
            this,
            RESTART_REQUEST_CODE,
            restart,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarm = getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMillis
        // set(), not setExact(): the platform may batch this, which is the desired behaviour for
        // a keep-alive retry. Exact alarms would fight Doze for no user benefit.
        runCatching { alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending) }
            .onFailure { Log.w(TAG, "keep-alive restart could not be scheduled", it) }
    }

    private fun dispatch(event: String, intent: Intent? = null) {
        (application as? AetherApplication)?.hostInitializer?.dispatch(
            HostComponentEvent(
                owner = this,
                kind = HostComponentKind.SERVICE,
                slot = null,
                lifecycle = event,
                proxyType = "daemon",
                intent = intent,
            ),
        )
    }

    companion object {
        const val CHANNEL_ID = "aether.daemon"
        const val NOTIFICATION_ID = 0x4145
        private const val RESTART_REQUEST_CODE = 1
        private const val TAG = "AetherDaemon"
    }
}

/** Compatibility service component with the same safe, process-local behavior. */
class DaemonInnerService : DaemonService()
