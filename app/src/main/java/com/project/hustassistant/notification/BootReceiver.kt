package com.project.hustassistant.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.project.hustassistant.TodoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BootReceiver — đặt lại toàn bộ nhắc việc sau khi:
 *   • máy KHỞI ĐỘNG LẠI (BOOT_COMPLETED): AlarmManager bị xoá sạch alarm khi reboot →
 *     không có receiver này thì mất hết nhắc việc.
 *   • app được CẬP NHẬT (MY_PACKAGE_REPLACED): cài đè bản mới cũng xoá alarm cũ.
 *
 * Đọc task từ Room rồi gọi rescheduleAll() (idempotent → không nhân đôi).
 * Dùng goAsync() để giữ process sống trong lúc đọc DB + đặt lịch (giới hạn ~10s, đủ).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val container = (context.applicationContext as TodoApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.notificationManager.rescheduleAll(container.taskRepository.getTasks())
            } catch (e: Exception) {
                Log.e(TAG, "reschedule after boot/update failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object { const val TAG = "BootReceiver" }
}
