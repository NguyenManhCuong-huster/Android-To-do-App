package com.project3.todoapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.project3.todoapp.data.attachment.local.AttachmentDAO
import com.project3.todoapp.data.attachment.local.LocalAttachment
import com.project3.todoapp.data.email.local.EmailDAO
import com.project3.todoapp.data.email.local.LocalEmail
import com.project3.todoapp.data.news.local.LocalNews
import com.project3.todoapp.data.news.local.NewsDAO
import com.project3.todoapp.data.tag.local.LocalTag
import com.project3.todoapp.data.tag.local.TagDAO
import com.project3.todoapp.data.task.local.LocalTask
import com.project3.todoapp.data.task.local.TaskDAO
import com.project3.todoapp.data.tasktag.local.LocalTaskTagCrossRef
import com.project3.todoapp.data.tasktag.local.TaskTagCrossRefDAO
import com.project3.todoapp.data.userinfo.local.LocalUserInfo
import com.project3.todoapp.data.userinfo.local.UserInfoDAO

/**
 * Bump version 12 → 13: thêm LocalAttachment entity (cache metadata cho
 * file đính kèm của email và news).
 *
 * Schema thay đổi: 1 bảng mới `attachment`, không ảnh hưởng bảng cũ.
 * fallbackToDestructiveMigration giữ nguyên — wipe sẽ trigger re-sync,
 * an toàn vì attachment metadata sync 1 chiều từ server.
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
    ],
    version = 13,
    exportSchema = false,
)
abstract class ToDoDatabase : RoomDatabase() {

    abstract fun taskDao():             TaskDAO
    abstract fun tagDao():              TagDAO
    abstract fun taskTagCrossRefDao():  TaskTagCrossRefDAO
    abstract fun emailDao():            EmailDAO
    abstract fun userInfoDao():         UserInfoDAO
    abstract fun newsDao():             NewsDAO
    abstract fun attachmentDao():       AttachmentDAO

    companion object {
        @Volatile
        private var INSTANCE: ToDoDatabase? = null

        fun getDatabase(context: Context): ToDoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ToDoDatabase::class.java,
                    "task_database",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
