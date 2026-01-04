package com.project3.todoapp.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDAO {
    //Read all tasks
    @Query("SELECT * FROM task ORDER BY start ASC")
    fun observeAll(): Flow<List<LocalTask>>

    @Query("SELECT * FROM task")
    suspend fun getAll(): List<LocalTask>

    //Insert or Update
    @Upsert
    suspend fun upsertTask(task: LocalTask)

    @Query("UPDATE task SET isCompleted = :completed WHERE id = :taskId")
    suspend fun updateTaskCompletion(taskId: String, completed: Boolean)

    //Read task
    @Query("SELECT * FROM task WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): LocalTask?

    //Delete task
    @Query("DELETE FROM task WHERE id = :taskId")
    suspend fun deleteById(taskId: String): Int

    //Delete all tasks
    @Query("DELETE FROM task")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(tasks: List<LocalTask>)

    @Transaction
    suspend fun syncTasks(tasks: List<LocalTask>) {
        deleteAll()
        upsertAll(tasks)
    }
}