package com.tomatodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tomatodo.data.model.Subject
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subject: Subject): Long

    @Insert
    suspend fun insertAll(subjects: List<Subject>)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM subjects ORDER BY sortOrder, id")
    suspend fun getAll(): List<Subject>

    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}
