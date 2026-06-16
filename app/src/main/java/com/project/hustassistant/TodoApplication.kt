package com.project.hustassistant

import android.app.Application
import com.project.hustassistant.di.AppContainer

class TodoApplication : Application() {
    // Khai báo container
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        // Khởi tạo container ngay khi app vừa chạy
        container = AppContainer(this)
    }
}