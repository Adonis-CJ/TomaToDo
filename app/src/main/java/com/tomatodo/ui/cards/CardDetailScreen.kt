@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.tomatodo.ui.cards

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.CardTextUtils
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.Subject
import com.tomatodo.ui.cards.render.MarkdownText
import com.tomatodo.ui.theme.AppSerif
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DetailMode { READ, EDIT }

private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

/**
 * 卡片详情（KMS v1.2）：阅读视图（MD+LaTeX 全渲染）↔ 编辑视图（工具栏 + 分栏预览）。
 * 1.5s 防抖自动保存，退出即保存；新建卡片先选模板。
 */
@Composable
fun CardDetailScreen(
    cardId: Long?,
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = viewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val allTags by viewModel.allTagNames.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val lastSavedAt by viewModel.lastSavedAt.collectAsState()

    var initialized by remember { mutableStateOf(false) }
    var currentCardId by remember { mutableStateOf(cardId) }
    var mode by remember { mutableStateOf(if (cardId == null) DetailMode.EDIT else DetailMode.READ) }

    var content by remember { mutableStateOf(TextFieldValue("")) }
    var subjectId by remember { mutableStateOf<Long?>(null) }
    var type by remember { mutableStateOf(CardType.KNOWLEDGE) }
    var source by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }

    var showTemplateDialog by remember { mutableStateOf(cardId == null) }
    var showPreview by remember { mutableStateOf(false) }
    var formulaMenu by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var viewerImages by remember { mutableStateOf(listOf<File>()) }
    var viewerIndex by remember { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(cardId) { viewModel.load(cardId) }
    LaunchedEffect(detail) {
        val d = detail ?: return@LaunchedEffect
        if (!initialized) {
            content = TextFieldValue(d.content)
            subjectId = d.card?.subjectId
            type = d.card?.type ?: CardType.KNOWLEDGE
            source = d.card?.source ?: ""
            tags = d.tags
            initialized = true
        }
    }

    // 变更快照：防抖保存仅在真正有改动时执行（避免打开即写、updated 被顶掉）
    fun snapshot(): String =
        listOf(content.text, subjectId?.toString() ?: "", type.name, source, tags.joinToString("\u0001"))
            .joinToString("\u0002")
    var savedSnapshot by remember { mutableStateOf<String?>(null) }

    fun doSave(onDone: (Long) -> Unit = {}) {
        viewModel.save(
            cardId = currentCardId,
            content = content.text,
            subjectId = subjectId,
            type = type,
            source = source,
            tags = tags
        ) { id ->
            currentCardId = id
            savedSnapshot = snapshot()
            onDone(id)
        }
    }

    // 自动保存（1.5s 防抖）
    LaunchedEffect(content.text, subjectId, type, source, tags, initialized) {
        if (!initialized) return@LaunchedEffect
        delay(1500)
        if (snapshot() != savedSnapshot) doSave()
    }

    fun handleBack() {
        // 新建且空表单：直接丢弃，不落一张空卡
        val emptyNew = currentCardId == null && content.text.isBlank() &&
            subjectId == null && type == CardType.KNOWLEDGE && source.isBlank() && tags.isEmpty()
        if (emptyNew) {
            onBack()
            return
        }
        if (initialized && snapshot() != savedSnapshot) {
            doSave { onBack() }
        } else {
            onBack()
        }
    }
    BackHandler { handleBack() }

    // ---- 图片插入 ----
    var cameraTemp by remember { mutableStateOf<Pair<Uri, File>?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val temp = cameraTemp
        cameraTemp = null
        if (ok && temp != null) {
            viewModel.insertImage(
                currentCardId, content.text, subjectId, type, source, tags, temp.first
            ) { id, ref ->
                currentCardId = id
                if (ref != null) insertAtCursor({ content = it }, content, "\n![]($ref)\n")
            }
        }
        viewModel.discardCameraTemp(temp?.second)
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.insertImage(
                currentCardId, content.text, subjectId, type, source, tags, uri
            ) { id, ref ->
                currentCardId = id
                if (ref != null) insertAtCursor({ content = it }, content, "\n![]($ref)\n")
            }
        }
    }

    val baseDir = currentCardId?.let { id ->
        remember(id) { CardTextUtils.cardDirFor(context.filesDir, id) }
    }

    // ---- 新建模板选择 ----
    if (showTemplateDialog && initialized) {
        TemplatePickerDialog(
            onPick = { template ->
                content = TextFieldValue(template)
                showTemplateDialog = false
            },
            onDismiss = { showTemplateDialog = false }
        )
    }

    when (mode) {
        DetailMode.READ -> ReadView(
            detail = detail,
            subjects = subjects,
            tags = tags,
            content = content.text,
            baseDir = baseDir,
            onBack = ::handleBack,
            onEdit = { mode = DetailMode.EDIT },
            onTrash = { confirmTrash = true },
            onImageClick = { dest ->
                val id = currentCardId ?: return@ReadView
                val refs = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
                    .findAll(content.text).map { it.groupValues[1] }.toList()
                val files = refs.map { viewModel.assetFile(id, it) }.filter { it.exists() }
                viewerIndex = refs.indexOf(dest).coerceAtLeast(0)
                viewerImages = files
            }
        )

        DetailMode.EDIT -> EditView(
            content = content,
            onContentChange = { content = it },
            subjects = subjects,
            subjectId = subjectId,
            onSubjectChange = { subjectId = it },
            type = type,
            onTypeChange = { type = it },
            source = source,
            onSourceChange = { source = it },
            tags = tags,
            onTagsChange = { tags = it },
            tagInput = tagInput,
            onTagInputChange = { tagInput = it },
            allTags = allTags,
            saving = saving,
            lastSavedAt = lastSavedAt,
            showPreview = showPreview,
            onTogglePreview = { showPreview = !showPreview },
            onBack = ::handleBack,
            onToggleRead = { doSave { mode = DetailMode.READ } },
            baseDir = baseDir,
            onCamera = {
                val pair = viewModel.newCameraImage()
                cameraTemp = pair
                cameraLauncher.launch(pair.first)
            },
            onGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onFormulaMenu = { formulaMenu = true },
            formulaMenu = formulaMenu,
            onFormulaDismiss = { formulaMenu = false },
            onInsertFormula = { snippet -> insertAtCursor({ content = it }, content, "\$\$$snippet\$\$") },
            onTemplateMenu = { templateMenu = true },
            templateMenu = templateMenu,
            onTemplateDismiss = { templateMenu = false },
            onInsertTemplate = { template -> content = TextFieldValue(template) },
            onInsertToolbar = { text, cursorBack -> insertAtCursor({ content = it }, content, text, cursorBack) },
            onLineStartInsert = { prefix -> insertAtLineStart({ content = it }, content, prefix) },
            onWrapSelection = { wrap -> wrapSelection({ content = it }, content, wrap) }
        )
    }

    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text("移入回收站？") },
            text = { Text("「${detail?.card?.title ?: "无标题"}」将移入回收站，30 天内可随时恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmTrash = false
                    currentCardId?.let { id -> viewModel.moveToTrash(id) { onBack() } }
                        ?: onBack()
                }) { Text("移入回收站", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTrash = false }) { Text("取消") }
            }
        )
    }

    if (viewerImages.isNotEmpty()) {
        ImageViewerDialog(
            images = viewerImages,
            initialIndex = viewerIndex,
            onDismiss = { viewerImages = emptyList() }
        )
    }
}

// ============================ 阅读视图 ============================

@Composable
private fun ReadView(
    detail: CardDetailState?,
    subjects: List<Subject>,
    tags: List<String>,
    content: String,
    baseDir: File?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTrash: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val card = detail?.card
    val subject = subjects.find { it.id == card?.subjectId }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                card?.title ?: "无标题",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = AppSerif),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onTrash) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "移入回收站",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑")
            }
        }

        // 标签行
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                tags.forEach { TagChip(it) }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 正文渲染区
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                MarkdownText(
                    markdown = content,
                    baseDir = baseDir,
                    textSize = 16.sp,
                    onImageClick = onImageClick,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // 元信息条
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Text(
                metaLine(card, subject),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }
}

private fun metaLine(card: com.tomatodo.data.model.KnowledgeCard?, subject: Subject?): String {
    if (card == null) return ""
    val parts = mutableListOf<String>()
    subject?.let { parts += it.name }
    parts += if (card.type == CardType.MISTAKE) "错题" else "知识点"
    card.source?.let { parts += "来源：$it" }
    parts += "已复习 ${card.reviewCount} 次"
    parts += when (card.masteryLevel) {
        2 -> "已掌握"
        1 -> "模糊"
        else -> "待巩固"
    }
    parts += "下次复习 " + dateFormat.format(Date(card.nextReviewAt))
    parts += "更新 " + dateFormat.format(Date(card.updatedAt))
    return parts.joinToString("  ·  ")
}

// ============================ 编辑视图 ============================

@Composable
private fun EditView(
    content: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    subjects: List<Subject>,
    subjectId: Long?,
    onSubjectChange: (Long?) -> Unit,
    type: CardType,
    onTypeChange: (CardType) -> Unit,
    source: String,
    onSourceChange: (String) -> Unit,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    allTags: List<String>,
    saving: Boolean,
    lastSavedAt: Long?,
    showPreview: Boolean,
    onTogglePreview: () -> Unit,
    onBack: () -> Unit,
    onToggleRead: () -> Unit,
    baseDir: File?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFormulaMenu: () -> Unit,
    formulaMenu: Boolean,
    onFormulaDismiss: () -> Unit,
    onInsertFormula: (String) -> Unit,
    onTemplateMenu: () -> Unit,
    templateMenu: Boolean,
    onTemplateDismiss: () -> Unit,
    onInsertTemplate: (String) -> Unit,
    onInsertToolbar: (String, Int) -> Unit,
    onLineStartInsert: (String) -> Unit,
    onWrapSelection: (Pair<String, String>) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
                Text(
                    if (saving) "保存中…" else lastSavedAt?.let { "已保存 ${dateFormat.format(Date(it))}" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (!wide) {
                    IconButton(onClick = onTogglePreview) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = if (showPreview) "返回编辑" else "预览",
                            tint = if (showPreview) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onToggleRead) { Text("阅读") }
            }

            // 工具栏（预览态隐藏）
            if (!showPreview || wide) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolIcon(Icons.Outlined.Title, "标题") { onLineStartInsert("# ") }
                    ToolIcon(Icons.Outlined.FormatBold, "加粗") { onWrapSelection("**" to "**") }
                    ToolIcon(Icons.Outlined.FormatItalic, "斜体") { onWrapSelection("*" to "*") }
                    ToolIcon(Icons.Outlined.FormatStrikethrough, "删除线") { onWrapSelection("~~" to "~~") }
                    ToolIcon(Icons.Outlined.FormatListBulleted, "列表") { onLineStartInsert("- ") }
                    ToolIcon(Icons.Outlined.FormatQuote, "引用") { onLineStartInsert("> ") }
                    ToolIcon(Icons.Outlined.Code, "代码块") { onInsertToolbar("\n```\n\n```\n", 5) }
                    ToolIcon(Icons.Outlined.TableChart, "表格") {
                        onInsertToolbar("\n| 表头 | 表头 |\n|---|---|\n| 内容 | 内容 |\n", 0)
                    }
                    ToolIcon(Icons.Outlined.Link, "链接") { onWrapSelection("[" to "](https://)") }
                    Box {
                        ToolIcon(Icons.Outlined.Functions, "公式") { onFormulaMenu() }
                        DropdownMenu(expanded = formulaMenu, onDismissRequest = onFormulaDismiss) {
                            CardTextUtils.FORMULA_SNIPPETS.forEach { (label, snippet) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    trailingIcon = {
                                        Text(
                                            snippet,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(120.dp)
                                        )
                                    },
                                    onClick = {
                                        onFormulaDismiss()
                                        onInsertFormula(snippet)
                                    }
                                )
                            }
                        }
                    }
                    Box {
                        ToolIcon(Icons.Outlined.Edit, "模板") { onTemplateMenu() }
                        DropdownMenu(expanded = templateMenu, onDismissRequest = onTemplateDismiss) {
                            CardTextUtils.TEMPLATES.forEach { (name, body) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onTemplateDismiss()
                                        onInsertTemplate(body)
                                    }
                                )
                            }
                        }
                    }
                    ToolIcon(Icons.Outlined.PhotoCamera, "拍照") { onCamera() }
                    ToolIcon(Icons.Outlined.Image, "相册") { onGallery() }
                }
            }

            // 内容区：宽屏左编辑右预览，窄屏单栏切换
            Row(Modifier.weight(1f)) {
                if (!showPreview || wide) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = onContentChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = {
                            Text("以 Markdown 书写：# 标题、\$公式\$、表格、图片…")
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
                if (wide) {
                    VerticalDivider()
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        MarkdownText(
                            markdown = content.text,
                            baseDir = baseDir,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (showPreview) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        MarkdownText(
                            markdown = content.text,
                            baseDir = baseDir,
                            onImageClick = { /* 预览态图片点击忽略 */ },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 元数据条
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 科目选择
                        Box {
                            var subjectMenu by remember { mutableStateOf(false) }
                            AssistChip(
                                onClick = { subjectMenu = true },
                                label = {
                                    Text(subjects.find { it.id == subjectId }?.name ?: "科目")
                                },
                                leadingIcon = {
                                    val color = subjects.find { it.id == subjectId }?.color
                                    if (color != null) {
                                        Box(
                                            Modifier
                                                .size(10.dp)
                                                .background(
                                                    Color(color),
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            )
                            DropdownMenu(expanded = subjectMenu, onDismissRequest = { subjectMenu = false }) {
                                DropdownMenuItem(text = { Text("未分类") }, onClick = {
                                    onSubjectChange(null); subjectMenu = false
                                })
                                subjects.forEach { s ->
                                    DropdownMenuItem(text = { Text(s.name) }, onClick = {
                                        onSubjectChange(s.id); subjectMenu = false
                                    })
                                }
                            }
                        }
                        // 类型
                        FilterChip(
                            selected = type == CardType.KNOWLEDGE,
                            onClick = { onTypeChange(CardType.KNOWLEDGE) },
                            label = { Text("知识点") }
                        )
                        FilterChip(
                            selected = type == CardType.MISTAKE,
                            onClick = { onTypeChange(CardType.MISTAKE) },
                            label = { Text("错题") }
                        )
                        OutlinedTextField(
                            value = source,
                            onValueChange = onSourceChange,
                            modifier = Modifier
                                .width(200.dp)
                                .height(56.dp),
                            placeholder = { Text("来源（章节/真题）", style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true
                        )
                    }
                    // 标签输入 + 联想
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tags.forEach { t ->
                            AssistChip(
                                onClick = { onTagsChange(tags - t) },
                                label = { Text(t) },
                                trailingIcon = {
                                    Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                        OutlinedTextField(
                            value = tagInput,
                            onValueChange = onTagInputChange,
                            modifier = Modifier.width(160.dp),
                            placeholder = { Text("加标签", style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true
                        )
                        if (tagInput.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    if (tagInput.isNotBlank() && tagInput !in tags) {
                                        onTagsChange(tags + tagInput.trim())
                                    }
                                    onTagInputChange("")
                                },
                                label = { Text("添加") }
                            )
                        }
                    }
                    // 联想已有标签
                    val suggestions = allTags.filter {
                        it !in tags && (tagInput.isBlank() || it.contains(tagInput, ignoreCase = true))
                    }.take(8)
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.forEach { t ->
                                AssistChip(
                                    onClick = {
                                        onTagsChange(tags + t)
                                        onTagInputChange("")
                                    },
                                    label = { Text(t, style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    )
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun TagChip(name: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TemplatePickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "从模板开始更快成文；也可选择空白自由书写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CardTextUtils.TEMPLATES.forEach { (name, body) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(body) }
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

// ---- 插入辅助（纯函数式更新 TextFieldValue）----

private fun insertAtCursor(
    setter: (TextFieldValue) -> Unit,
    current: TextFieldValue,
    text: String,
    cursorBack: Int = 0
) {
    val sel = current.selection
    val newText = current.text.substring(0, sel.min) + text + current.text.substring(sel.max)
    val cursor = (sel.min + text.length - cursorBack).coerceIn(0, newText.length)
    setter(TextFieldValue(newText, TextRange(cursor)))
}

private fun insertAtLineStart(
    setter: (TextFieldValue) -> Unit,
    current: TextFieldValue,
    prefix: String
) {
    val pos = current.selection.min
    val lineStart = current.text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let {
        if (pos == 0) 0 else it + 1
    }
    val newText = current.text.substring(0, lineStart) + prefix + current.text.substring(lineStart)
    setter(TextFieldValue(newText, TextRange(pos + prefix.length)))
}

private fun wrapSelection(
    setter: (TextFieldValue) -> Unit,
    current: TextFieldValue,
    wrap: Pair<String, String>
) {
    val sel = current.selection
    if (sel.collapsed) {
        val text = wrap.first + wrap.second
        insertAtCursor(setter, current, text, wrap.second.length)
    } else {
        val selected = current.text.substring(sel.min, sel.max)
        val newText = current.text.substring(0, sel.min) + wrap.first + selected + wrap.second +
            current.text.substring(sel.max)
        setter(TextFieldValue(newText, TextRange(sel.max + wrap.first.length + wrap.second.length)))
    }
}
