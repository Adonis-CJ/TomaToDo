package com.tomatodo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 复习历史记录（v2 新表）：每次评价一条，支撑详情页复习轨迹与统计 */
@Entity(
    tableName = "review_records",
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
data class ReviewRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val result: String,            // FORGET / VAGUE / REMEMBER
    val reviewedAt: Long
)
