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

    @Insert
    suspend fun insertAll(images: List<CardImage>)

    @Query("DELETE FROM card_images WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM card_images WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: Long)

    @Query("SELECT * FROM card_images")
    suspend fun getAll(): List<CardImage>

    @Query("DELETE FROM card_images")
    suspend fun deleteAll()
}
