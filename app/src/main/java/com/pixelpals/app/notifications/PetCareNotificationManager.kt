package com.pixelpals.app.notifications

import android.Manifest
import android.annotation.SuppressLint
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
import com.pixelpals.app.R
import com.pixelpals.app.core.care.CareReminderDecision
import com.pixelpals.app.core.care.CareReminderType
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.status.PetDashboardActivity

object PetCareNotificationManager {
    const val CHANNEL_ID: String = "pixelpals_pet_care"
    const val ACTION_SNOOZE: String = "com.pixelpals.app.ACTION_SNOOZE_CARE"
    const val EXTRA_PET_ID: String = "pet_care_pet_id"
    private const val NOTIFICATION_ID: Int = 2101

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.care_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.care_notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, petType: PetType, decision: CareReminderDecision): Boolean {
        if (!canNotify(context)) return false
        createChannel(context)
        val petName: String = context.getString(petType.displayNameResId)
        val contentIntent = PendingIntent.getActivity(
            context,
            2_200 + decision.type.ordinal,
            PetDashboardActivity.createIntent(context, decision.action, "care_notification"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            2_300 + decision.type.ordinal,
            Intent(context, PetCareNotificationReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_PET_ID, petType.name.lowercase())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getTitle(context, petName, decision))
            .setContentText(getMessage(context, petName, decision))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (decision.isCritical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, context.getString(R.string.care_notification_action_care), contentIntent)
            .addAction(0, context.getString(R.string.care_notification_action_later), snoozeIntent)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.isSuccess
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun getTitle(context: Context, petName: String, decision: CareReminderDecision): String {
        val resourceId: Int = when (decision.type) {
            CareReminderType.SATIETY -> R.string.care_notification_satiety_title
            CareReminderType.ENERGY -> R.string.care_notification_energy_title
            CareReminderType.HYGIENE -> R.string.care_notification_hygiene_title
            CareReminderType.ATTENTION -> R.string.care_notification_attention_title
            CareReminderType.AT_RISK -> R.string.care_notification_risk_title
            CareReminderType.SICK -> R.string.care_notification_sick_title
        }
        return context.getString(resourceId, petName)
    }

    private fun getMessage(context: Context, petName: String, decision: CareReminderDecision): String {
        val resourceId: Int = when (decision.type) {
            CareReminderType.SATIETY -> if (decision.isCritical) {
                R.string.care_notification_satiety_critical_message
            } else {
                R.string.care_notification_satiety_message
            }
            CareReminderType.ENERGY -> R.string.care_notification_energy_message
            CareReminderType.HYGIENE -> R.string.care_notification_hygiene_message
            CareReminderType.ATTENTION -> R.string.care_notification_attention_message
            CareReminderType.AT_RISK -> R.string.care_notification_risk_message
            CareReminderType.SICK -> R.string.care_notification_sick_message
        }
        return context.getString(resourceId, petName)
    }
}
