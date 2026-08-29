package com.tomatodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tomatodo.data.model.CardTag
import com.tomatodo.data.model.Tag
import kotlinx.coroutines.flow.Flow

/** 标签计数（仅统计未删除卡片） */
data class TagWithCount(val tagId: Long, val name: String, val cardCount: Int)

@Dao
interface TagDao {
    @Query(
        "SELECT t.id AS tagId, t.name AS name, COUNT(kc.id) AS cardCount " +
            "FROM tags t LEFT JOIN card_tags ct ON ct.tagId = t.id " +
            "LEFT JOIN knowledge_cards kc ON kc.id = ct.cardId AND kc.deletedAt IS NULL " +
            "GROUP BY t.id ORDER BY cardCount DESC, t.name"
    )
    fun observeAllWithCount(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Tag?

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAll(): List<Tag>

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<Tag>>

    @Insert
    suspend fun insert(tag: Tag): Long

    @Insert
    suspend fun insertAll(tags: List<Tag>)

    @Update
    suspend fun update(tag: Tag)

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    // ---- 卡片-标签关联 ----

    @Query("SELECT t.* FROM tags t INNER JOIN card_tags ct ON ct.tagId = t.id WHERE ct.cardId = :cardId ORDER BY t.name")
    suspend fun tagsForCard(cardId: Long): List<Tag>

    @Query("SELECT t.name FROM tags t INNER JOIN card_tags ct ON ct.tagId = t.id WHERE ct.cardId = :cardId ORDER BY t.name")
    suspend fun tagNamesForCard(cardId: Long): List<String>

    @Query("SELECT cardId FROM card_tags WHERE tagId = :tagId")
    suspend fun cardIdsForTag(tagId: Long): List<Long>

    @Query("SELECT * FROM card_tags")
    fun observeAllLinks(): Flow<List<CardTag>>

    @Query("SELECT * FROM card_tags")
    suspend fun getAllLinks(): List<CardTag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCardTag(link: CardTag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCardTags(links: List<CardTag>)

    @Query("DELETE FROM card_tags WHERE cardId = :cardId")
    suspend fun clearForCard(cardId: Long)

    @Query("DELETE FROM card_tags")
    suspend fun clearAllCardTags()

    /** 标签合并：把 fromTag 的关联全部挂到 toTag（已存在的关联忽略），用于重命名冲突时合并 */
    @Query("INSERT OR IGNORE INTO card_tags (cardId, tagId) SELECT cardId, :toTagId FROM card_tags WHERE tagId = :fromTagId")
    suspend fun reassignCardTags(fromTagId: Long, toTagId: Long)
}
