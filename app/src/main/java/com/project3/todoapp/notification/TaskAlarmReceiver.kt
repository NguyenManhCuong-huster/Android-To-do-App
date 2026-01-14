package com.project3.todoapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.project3.todoapp.R

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: context.getString(R.string.task_reminder)
        val message =
            intent.getStringExtra("message") ?: context.getString(R.string.task_reminder_message)
        val taskId = intent.getStringExtra("taskId") ?: ""

        val builder = NotificationCompat.Builder(context, "task_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)
        // Dùng hashCode của taskId để mỗi Task hiện 1 thông báo riêng, không đè nhau
        try {
            notificationManager.notify(taskId.hashCode(), builder.build())
        } catch (e: SecurityException) { /* Handle missing permission */
        }
    }
}