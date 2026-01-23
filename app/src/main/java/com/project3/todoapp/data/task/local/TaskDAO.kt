package com.project3.todoapp.data.task.local

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

    // R: Read - Lấy danh sách các Task có gắn một Tag cụ thể
    @Query(
        """
        SELECT task.* FROM task 
        INNER JOIN task_tag_cross_ref ON task.id = task_tag_cross_ref.taskId 
        WHERE task_tag_cross_ref.tagId = :tagId
    """
    )
    fun getTasksForTag(tagId: String): Flow<List<LocalTask>>

    @Transaction
    @Query("SELECT * FROM task ORDER BY start ASC")
    fun observeTasksWithTags(): Flow<List<LocalTaskWithTags>>

    @Transaction
    suspend fun syncTasks(tasks: List<LocalTask>) {
        deleteAll()
        upsertAll(tasks)
    }
}