package com.tomatodo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 卡片图片（一卡多图，PRD §5.1） */
@Entity(
    tableName = "card_images",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeCard::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId")]
)
data class CardImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val filePath: String,          // 内部存储相对路径
    val sortOrder: Int = 0,
    val createdAt: Long
)
