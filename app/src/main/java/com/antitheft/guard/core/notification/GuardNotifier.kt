package com.antitheft.guard.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.antitheft.guard.R

/**
 * Owns both notification channels so the ids live in exactly one place.
 *
 * The ongoing status notice and the alerts are deliberately separate channels: a user who mutes
 * the permanent "protection is on" notice must still be woken by an actual alert.
 */
class GuardNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_status_description)
            setShowBadge(false)
        }

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alerts_description)
        }

        manager.createNotificationChannels(listOf(status, alerts))
    }

    /**
     * The ongoing notice the foreground service runs under. The caller supplies the intents so
     * this class stays free of any dependency on the service or the UI it launches.
     */
    fun ongoingNotification(
        text: String,
        contentIntent: PendingIntent,
        turnOffIntent: PendingIntent,
    ): Notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
        .setSmallIcon(R.drawable.ic_shield)
        .setContentTitle(context.getString(R.string.notification_status_title))
        .setContentText(text)
        .setContentIntent(contentIntent)
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .addAction(0, context.getString(R.string.notification_action_turn_off), turnOffIntent)
        .build()

    fun showOngoing(notification: Notification) = notify(ONGOING_ID, notification)

    fun showAlert(id: Int, title: String, text: String, contentIntent: PendingIntent) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        notify(id, notification)
    }

    /**
     * Nothing is posted without the permission. A silently dropped notification is the right
     * outcome here: the user is told what the switch needs when they arm a feature, and the
     * service's own foreground notice does not go through this path.
     */
    private fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(id, notification)
    }

    companion object {
        const val ONGOING_ID = 1

        private const val CHANNEL_STATUS = "guard_status"
        private const val CHANNEL_ALERTS = "guard_alerts"
    }
}
