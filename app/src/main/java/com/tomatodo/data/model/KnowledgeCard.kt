package com.tomatodo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CardType { MISTAKE, KNOWLEDGE }

/**
 * 知识卡片（KMS v1.2）：正文以独立 Markdown 文档存储于 `cards/{id}/note.md`（mdPath），
 * Room 仅作索引与元数据。title/excerpt/wordCount 为派生字段，由 CardRepository 写入时统一计算。
 * deletedAt 非空表示位于回收站（软删除，30 天后彻底清除）。
 */
@Entity(tableName = "knowledge_cards")
data class KnowledgeCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "无标题",
    val excerpt: String = "",      // 去语法纯文本摘要（列表展示 / 检索，不读文件）
    val wordCount: Int = 0,
    val mdPath: String = "",       // 相对 filesDir，如 cards/3/note.md
    val deletedAt: Long? = null,   // 进入回收站的时间戳；null = 正常
    val subjectId: Long? = null,
    val type: CardType = CardType.KNOWLEDGE,
    val source: String? = null,    // 来源（章节/真题/模拟卷）
    val masteryLevel: Int = 0,     // 0 忘记 / 1 模糊 / 2 记得
    val reviewCount: Int = 0,
    val nextReviewAt: Long,
    val lastReviewedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
