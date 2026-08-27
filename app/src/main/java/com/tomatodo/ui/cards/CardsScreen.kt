package com.tomatodo.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import java.io.File

private fun cardTypeLabel(type: CardType): String = when (type) {
    CardType.MISTAKE -> "错题"
    CardType.KNOWLEDGE -> "知识点"
}

/**
 * 卡片页（OPTIMIZATION §4.1）：科目筛选 Chip 行 + 网格/分组双模式浏览。
 * 点击卡片翻转；编辑按钮进入专属撰写页。
 */
@Composable
fun CardsScreen(
    onEditCard: (Long?) -> Unit,
    viewModel: CardsViewModel = viewModel()
) {
    val cards by viewModel.cards.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val imagesByCard by viewModel.imagesByCard.collectAsState()
    val selectedSubjectId by viewModel.selectedSubjectId.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val subjectCounts by viewModel.subjectCounts.collectAsState()
    val unassignedCount by viewModel.unassignedCount.collectAsState()
    val filteredCards by viewModel.filteredCards.collectAsState()
    var flipped by remember { mutableStateOf(setOf<Long>()) }

    val subjectById = remember(subjects) { subjects.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("知识卡片", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            // 浏览模式切换：网格 / 分组
            IconButton(onClick = {
                viewModel.setViewMode(
                    if (viewMode == CardsViewMode.GRID) CardsViewMode.GROUPED else CardsViewMode.GRID
                )
            }) {
                Icon(
                    if (viewMode == CardsViewMode.GRID) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    contentDescription = if (viewMode == CardsViewMode.GRID) "切换为分组" else "切换为网格",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { onEditCard(null) }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建卡片")
            }
        }

        // 科目筛选行（OPTIMIZATION §4.1）
        SubjectFilterRow(
            subjects = subjects,
            selectedSubjectId = selectedSubjectId,
            subjectCounts = subjectCounts,
            unassignedCount = unassignedCount,
            totalCount = cards.size,
            onSelect = { viewModel.selectSubject(it) }
        )

        if (viewMode == CardsViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCards, key = { it.id }) { card ->
                    KnowledgeCardItem(
                        card = card,
                        subject = subjectById[card.subjectId],
                        imageFiles = imagesByCard[card.id]
                            ?.map { viewModel.imageFile(it.filePath) }
                            .orEmpty(),
                        flipped = card.id in flipped,
                        onClick = {
                            flipped = if (card.id in flipped) flipped - card.id else flipped + card.id
                        },
                        onEdit = { onEditCard(card.id) },
                        onDelete = { viewModel.deleteCard(card) }
                    )
                }
            }
        } else {
            // 分组模式：按科目分组，full-span 分组头
            val grouped = remember(filteredCards, subjects) {
                val bySubject = filteredCards.groupBy { it.subjectId }
                buildList {
                    subjects.forEach { s ->
                        bySubject[s.id]?.let { add(s to it) }
                    }
                    bySubject[null]?.let { add(null to it) }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                grouped.forEach { (subject, groupCards) ->
                    item(
                        key = "header_${subject?.id ?: -1L}",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        GroupHeader(subject, groupCards.size)
                    }
                    items(groupCards, key = { it.id }) { card ->
                        KnowledgeCardItem(
                            card = card,
                            subject = subjectById[card.subjectId],
                            imageFiles = imagesByCard[card.id]
                                ?.map { viewModel.imageFile(it.filePath) }
                                .orEmpty(),
                            flipped = card.id in flipped,
                            onClick = {
                                flipped = if (card.id in flipped) flipped - card.id else flipped + card.id
                            },
                            onEdit = { onEditCard(card.id) },
                            onDelete = { viewModel.deleteCard(card) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectFilterRow(
    subjects: List<Subject>,
    selectedSubjectId: Long?,
    subjectCounts: Map<Long, Int>,
    unassignedCount: Int,
    totalCount: Int,
    onSelect: (Long?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedSubjectId == null,
                onClick = { onSelect(null) },
                label = { Text("全部 ($totalCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        items(subjects, key = { it.id }) { s ->
            val count = subjectCounts[s.id] ?: 0
            FilterChip(
                selected = selectedSubjectId == s.id,
                onClick = { onSelect(if (selectedSubjectId == s.id) null else s.id) },
                label = { Text("${s.name} ($count)") },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(s.color))
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(s.color),
                    selectedLabelColor = Color.White
                )
            )
        }
        if (unassignedCount > 0) {
            item(key = "unassigned") {
                FilterChip(
                    selected = selectedSubjectId == UNASSIGNED_SUBJECT_ID,
                    onClick = {
                        onSelect(if (selectedSubjectId == UNASSIGNED_SUBJECT_ID) null else UNASSIGNED_SUBJECT_ID)
                    },
                    label = { Text("未分类 ($unassignedCount)") }
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(subject: Subject?, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(subject?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            subject?.name ?: "未分类",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KnowledgeCardItem(
    card: KnowledgeCard,
    subject: Subject?,
    imageFiles: List<File>,
    flipped: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                    cardTypeLabel(card.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (flipped) card.back else card.front,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = if (flipped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
            )
            if (imageFiles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    imageFiles.take(3).forEach { file ->
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onEdit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onDelete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
