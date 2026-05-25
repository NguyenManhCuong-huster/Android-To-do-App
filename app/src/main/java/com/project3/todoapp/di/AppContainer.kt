package com.project3.todoapp.di

import android.content.Context
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.ToDoDatabase
import com.project3.todoapp.data.account.network.AccountApi
import com.project3.todoapp.data.ai.AiRepository
import com.project3.todoapp.data.ai.network.AiApi
import com.project3.todoapp.data.attachment.AttachmentRepository
import com.project3.todoapp.data.attachment.network.AttachmentApi
import com.project3.todoapp.data.email.EmailRepository
import com.project3.todoapp.data.email.network.EmailApi
import com.project3.todoapp.data.email.network.EmailRemoteDataSource
import com.project3.todoapp.data.news.DefaultNewsRepository
import com.project3.todoapp.data.news.NewsRepository
import com.project3.todoapp.data.news.network.NewsApi
import com.project3.todoapp.data.news.network.NewsNetworkDataSource
import com.project3.todoapp.data.tag.DefaultTagRepository
import com.project3.todoapp.data.tag.TagRepository
import com.project3.todoapp.data.tag.network.NetworkTagSource
import com.project3.todoapp.data.tag.network.TagApi
import com.project3.todoapp.data.task.DefaultTaskRepository
import com.project3.todoapp.data.task.TaskRepository
import com.project3.todoapp.data.task.network.TaskApi
import com.project3.todoapp.data.task.network.TaskNetworkDataSource
import com.project3.todoapp.data.tasktag.DefaultTaskTagRepository
import com.project3.todoapp.data.tasktag.TaskTagRepository
import com.project3.todoapp.data.userinfo.DefaultUserInfoRepository
import com.project3.todoapp.data.userinfo.UserInfoRepository
import com.project3.todoapp.data.userinfo.network.UserInfoApi
import com.project3.todoapp.data.userinfo.network.UserInfoRemoteDataSource
import com.project3.todoapp.network.ApiClient
import com.project3.todoapp.network.AuthApi
import com.project3.todoapp.network.NetworkManager
import com.project3.todoapp.network.ServerConfig
import com.project3.todoapp.network.TokenStore
import com.project3.todoapp.notification.PermissionManager
import com.project3.todoapp.notification.TaskNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.project3.todoapp.data.news.network.NetworkDataSource     as NewsNetwork
import com.project3.todoapp.data.tag.network.NetworkDataSource      as TagNetworkDataSource
import com.project3.todoapp.data.userinfo.network.NetworkDataSource as UserInfoNetworkDataSource

/**
 * AppContainer — DI thủ công.
 *
 * THAY ĐỔI 2026-05-23:
 *  - Thêm AttachmentRepository (sync metadata file đính kèm + download-on-demand).
 *  - EmailRepository và NewsRepository giờ depend AttachmentRepository → khởi tạo
 *    sau attachmentRepository.
 */
class AppContainer(val context: Context) {

    // ── 1. NETWORK CONFIG ──────────────────────────────
    val serverConfig: ServerConfig by lazy { ServerConfig(context) }
    val tokenStore: TokenStore by lazy { TokenStore(context) }
    private val apiClient: ApiClient by lazy { ApiClient(serverConfig, tokenStore) }

    // ── 2. RETROFIT SERVICES ───────────────────────────
    val authApi: AuthApi by lazy { apiClient.create(AuthApi::class.java) }
    val taskApi: TaskApi by lazy { apiClient.create(TaskApi::class.java) }
    val tagApi: TagApi by lazy { apiClient.create(TagApi::class.java) }
    val emailApi: EmailApi by lazy { apiClient.create(EmailApi::class.java) }
    val accountApi: AccountApi by lazy { apiClient.create(AccountApi::class.java) }
    val userInfoApi: UserInfoApi by lazy { apiClient.create(UserInfoApi::class.java) }
    val aiApi: AiApi by lazy { apiClient.create(AiApi::class.java) }
    val newsApi: NewsApi by lazy { apiClient.create(NewsApi::class.java) }
    val attachmentApi: AttachmentApi by lazy { apiClient.create(AttachmentApi::class.java) }     // ← MỚI

    // ── 3. DATABASE ────────────────────────────────────
    private val database by lazy { ToDoDatabase.getDatabase(context) }

    // ── 4. APP INFRASTRUCTURE ──────────────────────────
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val authManager: AuthManager by lazy { AuthManager(context, tokenStore, authApi, appScope) }
    val networkManager: NetworkManager by lazy { NetworkManager(context) }

    // ── 5. NETWORK DATA SOURCES ────────────────────────
    private val taskNetworkDataSource by lazy { TaskNetworkDataSource(taskApi) }
    private val tagNetworkDataSource: TagNetworkDataSource by lazy { NetworkTagSource(tagApi) }
    private val emailNetworkDataSource by lazy { EmailRemoteDataSource(emailApi) }
    private val userInfoNetworkDataSource: UserInfoNetworkDataSource by lazy {
        UserInfoRemoteDataSource(userInfoApi, authApi)
    }
    private val newsNetworkDataSource: NewsNetwork by lazy { NewsNetworkDataSource(newsApi) }

    // ── 6. REPOSITORIES ────────────────────────────────
    val taskRepository: TaskRepository by lazy {
        DefaultTaskRepository(
            networkDataSource = taskNetworkDataSource,
            localDataSource = database.taskDao(),
            dispatcher = Dispatchers.IO,
            scope = appScope,
            authManager = authManager,
            networkManager = networkManager,
        )
    }

    val tagRepository: TagRepository by lazy {
        DefaultTagRepository(
            tagDao = database.tagDao(),
            networkDataSource = tagNetworkDataSource,
            authManager = authManager,
            networkManager = networkManager,
            scope = appScope,
            dispatcher = Dispatchers.IO,
        )
    }

    val taskTagRepository: TaskTagRepository by lazy {
        DefaultTaskTagRepository(
            taskTagCrossRefDao = database.taskTagCrossRefDao(),
            dispatcher = Dispatchers.IO,
        )
    }

    /** ← MỚI. Khởi tạo trước email/news repo vì 2 cái kia depend. */
    val attachmentRepository: AttachmentRepository by lazy {
        AttachmentRepository(
            context        = context.applicationContext,
            attachmentDao  = database.attachmentDao(),
            attachmentApi  = attachmentApi,
            dispatcher     = Dispatchers.IO,
        )
    }

    val emailRepository: EmailRepository by lazy {
        EmailRepository(
            emailDao = database.emailDao(),
            networkDataSource = emailNetworkDataSource,
            authManager = authManager,
            networkManager = networkManager,
            attachmentRepository = attachmentRepository,    // ← MỚI
            dispatcher = Dispatchers.IO,
        )
    }

    val aiRepository: AiRepository by lazy { AiRepository(aiApi) }

    val userInfoRepository: UserInfoRepository by lazy {
        DefaultUserInfoRepository(
            userInfoDao = database.userInfoDao(),
            networkDataSource = userInfoNetworkDataSource,
            authManager = authManager,
            networkManager = networkManager,
            scope = appScope,
            dispatcher = Dispatchers.IO,
        )
    }

    val newsRepository: NewsRepository by lazy {
        DefaultNewsRepository(
            networkDataSource = newsNetworkDataSource,
            localDataSource = database.newsDao(),
            dispatcher = Dispatchers.IO,
            scope = appScope,
            authManager = authManager,
            networkManager = networkManager,
            attachmentRepository = attachmentRepository,    // ← MỚI
        )
    }

    // ── 7. NOTIFICATION / PERMISSION ───────────────────
    val notificationManager: TaskNotificationManager by lazy {
        TaskNotificationManager(context, appScope).apply { createNotificationChannels() }
    }
    val permissionManager: PermissionManager by lazy { PermissionManager(context) }
}
