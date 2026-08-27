package com.tomatodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tomatodo.data.model.KnowledgeCard
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeCardDao {
    @Query("SELECT * FROM knowledge_cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<KnowledgeCard>>

    @Query("SELECT * FROM knowledge_cards WHERE nextReviewAt <= :now ORDER BY nextReviewAt")
    fun observeDue(now: Long): Flow<List<KnowledgeCard>>

    @Query("SELECT * FROM knowledge_cards WHERE id = :id")
    suspend fun getById(id: Long): KnowledgeCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: KnowledgeCard): Long

    @Query("DELETE FROM knowledge_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE knowledge_cards SET masteryLevel = :mastery, reviewCount = :count, nextReviewAt = :nextReviewAt, lastReviewedAt = :now WHERE id = :id")
    suspend fun updateReview(id: Long, mastery: Int, count: Int, nextReviewAt: Long, now: Long)

    @Query("SELECT * FROM knowledge_cards")
    suspend fun getAll(): List<KnowledgeCard>

    @Insert
    suspend fun insertAll(cards: List<KnowledgeCard>)

    @Query("DELETE FROM knowledge_cards")
    suspend fun deleteAll()
}
