package com.tomatodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tomatodo.data.model.KnowledgeCard
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeCardDao {
    /** 正常卡片（回收站内容过滤，KMS v1.2） */
    @Query("SELECT * FROM knowledge_cards WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<KnowledgeCard>>

    @Query("SELECT * FROM knowledge_cards WHERE nextReviewAt <= :now AND deletedAt IS NULL ORDER BY nextReviewAt")
    fun observeDue(now: Long): Flow<List<KnowledgeCard>>

    @Query("SELECT * FROM knowledge_cards WHERE id = :id")
    suspend fun getById(id: Long): KnowledgeCard?

    /** 回收站列表 */
    @Query("SELECT * FROM knowledge_cards WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<KnowledgeCard>>

    @Insert
    suspend fun insert(card: KnowledgeCard): Long

    @Update
    suspend fun update(card: KnowledgeCard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: KnowledgeCard): Long

    /** 软删除 / 恢复（deletedAt 传 null 即恢复） */
    @Query("UPDATE knowledge_cards SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Long?, updatedAt: Long)

    @Query("DELETE FROM knowledge_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE knowledge_cards SET masteryLevel = :mastery, reviewCount = :count, nextReviewAt = :nextReviewAt, lastReviewedAt = :now WHERE id = :id")
    suspend fun updateReview(id: Long, mastery: Int, count: Int, nextReviewAt: Long, now: Long)

    @Query("SELECT * FROM knowledge_cards")
    suspend fun getAll(): List<KnowledgeCard>

    /** 元数据快搜（标题 / 摘要 / 来源；正文兜底扫描在 CardRepository） */
    @Query(
        "SELECT * FROM knowledge_cards WHERE deletedAt IS NULL AND (" +
            "title LIKE '%' || :q || '%' OR excerpt LIKE '%' || :q || '%' OR source LIKE '%' || :q || '%'" +
            ") ORDER BY updatedAt DESC"
    )
    suspend fun searchMeta(q: String): List<KnowledgeCard>

    @Insert
    suspend fun insertAll(cards: List<KnowledgeCard>)

    @Query("DELETE FROM knowledge_cards")
    suspend fun deleteAll()
}
