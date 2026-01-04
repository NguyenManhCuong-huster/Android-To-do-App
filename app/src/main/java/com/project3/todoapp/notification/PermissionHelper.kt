package com.project3.todoapp.notification

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri


object PermissionHelper {

    private const val PREF_NAME = "permission_prefs"
    private const val KEY_SKIP_NOTIF = "skip_notif_dialog"
    private const val KEY_SKIP_EXACT = "skip_exact_dialog"

    fun checkAndRequestPermissions(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. Kiểm tra quyền Thông báo (Android 13+)
        val hasNotif = NotificationManagerCompat.from(activity).areNotificationsEnabled()
        val skipNotif = prefs.getBoolean(KEY_SKIP_NOTIF, false)

        if (!hasNotif && !skipNotif) {
            showPermissionDialog(
                activity,
                "Quyền thông báo",
                "Ứng dụng cần quyền thông báo để nhắc nhở công việc. Bạn có muốn mở cài đặt không?",
                KEY_SKIP_NOTIF
            ) {
                openSettings(activity, Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
            }
            return // Hiển thị từng cái một để tránh chồng chéo
        }

        // 2. Kiểm tra quyền Báo thức chính xác (Android 12+)
        val hasExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms() else true
        val skipExact = prefs.getBoolean(KEY_SKIP_EXACT, false)

        if (!hasExact && !skipExact) {
            showPermissionDialog(
                activity,
                "Quyền báo thức chính xác",
                "Để nhắc nhở đúng giây, ứng dụng cần quyền báo thức chính xác. Mở cài đặt?",
                KEY_SKIP_EXACT
            ) {
                openSettings(activity, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) {
                    data = "package:${activity.packageName}".toUri()
                }
            }
        }
    }

    private fun showPermissionDialog(
        activity: Activity,
        title: String,
        message: String,
        prefKey: String,
        onConfirm: () -> Unit
    ) {
        val context = activity
        val builder = AlertDialog.Builder(context)

        // Tạo layout cho Checkbox
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
        }

        val checkBox = CheckBox(context).apply {
            text = "Không hỏi lại lần sau"
            textSize = 14f
        }
        container.addView(checkBox)

        builder.setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton("Cài đặt") { _, _ ->
                if (checkBox.isChecked) {
                    saveSkipPreference(activity, prefKey)
                }
                onConfirm()
            }
            .setNegativeButton("Để sau") { _, _ ->
                if (checkBox.isChecked) {
                    saveSkipPreference(activity, prefKey)
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun saveSkipPreference(activity: Activity, key: String) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(key, true) }
    }

    private fun openSettings(activity: Activity, action: String, block: Intent.() -> Unit = {}) {
        try {
            val intent = Intent(action).apply(block)
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Fallback nếu không mở được trang cài đặt cụ thể
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}