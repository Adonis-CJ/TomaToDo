package com.tomatodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 图片尺寸令牌与渲染预处理（v1.4 §1） */
class CardTextUtilsTest {

    // ---- splitImageSize ----

    @Test
    fun `拆分 - 无令牌原样返回`() {
        val (path, pct) = CardTextUtils.splitImageSize("assets/x.jpg")
        assertEquals("assets/x.jpg", path)
        assertNull(pct)
    }

    @Test
    fun `拆分 - 合法令牌`() {
        val (path, pct) = CardTextUtils.splitImageSize("assets/x.jpg#w=50")
        assertEquals("assets/x.jpg", path)
        assertEquals(50, pct)

        val (_, full) = CardTextUtils.splitImageSize("assets/x.jpg#w=100")
        assertEquals(100, full)
    }

    @Test
    fun `拆分 - 越界与非法令牌视为路径`() {
        listOf("a.jpg#w=0", "a.jpg#w=101", "a.jpg#50", "a.jpg#w=x", "a.jpg#anchor").forEach {
            val (path, pct) = CardTextUtils.splitImageSize(it)
            assertEquals(it, path)
            assertNull(pct)
        }
    }

    // ---- withImageSize ----

    @Test
    fun `写入 - 设置 替换 移除`() {
        assertEquals("a.jpg#w=30", CardTextUtils.withImageSize("a.jpg", 30))
        assertEquals("a.jpg#w=30", CardTextUtils.withImageSize("a.jpg#w=80", 30))
        assertEquals("a.jpg", CardTextUtils.withImageSize("a.jpg#w=80", null))
        assertEquals("a.jpg", CardTextUtils.withImageSize("a.jpg#w=80", 100))
        assertEquals("a.jpg", CardTextUtils.withImageSize("a.jpg", 0))
        assertEquals("a.jpg", CardTextUtils.withImageSize("a.jpg", 150))
    }

    // ---- imageTargets ----

    @Test
    fun `抽取 - 按序返回且保留令牌`() {
        val md = "# T\n\n![a](assets/1.jpg)\n\ntext ![b](assets/2.jpg#w=50)\n"
        assertEquals(
            listOf("assets/1.jpg", "assets/2.jpg#w=50"),
            CardTextUtils.imageTargets(md)
        )
    }

    @Test
    fun `抽取 - 无图片返回空`() {
        assertTrue(CardTextUtils.imageTargets("# 只有文字").isEmpty())
    }

    // ---- prepareForRender 与令牌的协作 ----

    @Test
    fun `渲染预处理 - 绝对路径化并保留令牌`() {
        val base = File("/data/cards/1")
        val out = CardTextUtils.prepareForRender("![a](assets/x.jpg#w=50)", base)
        val expected = File(base, "assets/x.jpg").absolutePath
        assertTrue(out.contains(expected + "#w=50"))
    }

    @Test
    fun `渲染预处理 - 无令牌路径不引入 fragment`() {
        val base = File("/data/cards/1")
        val out = CardTextUtils.prepareForRender("![a](assets/x.jpg)", base)
        assertTrue(out.contains(File(base, "assets/x.jpg").absolutePath))
        assertTrue(!out.contains("#"))
    }

    // ---- LaTeX 转义契约（v1.6 §1：行内解析已开启，依赖单美元→双美元预处理）----

    @Test
    fun `渲染预处理 - 行内单美元转双美元`() {
        // JLatexMath 行内处理器只认 `$$...$$`，单个 `$x$` 必须被抬升，否则以源码呈现
        val out = CardTextUtils.prepareForRender("质能方程 \$E=mc^2\$ 成立", null)
        assertTrue(out.contains("\$\$E=mc^2\$\$"))
    }

    @Test
    fun `渲染预处理 - 已是双美元的块级不被叠加`() {
        val out = CardTextUtils.prepareForRender("\$\$\nE=mc^2\n\$\$", null)
        assertTrue(!out.contains("\$\$\$\$"))
        assertTrue(out.contains("\$\$"))
    }

    @Test
    fun `渲染预处理 - 围栏代码块内美元不转义`() {
        val out = CardTextUtils.prepareForRender("```\n\$a\$\n```", null)
        assertTrue(out.contains("\$a\$"))
        assertTrue(!out.contains("\$\$a\$\$"))
    }

    @Test
    fun `渲染预处理 - 反斜杠转义的美元不视为公式`() {
        val out = CardTextUtils.prepareForRender("价格 \\\$5 起", null)
        assertTrue(out.contains("\\\$5"))
    }
}
