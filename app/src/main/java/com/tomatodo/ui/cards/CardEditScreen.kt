@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.tomatodo.ui.cards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tomatodo.data.model.CardImage
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import java.io.File

/**
 * 专属卡片撰写页（OPTIMIZATION §4.2）：新建/编辑复用。
 * 平板横屏双栏（左表单 / 右实时预览），窄屏单栏滚动。
 */
@Composable
fun CardEditScreen(
    cardId: Long?,
    onBack: () -> Unit,
    viewModel: CardsViewModel = viewModel()
) {
    val subjects by viewModel.subjects.collectAsState()
    var editData by remember { mutableStateOf<CardEditData?>(null) }
    var initialized by remember { mutableStateOf(false) }

    // 表单状态
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    var type by remember { mutableStateOf(CardType.KNOWLEDGE) }
    var keptImages by remember { mutableStateOf(listOf<CardImage>()) }
    var removedImages by remember { mutableStateOf(listOf<CardImage>()) }
    var pendingImages by remember { mutableStateOf(listOf<PendingImage>()) }
    var flipped by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableIntStateOf(-1) }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(cardId) { editData = viewModel.loadForEdit(cardId) }
    LaunchedEffect(editData) {
        val d = editData ?: return@LaunchedEffect
        if (!initialized) {
            front = d.card?.front ?: ""
            back = d.card?.back ?: ""
            source = d.card?.source ?: ""
            tagsText = d.card?.tags?.joinToString("、") ?: ""
            selectedSubjectId = d.card?.subjectId
            type = d.card?.type ?: CardType.KNOWLEDGE
            keptImages = d.images
            initialized = true
        }
    }

    var cameraTemp by remember { mutableStateOf(PendingImage(android.net.Uri.EMPTY, null)) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingImages = pendingImages + PendingImage(
                uri = cameraTemp.uri, tempFile = cameraTemp.tempFile
            )
        } else {
            cameraTemp.tempFile?.delete()
        }
        cameraTemp = PendingImage(android.net.Uri.EMPTY, null)
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { pendingImages = pendingImages + PendingImage(uri = it) }
    }

    val existing = editData?.card
    val canSave = front.isNotBlank() || back.isNotBlank()
    val dirty = initialized && (
        front != (existing?.front ?: "") || back != (existing?.back ?: "") ||
            pendingImages.isNotEmpty() || removedImages.isNotEmpty()
        )

    fun tryExit() {
        if (dirty) confirmExit = true else onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton { tryExit() }
            Text(
                if (existing == null) "新建卡片" else "编辑卡片",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    viewModel.saveCard(
                        existing = existing,
                        front = front,
                        back = back,
                        subjectId = selectedSubjectId,
                        type = type,
                        source = source,
                        tags = tagsText.split("、", ",", "，")
                            .map { it.trim() }.filter { it.isNotBlank() },
                        pendingImages = pendingImages,
                        keptImages = keptImages,
                        removedImages = removedImages,
                        onDone = onBack
                    )
                },
                enabled = canSave
            ) { Text("保存") }
            Spacer(Modifier.width(8.dp))
        }

        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoPane = maxWidth >= 840.dp
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                    ) { EditForm(
                        subjects = subjects,
                        front = front, onFront = { front = it },
                        back = back, onBack = { back = it },
                        source = source, onSource = { source = it },
                        tagsText = tagsText, onTags = { tagsText = it },
                        selectedSubjectId = selectedSubjectId, onSubject = { selectedSubjectId = it },
                        type = type, onType = { type = it },
                        keptImages = keptImages,
                        removedImages = removedImages,
                        pendingImages = pendingImages,
                        onTakePhoto = {
                            val (uri, file) = viewModel.newCameraImage()
                            cameraTemp = PendingImage(uri, file)
                            cameraLauncher.launch(uri)
                        },
                        onPick = {
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveKept = { img ->
                            keptImages = keptImages - img
                            removedImages = removedImages + img
                        },
                        onRemovePending = { p ->
                            p.tempFile?.delete()
                            pendingImages = pendingImages - p
                        },
                        onViewImage = { idx -> viewerIndex = idx },
                        viewModel = viewModel
                    ) }
                    PreviewPane(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp),
                        subject = subjects.find { it.id == selectedSubjectId },
                        frontText = front.ifBlank { "正面（问题 / 知识点）" },
                        backText = back.ifBlank { "背面（答案 / 解析）" },
                        type = type,
                        previewFiles = keptImages.map { viewModel.imageFile(it.filePath) },
                        flipped = flipped,
                        onFlip = { flipped = !flipped }
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    EditForm(
                        subjects = subjects,
                        front = front, onFront = { front = it },
                        back = back, onBack = { back = it },
                        source = source, onSource = { source = it },
                        tagsText = tagsText, onTags = { tagsText = it },
                        selectedSubjectId = selectedSubjectId, onSubject = { selectedSubjectId = it },
                        type = type, onType = { type = it },
                        keptImages = keptImages,
                        removedImages = removedImages,
                        pendingImages = pendingImages,
                        onTakePhoto = {
                            val (uri, file) = viewModel.newCameraImage()
                            cameraTemp = PendingImage(uri, file)
                            cameraLauncher.launch(uri)
                        },
                        onPick = {
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveKept = { img ->
                            keptImages = keptImages - img
                            removedImages = removedImages + img
                        },
                        onRemovePending = { p ->
                            p.tempFile?.delete()
                            pendingImages = pendingImages - p
                        },
                        onViewImage = { idx -> viewerIndex = idx },
                        viewModel = viewModel
                    )
                    Spacer(Modifier.height(16.dp))
                    PreviewPane(
                        modifier = Modifier.fillMaxWidth(),
                        subject = subjects.find { it.id == selectedSubjectId },
                        frontText = front.ifBlank { "正面（问题 / 知识点）" },
                        backText = back.ifBlank { "背面（答案 / 解析）" },
                        type = type,
                        previewFiles = keptImages.map { viewModel.imageFile(it.filePath) },
                        flipped = flipped,
                        onFlip = { flipped = !flipped }
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (viewerIndex >= 0) {
        ImageViewerDialog(
            images = keptImages.map { viewModel.imageFile(it.filePath) },
            initialIndex = viewerIndex,
            onDismiss = { viewerIndex = -1 }
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("放弃编辑？") },
            text = { Text("当前内容尚未保存，确定要离开吗？") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onBack() }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("继续编辑") }
            }
        )
    }
}

@Composable
private fun IconButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
    }
}

@Composable
private fun EditForm(
    subjects: List<Subject>,
    front: String, onFront: (String) -> Unit,
    back: String, onBack: (String) -> Unit,
    source: String, onSource: (String) -> Unit,
    tagsText: String, onTags: (String) -> Unit,
    selectedSubjectId: Long?, onSubject: (Long?) -> Unit,
    type: CardType, onType: (CardType) -> Unit,
    keptImages: List<CardImage>,
    removedImages: List<CardImage>,
    pendingImages: List<PendingImage>,
    onTakePhoto: () -> Unit,
    onPick: () -> Unit,
    onRemoveKept: (CardImage) -> Unit,
    onRemovePending: (PendingImage) -> Unit,
    onViewImage: (Int) -> Unit,
    viewModel: CardsViewModel
) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        // 科目前置（色块选择器）
        Text("科目", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subjects, key = { it.id }) { s ->
                val selected = selectedSubjectId == s.id
                SubjectPill(
                    subject = s,
                    selected = selected,
                    onClick = { onSubject(if (selected) null else s.id) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // 类型
        Text("类型", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = type == CardType.KNOWLEDGE,
                onClick = { onType(CardType.KNOWLEDGE) },
                label = { Text("知识点") }
            )
            FilterChip(
                selected = type == CardType.MISTAKE,
                onClick = { onType(CardType.MISTAKE) },
                label = { Text("错题") }
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = source,
            onValueChange = onSource,
            label = { Text("来源（章节 / 真题，可选）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = tagsText,
            onValueChange = onTags,
            label = { Text("标签（用「、」分隔，可选）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = front,
            onValueChange = onFront,
            label = { Text("正面（问题 / 知识点）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = back,
            onValueChange = onBack,
            label = { Text("背面（答案 / 解析）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(Modifier.height(20.dp))

        // 图片附件
        Text("图片附件", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTakePhoto) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("拍照")
            }
            OutlinedButton(onClick = onPick) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("选图")
            }
        }
        Spacer(Modifier.height(12.dp))

        val pendingThumbs = pendingImages.toList()
        if (keptImages.isNotEmpty() || pendingThumbs.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(keptImages.size, key = { keptImages[it].id }) { idx ->
                    val img = keptImages[idx]
                    ImageThumb(file = viewModel.imageFile(img.filePath),
                        onClick = { onViewImage(idx) },
                        onRemove = { onRemoveKept(img) })
                }
                items(pendingThumbs.size, key = { 10_000 + it }) { idx ->
                    val p = pendingThumbs[idx]
                    ImageThumb(uri = p.uri,
                        onClick = {},
                        onRemove = { onRemovePending(p) })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SubjectPill(subject: Subject, selected: Boolean, onClick: () -> Unit) {
    val color = Color(subject.color)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) color else MaterialTheme.colorScheme.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.onPrimary else color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                subject.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ImageThumb(
    file: File? = null,
    uri: android.net.Uri? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box {
        AsyncImage(
            model = file ?: uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除图片",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 右栏实时预览：卡片成品态，点击模拟翻转 */
@Composable
private fun PreviewPane(
    modifier: Modifier = Modifier,
    subject: Subject?,
    frontText: String,
    backText: String,
    type: CardType,
    previewFiles: List<File>,
    flipped: Boolean,
    onFlip: () -> Unit
) {
    Column(modifier) {
        Text(
            "预览（点击卡片翻转）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onFlip)
        ) {
            Column(Modifier.padding(16.dp)) {
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
                        if (type == CardType.MISTAKE) "错题" else "知识点",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (flipped) backText else frontText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    color = if (flipped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                )
                if (previewFiles.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewFiles.take(3).forEach { file ->
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
