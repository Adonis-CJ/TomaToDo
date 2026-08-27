package com.tomatodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tomatodo.data.model.CardImage
import kotlinx.coroutines.flow.Flow

@Dao
interface CardImageDao {
    @Query("SELECT * FROM card_images WHERE cardId = :cardId ORDER BY sortOrder")
    fun observeForCard(cardId: Long): Flow<List<CardImage>>

    @Query("SELECT * FROM card_images ORDER BY sortOrder")
    fun observeAllImages(): Flow<List<CardImage>>

    @Insert
    suspend fun insert(image: CardImage): Long

    @Insert
    suspend fun insertAll(images: List<CardImage>)

    @Query("DELETE FROM card_images WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM card_images WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: Long)

    @Query("UPDATE card_images SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("SELECT * FROM card_images")
    suspend fun getAll(): List<CardImage>

    @Query("DELETE FROM card_images")
    suspend fun deleteAll()
}
