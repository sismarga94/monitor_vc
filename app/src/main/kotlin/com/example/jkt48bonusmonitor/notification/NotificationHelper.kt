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
import com.example.jkt48bonusmonitor.domain.model.MemberSlot

private const val CHANNEL_ID = "bonus_quota_alerts"
private const val BASE_NOTIFICATION_ID = 1001
const val QUOTA_THRESHOLD = 5

class NotificationHelper(private val context: Context) {

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
     * Evaluates all slots and sends one notification per slot that:
     * - has available_quota < QUOTA_THRESHOLD
     * - has a quota different from what was last notified for that slot
     *
     * @param slots          Current slots from the API.
     * @param notifiedMap    Map of sessionDetailCode -> last notified quota.
     * @return Updated map (only entries that triggered a new notification are updated).
     */
    fun evaluateAndNotify(
        slots: List<MemberSlot>,
        notifiedMap: Map<String, Int>
    ): Map<String, Int> {
        if (!hasNotificationPermission()) return notifiedMap

        val updatedMap = notifiedMap.toMutableMap()

        slots.forEach { slot ->
            val lastNotified = notifiedMap[slot.sessionDetailCode]
            val shouldNotify = slot.availableQuota < QUOTA_THRESHOLD &&
                slot.availableQuota != lastNotified

            if (shouldNotify) {
                sendSlotNotification(slot)
                updatedMap[slot.sessionDetailCode] = slot.availableQuota
            }
        }

        return updatedMap
    }

    private fun sendSlotNotification(slot: MemberSlot) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            slot.sessionDetailCode.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use a stable unique ID per slot so each slot gets its own notification
        val notifId = BASE_NOTIFICATION_ID + slot.sessionDetailCode.hashCode().and(0xFFFF)

        val body = "${slot.sessionDate} ${slot.sessionLabel} (${slot.slotLabel}) — Available quota: ${slot.availableQuota}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${slot.memberName} - Low Bonus Quota!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
