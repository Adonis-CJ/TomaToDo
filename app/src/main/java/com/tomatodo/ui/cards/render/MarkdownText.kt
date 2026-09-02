package com.tomatodo.ui.cards.render

import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.tomatodo.R
import com.tomatodo.data.CardTextUtils
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Markdown + LaTeX 渲染组件（KMS v1.2）。
 * 解析在后台线程，应用在主线程；主题色注入「墨·纸」令牌，跟随深浅色。
 * 列表摘要不做渲染（excerpt 纯文本），仅阅读/预览场景使用本组件。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textSize: TextUnit = TextUnit.Unspecified,
    baseDir: File? = null,
    onImageClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val headingTypeface = remember {
        runCatching { ResourcesCompat.getFont(context, R.font.noto_serif_sc) }.getOrNull()
    }

    // onImageClick 常为调用方每次重组新建的 lambda；若直接作为 markwon 的 remember key，
    // 会导致 markwon 反复重建、parsed 归零重解析（阅读视角闪烁/空白）。
    // 用 rememberUpdatedState 持有最新回调，再以「一次性稳定闭包」传入插件，markwon 只随主题/字体重建。
    val currentOnImageClick by rememberUpdatedState(onImageClick)
    val stableOnImageClick = remember {
        { dest: String ->
            currentOnImageClick?.invoke(dest)
            Unit
        }
    }

    val markwon = remember(colorScheme, headingTypeface) {
        buildMarkwon(context, colorScheme, headingTypeface, stableOnImageClick)
    }

    var parsed by remember(markwon) { mutableStateOf<Spanned?>(null) }
    LaunchedEffect(markdown, markwon, baseDir) {
        val prepared = CardTextUtils.prepareForRender(markdown, baseDir)
        parsed = withContext(Dispatchers.Default) { markwon.toMarkdown(prepared) }
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                KmsImagePlugin.applyMovementMethod(this)
                highlightColor = android.graphics.Color.TRANSPARENT
            }
        },
        update = { tv ->
            if (textSize != TextUnit.Unspecified) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.value)
            }
            tv.setTextColor(colorScheme.onSurface.toArgb())
            val p = parsed
            if (p != null) markwon.setParsedMarkdown(tv, p)
        },
        modifier = modifier
    )
}

private fun buildMarkwon(
    context: Context,
    colors: ColorScheme,
    headingTypeface: Typeface?,
    onImageClick: ((String) -> Unit)?
) = Markwon.builder(context)
    .usePlugin(CorePlugin.create())
    // 行内公式依赖 Markwon 自研 inline parser：JLatexMathPlugin 开启 inlinesEnabled 后会
    // registry.require(MarkwonInlineParserPlugin)，缺失即抛 IllegalStateException，故必须先注册。
    .usePlugin(MarkwonInlineParserPlugin.create())
    .usePlugin(KmsImagePlugin(context, onImageClick))
    .usePlugin(StrikethroughPlugin.create())
    .usePlugin(TablePlugin.create { b ->
        val density = context.resources.displayMetrics.density
        b.tableBorderColor(colors.outline.toArgb())
            .tableBorderWidth((1 * density).toInt().coerceAtLeast(1))
            .tableCellPadding((6 * density).toInt())
            .tableHeaderRowBackgroundColor(colors.surfaceVariant.toArgb())
            .tableEvenRowBackgroundColor(colors.surface.toArgb())
    })
    .usePlugin(TaskListPlugin.create(context))
    .usePlugin(object : AbstractMarkwonPlugin() {
        override fun configureTheme(builder: MarkwonTheme.Builder) {
            val density = context.resources.displayMetrics.density
            builder.linkColor(colors.primary.toArgb())
                .isLinkUnderlined(true)
                .codeTextColor(colors.primary.toArgb())
                .codeBackgroundColor(colors.surfaceVariant.toArgb())
                .codeBlockTextColor(colors.onSurface.toArgb())
                .codeBlockBackgroundColor(colors.surfaceVariant.toArgb())
                .codeBlockMargin((10 * density).toInt())
                .blockQuoteColor(colors.outline.toArgb())
                .blockQuoteWidth((3 * density).toInt().coerceAtLeast(1))
                .thematicBreakColor(colors.outline.toArgb())
                .thematicBreakHeight((2 * density).toInt().coerceAtLeast(1))
            headingTypeface?.let { builder.headingTypeface(it) }
        }
    })
    .let { builder ->
        val density = context.resources.displayMetrics.density
        val inlinePx = 15f * density
        val blockPx = 16f * density
        builder.usePlugin(
            JLatexMathPlugin.create(inlinePx, blockPx) { b ->
                // Builder 默认 blocksEnabled=true、inlinesEnabled=false；
                // 不显式开启则行内 `$$...$$` 不被识别，直接以源码文本呈现（阅读视角 LaTeX 失效根因）。
                b.inlinesEnabled(true)
                b.theme()
                    .inlineTextColor(colors.onSurface.toArgb())
                    .blockTextColor(colors.onSurface.toArgb())
                    .blockFitCanvas(true)
            }
        )
        builder.build()
    }
