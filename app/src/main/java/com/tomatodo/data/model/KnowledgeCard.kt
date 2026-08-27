package com.tomatodo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CardType { MISTAKE, KNOWLEDGE }

/** 知识卡片 / 错题（PRD §5.1；v2 增加 tags 与 updatedAt） */
@Entity(tableName = "knowledge_cards")
data class KnowledgeCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val front: String,             // 正面（问题/知识点）
    val back: String,              // 背面（答案/解析）
    val subjectId: Long? = null,
    val type: CardType = CardType.KNOWLEDGE,
    val source: String? = null,    // 来源（章节/真题/模拟卷）
    val tags: List<String> = emptyList(), // v2：自定义标签
    val masteryLevel: Int = 0,     // 0 忘记 / 1 模糊 / 2 记得
    val reviewCount: Int = 0,
    val nextReviewAt: Long,
    val lastReviewedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long = 0        // v2
)
