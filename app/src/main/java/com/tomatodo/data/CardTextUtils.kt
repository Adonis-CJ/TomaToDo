package com.tomatodo.data

import java.io.File

/**
 * 卡片 Markdown 文本处理（KMS v1.2，纯函数便于单测）：
 * 标题/摘要派生、问/答面拆分、渲染预处理（图片路径与单美元公式）、公式快捷片。
 */
object CardTextUtils {

    const val NOTE_FILE_NAME = "note.md"
    const val ASSETS_DIR_NAME = "assets"
    const val TRASH_RETENTION_DAYS = 30L
    const val TITLE_MAX_LEN = 50
    const val EXCERPT_MAX_LEN = 90

    // ---- 图片尺寸令牌（v1.4）：`![](assets/x.jpg#50)` = 50% 画布宽，等比高 ----

    /** 工具栏预设宽度百分比（25/50/75/100，100 = 满宽） */
    val SIZE_PRESETS: List<Int> = listOf(25, 50, 75, 100)

    private val WIDTH_TOKEN = Regex("^w=(\\d{1,3})$")

    /** 目标引用 → (干净路径, 宽度百分比)。仅 `#w=1..100` 识别为尺寸令牌，其余视为路径 */
    fun splitImageSize(target: String): Pair<String, Int?> {
        val idx = target.lastIndexOf('#')
        if (idx < 0) return target to null
        val pct = WIDTH_TOKEN.matchEntire(target.substring(idx + 1))
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 1..100 }
            ?: return target to null
        return target.substring(0, idx) to pct
    }

    /** 写入/替换/移除尺寸令牌；percent 为 null 或 100 时恢复无令牌满宽 */
    fun withImageSize(target: String, percent: Int?): String {
        val (path, _) = splitImageSize(target)
        val pct = percent?.takeIf { it in 1..99 } ?: return path
        return "$path#w=$pct"
    }

    /** 按文档顺序抽取全部图片引用目标（含尺寸令牌，由调用方按需剥离） */
    fun imageTargets(md: String): List<String> =
        Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)").findAll(md).map { it.groupValues[1] }.toList()

    /** LaTeX 常用结构快捷片（插入为行内公式，光标停在首个占位） */
    val FORMULA_SNIPPETS: List<Pair<String, String>> = listOf(
        "分数" to "\\frac{a}{b}",
        "根号" to "\\sqrt{x}",
        "上标" to "x^{2}",
        "下标" to "x_{i}",
        "求和" to "\\sum_{i=1}^{n}",
        "积分" to "\\int_{a}^{b}",
        "极限" to "\\lim_{n \\to \\infty}",
        "矩阵" to "\\begin{bmatrix} a & b \\\\ c & d \\end{bmatrix}",
        "多行对齐" to "\\begin{aligned} a &= b \\\\ c &= d \\end{aligned}",
        "常用集合" to "\\mathbb{R}"
    )

    /** 去 Markdown 语法后的纯文本（摘要与字数统计用） */
    fun stripMarkdown(md: String): String {
        var s = md
        s = s.replace(Regex("```[\\s\\S]*?```"), " ")
        s = s.replace(Regex("~~~[\\s\\S]*?~~~"), " ")
        s = s.replace(Regex("`([^`]*)`")) { m -> m.groupValues[1] }
        s = s.replace(Regex("!\\[([^\\]]*)\\]\\(([^)]*)\\)")) { m ->
            m.groupValues[1].ifBlank { "[图]" }
        }
        s = s.replace(Regex("\\[\\[([^\\]]*)\\]\\]")) { m -> m.groupValues[1] }
        s = s.replace(Regex("\\[([^\\]]*)\\]\\(([^)]*)\\)")) { m -> m.groupValues[1] }
        s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("(\\*\\*|__|~~)"), "")
        s = s.replace(Regex("(?<!\\*)\\*(?!\\*)|(?<!_)_(?!_)"), "")
        s = s.replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\s*[-*+]\\s+\\[[ xX]]\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\|[^\\n]*\\|", RegexOption.MULTILINE), " ")
        s = s.replace(Regex("^\\s*([-*_])\\s*(\\1\\s*){2,}$", RegexOption.MULTILINE), " ")
        s = s.replace(Regex("\\$+"), "")
        s = s.replace(Regex("\\s+"), " ")
        return s.trim()
    }

    /** 标题 = 首个 `# ` 一级标题，缺省取首行非空文本，截断 50 字符 */
    fun deriveTitle(md: String): String {
        val h1 = Regex("^#\\s+(.+)$", RegexOption.MULTILINE).find(md)?.groupValues?.get(1)
        val raw = (h1 ?: firstNonEmptyLine(md))?.let { stripMarkdown(it) }.orEmpty()
        val t = raw.trim().ifBlank { "无标题" }
        return t.take(TITLE_MAX_LEN)
    }

    /** 摘要 = 去语法后前 90 字符（单行） */
    fun deriveExcerpt(md: String): String = stripMarkdown(md).take(EXCERPT_MAX_LEN)

    /** 字数 = 去语法纯文本长度 */
    fun deriveWordCount(md: String): Int = stripMarkdown(md).length

    private fun firstNonEmptyLine(md: String): String? =
        md.lineSequence().firstOrNull { it.isNotBlank() }

    /**
     * 问/答面拆分（复习用）：首个独立 `---` 分隔行之前为问面，之后为答面。
     * 无分隔线时问面为 null（复习页以标题+摘要充当问面）。
     */
    fun splitQuestionAnswer(md: String): Pair<String?, String> {
        val m = Regex("(?m)^\\s*-{3,}\\s*$").find(md) ?: return null to md
        val q = md.substring(0, m.range.first).trim()
        val a = md.substring(m.range.last + 1).trim()
        if (q.isBlank()) return null to (a.ifBlank { md })
        return q to a
    }

    /**
     * 渲染预处理（跳过围栏代码块内部）：
     * 1) 相对路径图片 → 绝对 file URI（Markwon/Coil 需要）；
     * 2) 行内单美元 `$...$` → `$$...$$`（Markwon 内联数学仅认双美元）。
     */
    fun prepareForRender(markdown: String, baseDir: File?): String {
        val out = StringBuilder(markdown.length + 64)
        var inFence = false
        for (line in markdown.lineSequence()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) inFence = !inFence
            out.append(if (inFence) line else rewriteLine(line, baseDir))
            out.append('\n')
        }
        return out.toString()
    }

    private fun rewriteLine(line: String, baseDir: File?): String {
        var s = line
        if (baseDir != null) {
            s = s.replace(Regex("""(!\[[^\]]*\]\()(?!https?://|file:|content:)([^)]+)(\))""")) { m ->
                // 尺寸令牌（#w=NN）须剥离后再解析绝对路径，拼回交给渲染端
                val (path, pct) = splitImageSize(m.groupValues[2].trim())
                val token = pct?.let { "#w=$it" }.orEmpty()
                m.groupValues[1] + File(baseDir, path).absolutePath + token + m.groupValues[3]
            }
        }
        s = s.replace(Regex("(?<!\\\\)(?<!\\$)\\$(?!\\$)((?:[^$\\n\\\\]|\\\\.)+?)(?<!\\\\)\\$(?!\\$)")) { m ->
            "$$" + m.groupValues[1] + "$$"
        }
        return s
    }

    /** 旧版 front/back → Markdown 文档（迁移与旧备份导入共用） */
    fun buildLegacyNote(front: String, back: String, imageRefs: List<String>): String =
        buildString {
            append(front.trim())
            append('\n')
            if (back.isNotBlank()) {
                append("\n---\n\n")
                append(back.trim())
                append('\n')
            }
            if (imageRefs.isNotEmpty()) {
                append("\n## 附图\n")
                imageRefs.forEachIndexed { i, ref ->
                    append("\n![附图${i + 1}]($ref)\n")
                }
            }
        }

    /** mdPath：cards/{id}/note.md（相对 filesDir） */
    fun mdPathFor(cardId: Long): String = "cards/$cardId/$NOTE_FILE_NAME"

    fun noteFileFor(filesDir: File, cardId: Long): File =
        File(filesDir, mdPathFor(cardId))

    fun cardDirFor(filesDir: File, cardId: Long): File = File(filesDir, "cards/$cardId")

    fun assetsDirFor(filesDir: File, cardId: Long): File =
        File(cardDirFor(filesDir, cardId), ASSETS_DIR_NAME)
}
