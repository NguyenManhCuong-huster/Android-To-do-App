package com.project.hustassistant

import android.app.Application
import android.util.Log
import com.project.hustassistant.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodoApplication : Application() {
    // Khai báo container
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        // Khởi tạo container ngay khi app vừa chạy
        container = AppContainer(this)

        // Đặt lại nhắc việc mỗi lần process khởi động — bù cho FORCE STOP (OS huỷ hết alarm
        // tới khi user mở lại app) và mọi trường hợp alarm bị mất. Idempotent: task đã có
        // alarm đúng thì bỏ qua, sai thì sửa, thiếu thì thêm, mồ côi thì huỷ.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.notificationManager.rescheduleAll(container.taskRepository.getTasks())
            } catch (e: Exception) {
                Log.e("TodoApplication", "reschedule on launch failed", e)
            }
        }
    }
}