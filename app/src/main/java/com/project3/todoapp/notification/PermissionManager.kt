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
import com.project3.todoapp.R

class PermissionManager(private val appContext: Context) {

    private val PREF_NAME = "permission_prefs"
    private val KEY_SKIP_NOTIF = "skip_notif_dialog"
    private val KEY_SKIP_EXACT = "skip_exact_dialog"

    fun checkAndRequestPermissions(activity: Activity) {
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. Kiểm tra quyền Thông báo (Android 13+)
        val hasNotif = NotificationManagerCompat.from(activity).areNotificationsEnabled()
        val skipNotif = prefs.getBoolean(KEY_SKIP_NOTIF, false)

        if (!hasNotif && !skipNotif) {
            showPermissionDialog(
                activity,
                activity.getString(R.string.notification_permission),
                activity.getString(R.string.ask_notification_setting),
                KEY_SKIP_NOTIF
            ) {
                openSettings(activity, Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
            }
            return
        }

        // 2. Kiểm tra quyền Báo thức chính xác (Android 12+)
        val hasExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms() else true
        val skipExact = prefs.getBoolean(KEY_SKIP_EXACT, false)

        if (!hasExact && !skipExact) {
            showPermissionDialog(
                activity,
                activity.getString(R.string.schedule_exact_alarm_permission),
                activity.getString(R.string.ask_schedule_exact_alarm_permission),
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
        // Luôn dùng activity context để tạo Dialog, dùng appContext sẽ bị crash
        val builder = AlertDialog.Builder(activity)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
        }

        val checkBox = CheckBox(activity).apply {
            text = activity.getString(R.string.do_not_ask)
            textSize = 14f
        }
        container.addView(checkBox)

        builder.setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton(activity.getString(R.string.setting)) { _, _ ->
                if (checkBox.isChecked) saveSkipPreference(prefKey)
                onConfirm()
            }
            .setNegativeButton(activity.getString(R.string.not_now)) { _, _ ->
                if (checkBox.isChecked) saveSkipPreference(prefKey)
            }
            .setCancelable(false)
            .show()
    }

    private fun saveSkipPreference(key: String) {
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(key, true) }
    }

    private fun openSettings(activity: Activity, action: String, block: Intent.() -> Unit = {}) {
        try {
            val intent = Intent(action).apply(block)
            activity.startActivity(intent)
        } catch (e: Exception) {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}