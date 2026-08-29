package com.tomatodo.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.CardTextUtils
import com.tomatodo.data.ReviewResult
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import com.tomatodo.ui.cards.ImageViewerDialog
import com.tomatodo.ui.cards.render.MarkdownText
import com.tomatodo.ui.theme.AppSerif
import com.tomatodo.ui.theme.Motion
import java.io.File

/**
 * 复习页（KMS v1.2）：单卡沉浸刷题。
 * 问面 = 正文首个 `---` 之前（无分隔线时用标题+摘要），答面 = 其后全文；
 * 两者均以 MD+LaTeX 渲染，图片可点开全屏。
 */
@Composable
fun ReviewScreen(viewModel: ReviewViewModel = viewModel()) {
    val dueCards by viewModel.dueCards.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val reviewedToday by viewModel.reviewedToday.collectAsState()

    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf("") }
    var viewerImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var viewerIndex by remember { mutableIntStateOf(0) }

    val subjectById = remember(subjects) { subjects.associateBy { it.id } }
    val current = dueCards.getOrNull(index)

    // 换卡即加载正文
    LaunchedEffect(current?.id) {
        content = current?.let { viewModel.readNote(it.id) } ?: ""
        revealed = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("今日待复习", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "今日已复习 $reviewedToday 张",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            // 完成小结
            dueCards.isNotEmpty() && index >= dueCards.size -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("本轮复习完成", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "今日累计复习 $reviewedToday 张卡片",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            current == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "今天没有待复习的卡片",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                // 进度条 x/y
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1} / ${dueCards.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "剩余 ${dueCards.size - index} 张",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (index + 1).toFloat() / dueCards.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.small),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 单卡沉浸（fade + 自下浮入）
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 24 }) togetherWith
                            fadeOut(Motion.exit())
                    },
                    label = "reviewCard",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) { card ->
                    ReviewCard(
                        card = card,
                        subject = subjectById[card.subjectId],
                        content = if (card.id == current?.id) content else "",
                        baseDir = viewModel.baseDirFor(card.id),
                        revealed = revealed,
                        onReveal = { revealed = true },
                        onResult = { result ->
                            viewModel.review(card, result)
                            revealed = false
                            index += 1
                        },
                        onImageClick = { dest ->
                            val refs = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
                                .findAll(content).map { it.groupValues[1] }.toList()
                            val files = refs.map { r ->
                                File(viewModel.baseDirFor(card.id), r.removePrefix("./"))
                            }.filter { it.exists() }
                            viewerIndex = refs.indexOf(dest).coerceAtLeast(0)
                            viewerImages = files
                        }
                    )
                }
            }
        }
    }

    if (viewerImages.isNotEmpty()) {
        ImageViewerDialog(
            images = viewerImages,
            initialIndex = viewerIndex,
            onDismiss = { viewerImages = emptyList() }
        )
    }
}

@Composable
private fun ReviewCard(
    card: KnowledgeCard,
    subject: Subject?,
    content: String,
    baseDir: File,
    revealed: Boolean,
    onReveal: () -> Unit,
    onResult: (ReviewResult) -> Unit,
    onImageClick: (String) -> Unit
) {
    val (question, answer) = remember(card.id, content) {
        CardTextUtils.splitQuestionAnswer(content)
    }
    // 无分隔线的卡片：问面退化为标题 + 摘要
    val questionMd = question ?: "# ${card.title}\n\n${card.excerpt}"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subject != null) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(subject.color))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        subject.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "已复习 ${card.reviewCount} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // 问面（始终渲染）
                MarkdownText(
                    markdown = questionMd,
                    baseDir = baseDir,
                    onImageClick = onImageClick,
                    modifier = Modifier.fillMaxWidth()
                )

                // 答面揭示：自下滑入（翻开隐喻）
                AnimatedVisibility(
                    visible = revealed,
                    enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 6 },
                    exit = fadeOut(Motion.exit())
                ) {
                    Column {
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "答案",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = AppSerif),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(8.dp))
                                MarkdownText(
                                    markdown = answer,
                                    baseDir = baseDir,
                                    onImageClick = onImageClick,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                targetState = revealed,
                transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
                label = "reviewActions"
            ) { revealedState ->
                if (revealedState) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onResult(ReviewResult.FORGET) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f)
                        ) { Text("忘记") }
                        FilledTonalButton(
                            onClick = { onResult(ReviewResult.VAGUE) },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f)
                        ) { Text("模糊") }
                        Button(
                            onClick = { onResult(ReviewResult.REMEMBER) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f)
                        ) { Text("记得") }
                    }
                } else {
                    OutlinedButton(
                        onClick = onReveal,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("查看答案") }
                }
            }
        }
    }
}
