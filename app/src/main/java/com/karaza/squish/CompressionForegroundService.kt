package com.karaza.squish

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Holds the process at foreground priority while an export or pass-through copy is
 * running. Without this, Android is free to kill the app if it's backgrounded
 * mid-export (screen off, app switched away) since a bare Activity+ViewModel carries
 * no special priority — the actual Transformer keeps running in [CompressionViewModel]
 * regardless of which component asked for foreground status; this service's only job
 * is to hold that status and show a notification while it does.
 */
class CompressionForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "compression"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CompressionForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CompressionForegroundService::class.java))
        }

        /** Safe to call even if the service isn't running — it's a no-op then. */
        fun updateProgress(context: Context, contentText: String, progress: Int) {
            if (!hasNotificationPermission(context)) return
            val notification = buildNotification(context, contentText, progress)
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        }

        private fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED

        private fun buildNotification(context: Context, contentText: String, progress: Int): Notification {
            ensureChannel(context)
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Squish")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp)
                .apply {
                    if (progress in 0..100) setProgress(100, progress, false) else setProgress(0, 0, true)
                }
                .build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Compression", NotificationManager.IMPORTANCE_LOW)
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification(this, "Compressing…", -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}
