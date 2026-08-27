package com.tomatodo.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 遗忘曲线复习逻辑（PRD F5 / OPTIMIZATION §6） */
class ReviewLogicTest {

    private val day = 86_400_000L

    @Test
    fun `记得 - 间隔前进一档`() {
        val now = 1_000_000L
        // 新卡（0 次）记住后：count=1 -> 间隔档位 1（2 天）
        val first = computeReviewOutcome(ReviewResult.REMEMBER, reviewCount = 0, now = now)
        assertEquals(1, first.newReviewCount)
        assertEquals(now + 2 * day, first.nextReviewAt)

        // 已复习 1 次再记住：count=2 -> 4 天
        val second = computeReviewOutcome(ReviewResult.REMEMBER, reviewCount = 1, now = now)
        assertEquals(2, second.newReviewCount)
        assertEquals(now + 4 * day, second.nextReviewAt)
    }

    @Test
    fun `记得 - 档位封顶 30 天`() {
        val now = 0L
        val outcome = computeReviewOutcome(ReviewResult.REMEMBER, reviewCount = 99, now = now)
        assertEquals(100, outcome.newReviewCount)
        assertEquals(now + 30 * day, outcome.nextReviewAt)
    }

    @Test
    fun `模糊 - 间隔回退一档`() {
        val now = 5_000_000L
        // 已到第 3 档（reviewCount=3）打模糊 -> count=2 -> 4 天
        val outcome = computeReviewOutcome(ReviewResult.VAGUE, reviewCount = 3, now = now)
        assertEquals(2, outcome.newReviewCount)
        assertEquals(now + 4 * day, outcome.nextReviewAt)
    }

    @Test
    fun `模糊 - 档位不为负`() {
        val now = 0L
        val outcome = computeReviewOutcome(ReviewResult.VAGUE, reviewCount = 0, now = now)
        assertEquals(0, outcome.newReviewCount)
        assertEquals(now + 1 * day, outcome.nextReviewAt)
    }

    @Test
    fun `忘记 - 重置回明天`() {
        val now = 2_000_000L
        val outcome = computeReviewOutcome(ReviewResult.FORGET, reviewCount = 5, now = now)
        assertEquals(0, outcome.newReviewCount)
        assertEquals(now + 1 * day, outcome.nextReviewAt)
    }
}
