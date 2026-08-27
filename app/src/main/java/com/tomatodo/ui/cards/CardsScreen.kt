@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject

private fun cardTypeLabel(type: CardType): String = when (type) {
    CardType.MISTAKE -> "错题"
    CardType.KNOWLEDGE -> "知识点"
}

@Composable
fun CardsScreen(viewModel: CardsViewModel = viewModel()) {
    val cards by viewModel.cards.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
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
            Button(onClick = { showAddSheet = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建卡片")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(cards, key = { it.id }) { card ->
                KnowledgeCardItem(
                    card = card,
                    subject = subjectById[card.subjectId],
                    flipped = card.id in flipped,
                    onClick = { flipped = if (card.id in flipped) flipped - card.id else flipped + card.id },
                    onDelete = { viewModel.deleteCard(card) }
                )
            }
        }
    }

    if (showAddSheet) {
        AddCardSheet(
            subjects = subjects,
            onDismiss = { showAddSheet = false },
            onSave = { front, back, subjectId, type, source ->
                viewModel.addCard(front, back, subjectId, type, source)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun KnowledgeCardItem(
    card: KnowledgeCard,
    subject: Subject?,
    flipped: Boolean,
    onClick: () -> Unit,
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
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onDelete() },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }
}

@Composable
private fun AddCardSheet(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (front: String, back: String, subjectId: Long?, type: CardType, source: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    var type by remember { mutableStateOf(CardType.KNOWLEDGE) }
    var showSubjectMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("新建卡片", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = front,
                onValueChange = { front = it },
                label = { Text("正面（问题/知识点）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = back,
                onValueChange = { back = it },
                label = { Text("背面（答案/解析）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text("来源（章节/真题，可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { showTypeMenu = true }) {
                        Text(cardTypeLabel(type))
                    }
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        CardType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(cardTypeLabel(t)) },
                                onClick = { type = t; showTypeMenu = false }
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { showSubjectMenu = true }) {
                        Text(subjects.find { it.id == selectedSubjectId }?.name ?: "选择科目")
                    }
                    DropdownMenu(
                        expanded = showSubjectMenu,
                        onDismissRequest = { showSubjectMenu = false }
                    ) {
                        subjects.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = { selectedSubjectId = s.id; showSubjectMenu = false }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    onSave(front, back, selectedSubjectId, type, source)
                }) { Text("保存") }
            }
        }
    }
}
