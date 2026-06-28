package com.project.hustassistant.di

import android.content.Context
import com.project.hustassistant.authentication.AuthManager
import com.project.hustassistant.data.ToDoDatabase
import com.project.hustassistant.data.account.network.AccountApi
import com.project.hustassistant.data.ai.AiRepository
import com.project.hustassistant.data.ai.ChatHistoryRepository
import com.project.hustassistant.data.ai.network.AiApi
import com.project.hustassistant.data.attachment.AttachmentRepository
import com.project.hustassistant.data.attachment.network.AttachmentApi
import com.project.hustassistant.data.email.EmailRepository
import com.project.hustassistant.data.email.network.EmailApi
import com.project.hustassistant.data.email.network.EmailRemoteDataSource
import com.project.hustassistant.data.grade.DefaultGradeRepository
import com.project.hustassistant.data.grade.GradeRepository
import com.project.hustassistant.data.grade.network.GradeApi
import com.project.hustassistant.data.grade.network.NetworkGradeSource
import com.project.hustassistant.data.news.DefaultNewsRepository
import com.project.hustassistant.data.news.NewsRepository
import com.project.hustassistant.data.news.network.NewsApi
import com.project.hustassistant.data.news.network.NewsNetworkDataSource
import com.project.hustassistant.data.tag.DefaultTagRepository
import com.project.hustassistant.data.tag.TagRepository
import com.project.hustassistant.data.tag.network.NetworkTagSource
import com.project.hustassistant.data.tag.network.TagApi
import com.project.hustassistant.data.task.DefaultTaskRepository
import com.project.hustassistant.data.task.TaskRepository
import com.project.hustassistant.data.task.network.TaskApi
import com.project.hustassistant.data.task.network.TaskNetworkDataSource
import com.project.hustassistant.data.tasktag.DefaultTaskTagRepository
import com.project.hustassistant.data.tasktag.TaskTagRepository
import com.project.hustassistant.data.userinfo.DefaultUserInfoRepository
import com.project.hustassistant.data.userinfo.UserInfoRepository
import com.project.hustassistant.data.userinfo.network.UserInfoApi
import com.project.hustassistant.data.userinfo.network.UserInfoRemoteDataSource
import com.project.hustassistant.data.sync.SyncManager
import com.project.hustassistant.network.ApiClient
import com.project.hustassistant.network.AuthApi
import com.project.hustassistant.network.NetworkManager
import com.project.hustassistant.network.ServerConfig
import com.project.hustassistant.network.SyncApi
import com.project.hustassistant.network.TokenStore
import com.project.hustassistant.notification.PermissionManager
import com.project.hustassistant.notification.TaskNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.project.hustassistant.data.grade.network.NetworkDataSource    as GradeNetworkDataSource
import com.project.hustassistant.data.news.network.NetworkDataSource     as NewsNetwork
import com.project.hustassistant.data.tag.network.NetworkDataSource      as TagNetworkDataSource
import com.project.hustassistant.data.userinfo.network.NetworkDataSource as UserInfoNetworkDataSource

/**
 * AppContainer — DI thủ công.
 *
 * THAY ĐỔI 2026-06: thêm GradeRepository (kết quả học tập) — cùng pattern Tag.
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
    val gradeApi: GradeApi by lazy { apiClient.create(GradeApi::class.java) }              // ← MỚI
    val syncApi: SyncApi by lazy { apiClient.create(SyncApi::class.java) }
    val emailApi: EmailApi by lazy { apiClient.create(EmailApi::class.java) }
    val accountApi: AccountApi by lazy { apiClient.create(AccountApi::class.java) }
    val userInfoApi: UserInfoApi by lazy { apiClient.create(UserInfoApi::class.java) }
    val aiApi: AiApi by lazy { apiClient.create(AiApi::class.java) }
    val newsApi: NewsApi by lazy { apiClient.create(NewsApi::class.java) }
    val attachmentApi: AttachmentApi by lazy { apiClient.create(AttachmentApi::class.java) }

    // ── 3. DATABASE ────────────────────────────────────
    private val database by lazy { ToDoDatabase.getDatabase(context) }

    // ── 4. APP INFRASTRUCTURE ──────────────────────────
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val authManager: AuthManager by lazy { AuthManager(context, tokenStore, authApi, appScope) }
    val networkManager: NetworkManager by lazy { NetworkManager(context) }

    // ── 5. NETWORK DATA SOURCES ────────────────────────
    private val taskNetworkDataSource by lazy { TaskNetworkDataSource(taskApi) }
    private val tagNetworkDataSource: TagNetworkDataSource by lazy { NetworkTagSource(tagApi) }
    private val gradeNetworkDataSource: GradeNetworkDataSource by lazy { NetworkGradeSource(gradeApi) } // ← MỚI
    private val emailNetworkDataSource by lazy { EmailRemoteDataSource(emailApi) }
    private val userInfoNetworkDataSource: UserInfoNetworkDataSource by lazy {
        UserInfoRemoteDataSource(userInfoApi, authApi)
    }
    private val newsNetworkDataSource: NewsNetwork by lazy { NewsNetworkDataSource(newsApi) }

    // ── 5b. SYNC (batch /api/sync, điều phối tập trung cho Task+Tag+cross-ref) ──
    val syncManager: SyncManager by lazy {
        SyncManager(
            syncApi        = syncApi,
            taskDao        = database.taskDao(),
            tagDao         = database.tagDao(),
            crossRefDao    = database.taskTagCrossRefDao(),
            authManager    = authManager,
            networkManager = networkManager,
            notificationManager = notificationManager,
            context        = context,
            dispatcher     = Dispatchers.IO,
        )
    }

    // ── 6. REPOSITORIES ────────────────────────────────
    val taskRepository: TaskRepository by lazy {
        DefaultTaskRepository(
            localDataSource = database.taskDao(),
            syncManager = syncManager,
            dispatcher = Dispatchers.IO,
            scope = appScope,
            authManager = authManager,
            networkManager = networkManager,
        )
    }

    val tagRepository: TagRepository by lazy {
        DefaultTagRepository(
            tagDao = database.tagDao(),
            syncManager = syncManager,
            authManager = authManager,
            networkManager = networkManager,
            scope = appScope,
            dispatcher = Dispatchers.IO,
        )
    }

    val gradeRepository: GradeRepository by lazy {                                          // ← MỚI
        DefaultGradeRepository(
            gradeDao = database.gradeDao(),
            networkDataSource = gradeNetworkDataSource,
            authManager = authManager,
            networkManager = networkManager,
            scope = appScope,
            dispatcher = Dispatchers.IO,
        )
    }

    val taskTagRepository: TaskTagRepository by lazy {
        DefaultTaskTagRepository(
            taskTagCrossRefDao = database.taskTagCrossRefDao(),
            taskDao = database.taskDao(),
            dispatcher = Dispatchers.IO,
        )
    }

    /** Khởi tạo trước email/news repo vì 2 cái kia depend. */
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
            attachmentRepository = attachmentRepository,
            dispatcher = Dispatchers.IO,
        )
    }

    val aiRepository: AiRepository by lazy {
        AiRepository(
            aiApi           = aiApi,
            contentResolver = context.applicationContext.contentResolver,
            streamingClient = apiClient.streamingHttpClient,
            serverConfig    = serverConfig,
        )
    }

    val chatHistoryRepository: ChatHistoryRepository by lazy {
        ChatHistoryRepository(
            dao        = database.chatHistoryDao(),
            dispatcher = Dispatchers.IO,
        )
    }

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
            networkDataSource    = newsNetworkDataSource,
            localDataSource      = database.newsDao(),
            recommendationDao    = database.newsRecommendationDao(),
            dispatcher           = Dispatchers.IO,
            scope                = appScope,
            authManager          = authManager,
            networkManager       = networkManager,
            attachmentRepository = attachmentRepository,
        )
    }

    // ── 7. NOTIFICATION / PERMISSION ───────────────────
    val notificationManager: TaskNotificationManager by lazy {
        TaskNotificationManager(context, appScope).apply { createNotificationChannels() }
    }
    val permissionManager: PermissionManager by lazy { PermissionManager(context) }
}
