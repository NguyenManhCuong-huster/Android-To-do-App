package com.project3.todoapp.di

import android.content.Context
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.DefaultTaskRepository
import com.project3.todoapp.data.TaskRepository
import com.project3.todoapp.data.local.TaskDAO
import com.project3.todoapp.data.local.ToDoDatabase
import com.project3.todoapp.data.network.GoogleDriveDatabase
import com.project3.todoapp.data.network.NetworkDataSource
import com.project3.todoapp.network.NetworkManager
import com.project3.todoapp.notification.PermissionManager
import com.project3.todoapp.notification.TaskNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(val context: Context) {

    // --- 1. DATABASE ---
    // a. Local Source
    private val database: ToDoDatabase by lazy {
        ToDoDatabase.Companion.getDatabase(context)
    }

    val taskLocalDataSource: TaskDAO by lazy {
        database.taskDao()
    }

    // b. Network Source
    val networkDataSource: NetworkDataSource by lazy {
        GoogleDriveDatabase(context, authManager)
    }

    // c. Scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // d. Google Drive authentication
    val authManager: AuthManager by lazy {
        AuthManager(context)
    }

    // e. Network check
    val networkManager: NetworkManager by lazy {
        NetworkManager(context)
    }

    val taskRepository: TaskRepository by lazy {
        DefaultTaskRepository(
            networkDataSource = networkDataSource,
            localDataSource = taskLocalDataSource,
            dispatcher = Dispatchers.IO,
            scope = applicationScope,
            authManager = authManager,
            networkManager = networkManager
        )
    }

    // --- 2. NOTIFICATION ---
    val notificationManager: TaskNotificationManager by lazy {
        TaskNotificationManager(context, applicationScope).apply {
            createNotificationChannels()
        }
    }

    // --- 3. PERMISSION
    val permissionManager: PermissionManager by lazy {
        PermissionManager(context)
    }
}