package com.project3.todoapp.di

import android.content.Context
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.ToDoDatabase
import com.project3.todoapp.data.tag.DefaultTagRepository
import com.project3.todoapp.data.tag.TagRepository
import com.project3.todoapp.data.tag.local.TagDAO
import com.project3.todoapp.data.task.DefaultTaskRepository
import com.project3.todoapp.data.task.TaskRepository
import com.project3.todoapp.data.task.local.TaskDAO
import com.project3.todoapp.data.task.network.GoogleDriveDatabase
import com.project3.todoapp.data.task.network.NetworkDataSource
import com.project3.todoapp.data.tasktag.DefaultTaskTagRepository
import com.project3.todoapp.data.tasktag.TaskTagRepository
import com.project3.todoapp.data.tasktag.local.TaskTagCrossRefDAO
import com.project3.todoapp.network.NetworkManager
import com.project3.todoapp.notification.PermissionManager
import com.project3.todoapp.notification.TaskNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(val context: Context) {

    // --- 1. DATABASE ---

    private val database: ToDoDatabase by lazy {
        ToDoDatabase.Companion.getDatabase(context)
    }

    // 1.1. Tasks Database
    // 1.1.a. Tasks Local Source
    val taskLocalDataSource: TaskDAO by lazy {
        database.taskDao()
    }

    // 1.1.b. Tasks Network Source
    val networkDataSource: NetworkDataSource by lazy {
        GoogleDriveDatabase(context, authManager)
    }

    // 1.2. Tag Database
    // 1.2.a. Tag Local Source
    val tagLocalSource: TagDAO by lazy {
        database.tagDao()
    }

    // 1.2.b. Tag Network Source


    // 1.3. Tasks-Tags Database

    val taskTagLocalSource: TaskTagCrossRefDAO by lazy {
        database.taskTagCrossRefDao()
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

    val tagRepository: TagRepository by lazy {
        DefaultTagRepository(
            tagDao = tagLocalSource,
            dispatcher = Dispatchers.IO
        )
    }

    val taskTagRepository: TaskTagRepository by lazy {
        DefaultTaskTagRepository(
            taskTagCrossRefDao = taskTagLocalSource,
            dispatcher = Dispatchers.IO
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