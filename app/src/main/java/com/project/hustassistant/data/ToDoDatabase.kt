package com.project.hustassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.project.hustassistant.data.attachment.local.AttachmentDAO
import com.project.hustassistant.data.attachment.local.LocalAttachment
import com.project.hustassistant.data.email.local.EmailDAO
import com.project.hustassistant.data.email.local.LocalEmail
import com.project.hustassistant.data.grade.local.GradeDAO
import com.project.hustassistant.data.grade.local.LocalGrade
import com.project.hustassistant.data.news.local.LocalNews
import com.project.hustassistant.data.news.local.LocalNewsRecommendation
import com.project.hustassistant.data.news.local.NewsDAO
import com.project.hustassistant.data.news.local.NewsRecommendationDAO
import com.project.hustassistant.data.tag.local.LocalTag
import com.project.hustassistant.data.tag.local.TagDAO
import com.project.hustassistant.data.task.local.LocalTask
import com.project.hustassistant.data.task.local.TaskDAO
import com.project.hustassistant.data.tasktag.local.LocalTaskTagCrossRef
import com.project.hustassistant.data.tasktag.local.TaskTagCrossRefDAO
import com.project.hustassistant.data.userinfo.local.LocalUserInfo
import com.project.hustassistant.data.userinfo.local.UserInfoDAO
import com.project.hustassistant.data.ai.local.ChatHistoryDao
import com.project.hustassistant.data.ai.local.LocalChatMessage
import com.project.hustassistant.data.ai.local.LocalChatSession
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Bump version 16 → 17: thêm cột isRead vào bảng email.
 */
@Database(
    entities = [
        LocalTask::class,
        LocalTag::class,
        LocalTaskTagCrossRef::class,
        LocalEmail::class,
        LocalUserInfo::class,
        LocalNews::class,
        LocalAttachment::class,
        LocalNewsRecommendation::class,
        LocalChatSession::class,
        LocalChatMessage::class,
        LocalGrade::class,
    ],
    version = 17,
    exportSchema = false,
)
abstract class ToDoDatabase : RoomDatabase() {

    abstract fun taskDao():               TaskDAO
    abstract fun tagDao():                TagDAO
    abstract fun taskTagCrossRefDao():    TaskTagCrossRefDAO
    abstract fun emailDao():              EmailDAO
    abstract fun userInfoDao():           UserInfoDAO
    abstract fun newsDao():               NewsDAO
    abstract fun attachmentDao():         AttachmentDAO
    abstract fun newsRecommendationDao(): NewsRecommendationDAO
    abstract fun chatHistoryDao():        ChatHistoryDao
    abstract fun gradeDao():              GradeDAO                             // ← MỚI

    companion object {
        @Volatile
        private var INSTANCE: ToDoDatabase? = null

        /** MỚI 2026-06: tạo bảng chat_sessions + chat_messages (không wipe dữ liệu user). */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                        "`id` TEXT NOT NULL, `contextType` TEXT NOT NULL, " +
                        "`contextKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `seq` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                        "`referencesJson` TEXT, `attachmentsJson` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` " +
                        "ON `chat_messages` (`sessionId`)",
                )
            }
        }

        /** MỚI 2026-06: tạo bảng grade (điểm học phần). Không đụng dữ liệu cũ. */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `grade` (" +
                        "`id` TEXT NOT NULL, `semester` TEXT NOT NULL, " +
                        "`courseCode` TEXT NOT NULL, `courseName` TEXT NOT NULL, " +
                        "`courseNameEn` TEXT NOT NULL, `credits` INTEGER NOT NULL, " +
                        "`letterGrade` TEXT NOT NULL, `modTime` INTEGER NOT NULL, " +
                        "`isDeleted` INTEGER NOT NULL, `isDirty` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `email` ADD COLUMN `isRead` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): ToDoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ToDoDatabase::class.java,
                    "task_database",
                )
                    .addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
