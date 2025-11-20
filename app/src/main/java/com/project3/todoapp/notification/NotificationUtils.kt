package com.project3.todoapp.notification

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.CheckBox
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.project3.todoapp.R


class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationUtils.createNotificationChannel(context)

        val title = intent.getStringExtra("title") ?: "Task reminder"
        val message = intent.getStringExtra("message") ?: "It's time to start!"

        val builder = NotificationCompat.Builder(context, "task_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), builder.build())
        }

    }
}

object NotificationUtils {

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            "task_channel",
            "Task Notifications",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun scheduleTaskNotification(
        context: Context,
        taskId: String,
        title: String,
        message: String,
        timeInMillis: Long
    ) {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationManager = NotificationManagerCompat.from(context)

        val hasNotificationPermission = notificationManager.areNotificationsEnabled()
        val hasExactAlarmPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                alarmManager.canScheduleExactAlarms()
            else true

        if (hasNotificationPermission && hasExactAlarmPermission) {
            // Delete if old notification exist
            cancelTaskNotification(context, taskId)
            // New schedule
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        }
    }

    fun checkAndRequestPermissions(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val skipNotifDialog = prefs.getBoolean("skip_notification_dialog", false)
        val skipExactDialog = prefs.getBoolean("skip_exact_alarm_dialog", false)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationManager = NotificationManagerCompat.from(context)

        val hasNotificationPermission = notificationManager.areNotificationsEnabled()
        val hasExactAlarmPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                alarmManager.canScheduleExactAlarms()
            else true

        if (!hasNotificationPermission && !skipNotifDialog) {
            showPermissionDialog(
                context,
                "Quyền thông báo",
                "Ứng dụng chưa có quyền gửi thông báo. Mở cài đặt để bật?",
                "skip_notification_dialog"
            ) { openNotificationSettings(context) }
        }

        if (!hasExactAlarmPermission && !skipExactDialog) {
            showPermissionDialog(
                context,
                "Quyền báo thức chính xác",
                "Ứng dụng cần quyền báo thức chính xác để nhắc việc đúng giờ. Mở cài đặt?",
                "skip_exact_alarm_dialog"
            ) { openExactAlarmSettings(context) }
        }
    }


    private fun showPermissionDialog(
        context: Context,
        title: String,
        message: String,
        prefKey: String,
        onAccept: () -> Unit
    ) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val checkbox = CheckBox(context).apply { text = "Don't ask" }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(checkbox)
            .setPositiveButton("Yes") { _, _ ->
                if (checkbox.isChecked) prefs.edit { putBoolean(prefKey, true) }
                onAccept()
            }
            .setNegativeButton("No") { _, _ ->
                if (checkbox.isChecked) prefs.edit { putBoolean(prefKey, true) }
            }
            .show()
    }


    fun cancelTaskNotification(context: Context, taskId: String) {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }
}
