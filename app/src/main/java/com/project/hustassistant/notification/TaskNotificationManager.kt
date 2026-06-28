package com.project.hustassistant.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.project.hustassistant.data.task.Task
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
            .setSmallIcon(com.project.hustassistant.R.drawable.ic_notification) // Thay icon của bạn
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

    // "Chữ ký" alarm đã đặt cho mỗi task: taskId -> "$time|$title|$message".
    // Nhờ nó, khi đặt lại biết được alarm cũ ĐÃ CÓ chưa và có ĐÚNG (cùng giờ + nội dung)
    // không → THÊM (chưa có) / SỬA (có nhưng sai) / BỎ QUA (đã đúng). Tránh đặt trùng &
    // tránh nhắc sai. SharedPreferences sống qua reboot nên còn dùng để dọn alarm mồ côi.
    private val schedulePrefs = context.applicationContext
        .getSharedPreferences("scheduled_alarms", Context.MODE_PRIVATE)

    private fun signatureOf(timeInMillis: Long, title: String, message: String) =
        "$timeInMillis|$title|$message"

    private fun hasNotificationPermission(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun hasExactAlarmPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms() else true

    // Intent bắn alarm. PHẢI có "taskId" để TaskAlarmReceiver notify(taskId.hashCode()) —
    // mỗi task 1 notification riêng, không đè nhau (trước đây thiếu nên mọi noti dùng id 0).
    private fun alarmIntent(taskId: String, title: String, message: String) =
        Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra("taskId", taskId)
            putExtra("title", title)
            putExtra("message", message)
        }

    /** Đã có alarm đang đặt cho taskId chưa (FLAG_NO_CREATE: chỉ kiểm tra, không tạo mới). */
    private fun alarmExists(taskId: String): Boolean =
        PendingIntent.getBroadcast(
            context, taskId.hashCode(),
            Intent(context, TaskAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null

    fun scheduleTaskNotification(
        taskId: String,
        title: String,
        message: String,
        timeInMillis: Long
    ) {
        // Mốc đã ở quá khứ → không đặt, đồng thời dọn alarm cũ (nếu có) để khỏi nhắc sai.
        if (timeInMillis <= System.currentTimeMillis()) {
            cancelNotification(taskId)
            return
        }

        val newSig = signatureOf(timeInMillis, title, message)
        // ĐÃ CÓ alarm và ĐÚNG (cùng giờ + nội dung) → bỏ qua, tránh đặt trùng vô ích.
        if (alarmExists(taskId) && schedulePrefs.getString(taskId, null) == newSig) return

        // Chưa đủ quyền → không đặt được; KHÔNG ghi prefs để lần sau (mở app / được cấp
        // quyền / reboot) còn thử đặt lại.
        if (!hasNotificationPermission() || !hasExactAlarmPermission()) return

        // Tới đây: CHƯA có (thêm) hoặc CÓ nhưng SAI giờ/nội dung (sửa). setExactAndAllowWhileIdle
        // với cùng request code + FLAG_UPDATE_CURRENT = GHI ĐÈ alarm cũ → không bao giờ nhân đôi.
        val pendingIntent = PendingIntent.getBroadcast(
            context, taskId.hashCode(),
            alarmIntent(taskId, title, message),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        schedulePrefs.edit().putString(taskId, newSig).apply()
    }

    /**
     * Đặt lại TOÀN BỘ nhắc việc cho [tasks] hiện có — gọi sau reboot / khi mở app / sau
     * force stop (những lúc AlarmManager bị xoá sạch alarm). Idempotent nhờ [scheduleTaskNotification]:
     * task đã đúng thì bỏ qua, sai thì sửa, thiếu thì thêm. Cuối cùng huỷ alarm "mồ côi"
     * (task đã xoá / hoàn thành / mốc đã qua → không còn trong tập mong muốn).
     */
    fun rescheduleAll(tasks: List<Task>) {
        val now = System.currentTimeMillis()
        val desired = HashSet<String>()
        for (t in tasks) {
            if (t.isCompleted || t.start <= now) continue
            desired.add(t.id)
            scheduleTaskNotification(t.id, t.title, t.description, t.start)
        }
        for (id in schedulePrefs.all.keys.toList()) {
            if (id !in desired) cancelNotification(id)
        }
    }

    fun cancelNotification(taskId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context, taskId.hashCode(),
            Intent(context, TaskAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        schedulePrefs.edit().remove(taskId).apply()
    }
}