package com.project3.todoapp.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class TaskNotificationManager(
    private val context: Context,
    private val externalScope: CoroutineScope // Dùng scope từ AppContainer
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val FEEDBACK_CHANNEL_ID = "feedback_channel"
    private val STATUS_NOTIF_ID = 999
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun createNotificationChannels() {
        // Channel cho báo thức (đã làm ở câu trước)
        val taskChannel = NotificationChannel(
            "task_channel",
            "Task Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )

        // Channel cho phản hồi trạng thái (Thêm, Sửa, Xóa)
        val feedbackChannel = NotificationChannel(
            FEEDBACK_CHANNEL_ID,
            "Status Updates",
            NotificationManager.IMPORTANCE_LOW // Độ ưu tiên thấp để không gây tiếng động quá lớn
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(taskChannel)
        manager?.createNotificationChannel(feedbackChannel)
    }

    // Hàm hiển thị thông báo trạng thái 5 giây
    fun showStatusNotification(message: String) {
        val builder = NotificationCompat.Builder(context, FEEDBACK_CHANNEL_ID)
            .setSmallIcon(com.project3.todoapp.R.drawable.ic_notification) // Thay icon của bạn
            .setContentTitle("Thông báo")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true) // Cho phép người dùng nhấn vào để xóa hoặc vuốt để xóa

        // Hiển thị thông báo
        try {
            notificationManager.notify(STATUS_NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            // Xử lý nếu chưa có quyền POST_NOTIFICATIONS trên Android 13+
        }

        // Tự động xóa sau 5 giây
        externalScope.launch {
            delay(5000)
            notificationManager.cancel(STATUS_NOTIF_ID)
        }
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            "task_channel",
            "Task Notifications",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun scheduleTaskNotification(
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

        // Kiểm tra quyền trước khi đặt lịch (Dùng ApplicationContext để check là đủ)
        val hasNotificationPermission =
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        val hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms() else true

        if (hasNotificationPermission && hasExactAlarmPermission) {
            cancelNotification(taskId) // Hủy cái cũ nếu có
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelNotification(taskId: String) {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}