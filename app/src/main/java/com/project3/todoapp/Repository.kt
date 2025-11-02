package com.project3.todoapp

import android.content.Context
import androidx.room.Room
import com.project3.todoapp.data.DefaultTaskRepository
import com.project3.todoapp.data.local.ToDoDatabase

object Repository {
    @Volatile
    private var database: ToDoDatabase? = null

    fun provideRepository(context: Context): DefaultTaskRepository {
        val db = database ?: createDatabase(context)
        return DefaultTaskRepository(db.taskDao())
    }

    private fun createDatabase(context: Context): ToDoDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            ToDoDatabase::class.java,
            "todo_database"
        )
            .fallbackToDestructiveMigration()
            .build().also { database = it }
    }
}