package com.tomatodo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 标签（KMS v1.2）：知识卡片多对多归类，替代 v2 的 JSON 标签列 */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

/** 卡片-标签关联 */
@Entity(
    tableName = "card_tags",
    primaryKeys = ["cardId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeCard::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class CardTag(val cardId: Long, val tagId: Long)
