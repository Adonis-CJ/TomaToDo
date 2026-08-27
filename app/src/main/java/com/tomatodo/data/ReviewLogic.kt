package com.tomatodo.data

/** 艾宾浩斯遗忘曲线复习间隔（天） */
val REVIEW_INTERVALS_DAYS = listOf(1, 2, 4, 7, 15, 30)

enum class ReviewResult { FORGET, VAGUE, REMEMBER }

data class ReviewOutcome(val newReviewCount: Int, val nextReviewAt: Long)

/** 根据掌握度结果计算下次复习时间（PRD §F5） */
fun computeReviewOutcome(result: ReviewResult, reviewCount: Int, now: Long): ReviewOutcome {
    return when (result) {
        ReviewResult.REMEMBER -> {
            val c = reviewCount + 1
            val days = REVIEW_INTERVALS_DAYS[minOf(c, REVIEW_INTERVALS_DAYS.size - 1)]
            ReviewOutcome(c, now + days * 86_400_000L)
        }
        ReviewResult.VAGUE -> {
            val c = (reviewCount - 1).coerceAtLeast(0)
            val days = REVIEW_INTERVALS_DAYS[c]
            ReviewOutcome(c, now + days * 86_400_000L)
        }
        ReviewResult.FORGET -> {
            ReviewOutcome(0, now + 86_400_000L)
        }
    }
}
