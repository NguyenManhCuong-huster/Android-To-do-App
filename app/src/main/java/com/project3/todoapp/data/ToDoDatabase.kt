package com.project3.todoapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.project3.todoapp.data.tag.local.LocalTag
import com.project3.todoapp.data.tag.local.TagDAO
import com.project3.todoapp.data.task.local.LocalTask
import com.project3.todoapp.data.task.local.TaskDAO
import com.project3.todoapp.data.tasktag.local.LocalTaskTagCrossRef
import com.project3.todoapp.data.tasktag.local.TaskTagCrossRefDAO

@Database(
    entities = [LocalTask::class, LocalTag::class, LocalTaskTagCrossRef::class],
    version = 5,
    exportSchema = false
)
abstract class ToDoDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDAO
    abstract fun tagDao(): TagDAO
    abstract fun taskTagCrossRefDao(): TaskTagCrossRefDAO

    companion object {
        @Volatile
        private var INSTANTCE: ToDoDatabase? = null

        fun getDatabase(context: Context): ToDoDatabase {
            return INSTANTCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ToDoDatabase::class.java,
                    "task_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANTCE = instance
                return instance
            }
        }
    }
}