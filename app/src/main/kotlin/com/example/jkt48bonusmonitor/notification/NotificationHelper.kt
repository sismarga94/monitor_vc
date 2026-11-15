package com.example.jkt48bonusmonitor.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.jkt48bonusmonitor.MainActivity
import com.example.jkt48bonusmonitor.R

private const val CHANNEL_ID = "bonus_quota_alerts"
private const val NOTIFICATION_ID = 1001
private const val TARGET_MEMBER = "Astrella Virgiananda"
private const val QUOTA_THRESHOLD = 5

/**
 * Handles all notification creation and channel setup.
 */
class NotificationHelper(private val context: Context) {

    /**
     * Creates the notification channel. Must be called before posting any notification.
     * Safe to call multiple times.
     */
    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Returns true if the app has permission to post notifications.
     * On Android < 13 (API 32), notifications are always permitted unless the channel is blocked.
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Evaluates the notification condition and sends a notification if warranted.
     *
     * Deduplication logic (must be persisted externally via DataStore):
     * - Only notify if [availableQuota] < [QUOTA_THRESHOLD]
     * - Only notify if [availableQuota] != [lastNotifiedQuota]
     * This function is pure — it does not read or write DataStore itself.
     *
     * @param availableQuota  The current quota value.
     * @param lastNotifiedQuota The quota value at the time of the last notification (null = never).
     * @return true if a notification was sent.
     */
    fun evaluateAndNotify(availableQuota: Int, lastNotifiedQuota: Int?): Boolean {
        val shouldNotify = availableQuota < QUOTA_THRESHOLD &&
            availableQuota != lastNotifiedQuota

        if (!shouldNotify) return false
        if (!hasNotificationPermission()) return false

        sendLowQuotaNotification(availableQuota)
        return true
    }

    private fun sendLowQuotaNotification(availableQuota: Int) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$TARGET_MEMBER - Low Bonus Quota!")
            .setContentText("Available quota: $availableQuota")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Available quota: $availableQuota"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
