@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.tomatodo.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.db.TagWithCount
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import com.tomatodo.ui.theme.AppSerif
import kotlinx.coroutines.launch

/**
 * 知识卡片页（KMS v1.2）：摘要卡网格/分组浏览、搜索、组合筛选（科目+标签+类型+待复习）、
 * 回收站入口；删除先进回收站并可 Snackbar 撤销。
 */
@Composable
fun CardsScreen(
    onEditCard: (Long?) -> Unit,
    onOpenCard: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: CardsViewModel = viewModel()
) {
    val cards by viewModel.cards.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val tagsWithCount by viewModel.tagsWithCount.collectAsState()
    val tagsByCard by viewModel.tagsByCard.collectAsState()
    val selectedSubjectId by viewModel.selectedSubjectId.collectAsState()
    val selectedTagIds by viewModel.selectedTagIds.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val dueOnly by viewModel.dueOnly.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val subjectCounts by viewModel.subjectCounts.collectAsState()
    val unassignedCount by viewModel.unassignedCount.collectAsState()
    val filteredCards by viewModel.filteredCards.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val filterSheetOpen by viewModel.filterSheetOpen.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var manageTags by remember { mutableStateOf(false) }

    val subjectById = remember(subjects) { subjects.associateBy { it.id } }
    val tagNameById = remember(tagsWithCount) { tagsWithCount.associate { it.tagId to it.name } }
    val searching = searchQuery.isNotBlank()
    val visibleCards = if (searching) searchResults else filteredCards

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 标题行
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("知识卡片", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
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
                IconButton(onClick = onOpenTrash) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "回收站",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = { onEditCard(null) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建")
                }
            }

            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::search,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                placeholder = { Text("搜索标题、摘要、来源、标签或正文…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            // 科目筛选行
            SubjectFilterRow(
                subjects = subjects,
                selectedSubjectId = selectedSubjectId,
                subjectCounts = subjectCounts,
                unassignedCount = unassignedCount,
                totalCount = cards.size,
                onSelect = viewModel::selectSubject,
                onOpenFilter = { viewModel.setFilterSheet(true) }
            )

            // 已激活的非科目筛选
            val activeTagNames = tagsWithCount
                .filter { it.tagId in selectedTagIds }
                .map { it.name }
            if (activeTagNames.isNotEmpty() || typeFilter != null || dueOnly) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeTagNames.take(4).forEach { name ->
                        FilterChip(
                            selected = true,
                            onClick = {
                                viewModel.toggleTag(tagsWithCount.first { it.name == name }.tagId)
                            },
                            label = { Text(name) }
                        )
                    }
                    if (typeFilter != null) {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.setTypeFilter(null) },
                            label = { Text(if (typeFilter == CardType.MISTAKE) "错题" else "知识点") }
                        )
                    }
                    if (dueOnly) {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.setDueOnly(false) },
                            label = { Text("待复习") }
                        )
                    }
                    TextButton(onClick = viewModel::clearFilters) { Text("清除") }
                }
            }

            // 卡片主体
            if (visibleCards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            searching -> "未找到匹配的卡片"
                            cards.isEmpty() -> "还没有卡片\n点右上角「新建」记录第一个知识点"
                            else -> "当前筛选下没有卡片"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (viewMode == CardsViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleCards, key = { it.id }) { card ->
                        SummaryCardItem(
                            card = card,
                            subject = subjectById[card.subjectId],
                            tagNames = tagsByCard[card.id].orEmpty().mapNotNull { tagNameById[it] },
                            onClick = { onOpenCard(card.id) },
                            onTrash = {
                                viewModel.moveToTrash(card)
                                scope.launch {
                                    val r = snackbar.showSnackbar("已移入回收站", actionLabel = "撤销")
                                    if (r == SnackbarResult.ActionPerformed) viewModel.undoTrash()
                                }
                            }
                        )
                    }
                }
            } else {
                val grouped = remember(filteredCards, subjects) {
                    val bySubject = filteredCards.groupBy { it.subjectId }
                    buildList {
                        subjects.forEach { s -> bySubject[s.id]?.let { add(s to it) } }
                        bySubject[null]?.let { add(null to it) }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    grouped.forEach { (subject, groupCards) ->
                        item(
                            key = "header_${subject?.id ?: -1L}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) { GroupHeader(subject, groupCards.size) }
                        items(groupCards, key = { it.id }) { card ->
                            SummaryCardItem(
                                card = card,
                                subject = subjectById[card.subjectId],
                                tagNames = tagsByCard[card.id].orEmpty().mapNotNull { tagNameById[it] },
                                onClick = { onOpenCard(card.id) },
                                onTrash = {
                                    viewModel.moveToTrash(card)
                                    scope.launch {
                                        val r = snackbar.showSnackbar("已移入回收站", actionLabel = "撤销")
                                        if (r == SnackbarResult.ActionPerformed) viewModel.undoTrash()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 底部 Snackbar（撤销删除）
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // 筛选抽屉
    if (filterSheetOpen) {
        FilterSheet(
            tagsWithCount = tagsWithCount,
            selectedTagIds = selectedTagIds,
            typeFilter = typeFilter,
            dueOnly = dueOnly,
            onToggleTag = viewModel::toggleTag,
            onTypeFilter = viewModel::setTypeFilter,
            onDueOnly = viewModel::setDueOnly,
            onManageTags = {
                viewModel.setFilterSheet(false)
                manageTags = true
            },
            onDismiss = { viewModel.setFilterSheet(false) }
        )
    }

    if (manageTags) {
        TagManageDialog(onDismiss = { manageTags = false }, viewModel = viewModel)
    }
}

@Composable
private fun SubjectFilterRow(
    subjects: List<Subject>,
    selectedSubjectId: Long?,
    subjectCounts: Map<Long, Int>,
    unassignedCount: Int,
    totalCount: Int,
    onSelect: (Long?) -> Unit,
    onOpenFilter: () -> Unit
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
                        onSelect(
                            if (selectedSubjectId == UNASSIGNED_SUBJECT_ID) null else UNASSIGNED_SUBJECT_ID
                        )
                    },
                    label = { Text("未分类 ($unassignedCount)") }
                )
            }
        }
        item(key = "more") {
            FilterChip(
                selected = false,
                onClick = onOpenFilter,
                label = { Text("更多筛选") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
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
        Text(subject?.name ?: "未分类", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(6.dp))
        Text(
            "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 摘要卡：科目色点 + 类型 · 标题 · 两行摘要 · 标签 · 复习徽标 */
@Composable
private fun SummaryCardItem(
    card: KnowledgeCard,
    subject: Subject?,
    tagNames: List<String>,
    onClick: () -> Unit,
    onTrash: () -> Unit
) {
    val due = card.nextReviewAt <= System.currentTimeMillis()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                } else {
                    Text(
                        "未分类",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (card.type == CardType.MISTAKE) "错题" else "知识点",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "移入回收站",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onTrash)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                card.title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = AppSerif),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (card.excerpt.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    card.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (tagNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tagNames.take(4).forEach { TagChip(it) }
                    if (tagNames.size > 4) TagChip("+${tagNames.size - 4}")
                }
            }
            if (due) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Text(
                        "待复习",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ============================ 筛选抽屉 ============================

@Composable
private fun FilterSheet(
    tagsWithCount: List<TagWithCount>,
    selectedTagIds: Set<Long>,
    typeFilter: CardType?,
    dueOnly: Boolean,
    onToggleTag: (Long) -> Unit,
    onTypeFilter: (CardType?) -> Unit,
    onDueOnly: (Boolean) -> Unit,
    onManageTags: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("筛选", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onManageTags) { Text("管理标签") }
            }
            Spacer(Modifier.height(8.dp))

            Text("类型", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = typeFilter == null,
                    onClick = { onTypeFilter(null) },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = typeFilter == CardType.KNOWLEDGE,
                    onClick = { onTypeFilter(CardType.KNOWLEDGE) },
                    label = { Text("知识点") }
                )
                FilterChip(
                    selected = typeFilter == CardType.MISTAKE,
                    onClick = { onTypeFilter(CardType.MISTAKE) },
                    label = { Text("错题") }
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("只看待复习", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "到期卡片优先（遗忘曲线排程）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = dueOnly, onCheckedChange = onDueOnly)
            }
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("标签", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (selectedTagIds.isNotEmpty()) {
                    Text(
                        "多选取交集 · 已选 ${selectedTagIds.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (tagsWithCount.isEmpty()) {
                Text(
                    "还没有标签，编辑卡片时可以添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tagsWithCount.forEach { tag ->
                        FilterChip(
                            selected = tag.tagId in selectedTagIds,
                            onClick = { onToggleTag(tag.tagId) },
                            label = { Text("${tag.name} (${tag.cardCount})") }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================ 标签管理 ============================

/** 重命名（重名自动合并）/ 删除标签 */
@Composable
fun TagManageDialog(onDismiss: () -> Unit, viewModel: CardsViewModel = viewModel()) {
    val tagsWithCount by viewModel.tagsWithCount.collectAsState()
    var renameTarget by remember { mutableStateOf<TagWithCount?>(null) }
    var deleteTarget by remember { mutableStateOf<TagWithCount?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理标签") },
        text = {
            Column {
                if (tagsWithCount.isEmpty()) {
                    Text("还没有标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tagsWithCount.forEach { tag ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tag.name, Modifier.weight(1f))
                        Text(
                            "${tag.cardCount} 张",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = {
                            renameTarget = tag
                            renameText = tag.name
                        }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "重命名",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { deleteTarget = tag }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名标签") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameTag(target.tagId, renameText)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除标签「${target.name}」？") },
            text = { Text("卡片不受影响，仅解除关联。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(target.tagId)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}
