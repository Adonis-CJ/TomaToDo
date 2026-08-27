package com.tomatodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tomatodo.data.model.ReviewRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewRecordDao {
    @Query("SELECT * FROM review_records WHERE cardId = :cardId ORDER BY reviewedAt DESC")
    fun observeForCard(cardId: Long): Flow<List<ReviewRecord>>

    @Query("SELECT * FROM review_records WHERE reviewedAt >= :from AND reviewedAt < :to")
    suspend fun getInRange(from: Long, to: Long): List<ReviewRecord>

    @Insert
    suspend fun insert(record: ReviewRecord): Long

    @Query("SELECT * FROM review_records")
    suspend fun getAll(): List<ReviewRecord>

    @Insert
    suspend fun insertAll(records: List<ReviewRecord>)

    @Query("DELETE FROM review_records")
    suspend fun deleteAll()
}
