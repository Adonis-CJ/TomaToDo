package com.tomatodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY startTime")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: Task): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET status = :status, isCompleted = :isCompleted, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus, isCompleted: Boolean, updatedAt: Long)

    @Query("UPDATE tasks SET pomodoroCount = pomodoroCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementPomodoro(id: Long, updatedAt: Long)

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<Task>

    @Insert
    suspend fun insertAll(tasks: List<Task>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
