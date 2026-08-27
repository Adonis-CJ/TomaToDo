package com.tomatodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tomatodo.data.model.PomodoroSession
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroSessionDao {
    @Query("SELECT * FROM pomodoro_sessions ORDER BY startAt DESC")
    fun observeAll(): Flow<List<PomodoroSession>>

    @Insert
    suspend fun insert(session: PomodoroSession): Long

    @Query("SELECT * FROM pomodoro_sessions WHERE startAt >= :from AND startAt < :to ORDER BY startAt")
    suspend fun getInRange(from: Long, to: Long): List<PomodoroSession>
}
