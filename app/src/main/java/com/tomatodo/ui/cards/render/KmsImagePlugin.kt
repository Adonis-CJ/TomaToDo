package com.tomatodo.ui.cards.render

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import coil.imageLoader
import coil.request.ImageRequest
import com.tomatodo.data.CardTextUtils
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.DrawableUtils
import io.noties.markwon.image.ImageProps
import io.noties.markwon.image.ImageSpanFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.commonmark.node.Image
import java.io.File

/**
 * Markwon 图片插件（Coil 2 实现，KMS v1.2）：
 * 官方 image-coil 模块编译于 Coil 1.x，与项目 Coil 2.7 存在同包类冲突，故按其逻辑自研。
 * - 图片地址由 [CardTextUtils.prepareForRender] 预处理为绝对路径后交给 Coil；
 * - [onImageClick] 非空时给图片 span 追加点击事件（跳全屏查看器）。
 */
class KmsImagePlugin(
    private val context: Context,
    private val onImageClick: ((String) -> Unit)?
) : AbstractMarkwonPlugin() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<AsyncDrawable, Job>()

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(Image::class.java) { configuration, props ->
            val destination = props.get(ImageProps.DESTINATION)
            val span: Any? = ImageSpanFactory().getSpans(configuration, props)
            if (destination != null && onImageClick != null) {
                val click = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onImageClick?.invoke(destination)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        // 保持图片外观，不做链接着色
                    }
                }
                arrayOf(span, click)
            } else {
                span
            }
        }
    }

    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.asyncDrawableLoader(object : AsyncDrawableLoader() {
            override fun load(drawable: AsyncDrawable) {
                jobs.remove(drawable)?.cancel()
                jobs[drawable] = scope.launch {
                    // Coil 2 把 String 默认按 URL 解析，本地绝对路径会被当成 https:// 而加载失败；
                    // 必须显式转成 File 才会走本地文件加载。
                    val destination = drawable.destination
                    val data: Any = if (destination != null && destination.startsWith("/")) {
                        File(destination)
                    } else {
                        destination ?: ""
                    }
                    val request = ImageRequest.Builder(context)
                        .data(data)
                        .allowHardware(false)
                        .crossfade(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val loaded = result.drawable
                    if (drawable.isAttached && loaded != null) {
                        DrawableUtils.applyIntrinsicBoundsIfEmpty(loaded)
                        drawable.setResult(loaded)
                    }
                    jobs.remove(drawable)
                }
            }

            override fun cancel(drawable: AsyncDrawable) {
                jobs.remove(drawable)?.cancel()
            }

            override fun placeholder(drawable: AsyncDrawable): Drawable? = null
        })
    }

    override fun beforeSetText(textView: TextView, markdown: Spanned) {
        AsyncDrawableScheduler.unschedule(textView)
    }

    override fun afterSetText(textView: TextView) {
        AsyncDrawableScheduler.schedule(textView)
    }

    companion object {
        /** 供 Compose 侧初始化 TextView 的链接点击支持 */
        fun applyMovementMethod(textView: TextView) {
            textView.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
