package com.tomatodo.ui.cards

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tomatodo.data.CardTextUtils
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val trashDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

/**
 * 回收站（KMS v1.2）：软删除卡片，可恢复 / 彻底删除 / 清空；
 * 30 天后由应用启动时惰性自动清除。
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: CardsViewModel = viewModel()
) {
    val trashCards by viewModel.trashCards.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    var purgeTarget by remember { mutableStateOf<KnowledgeCard?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    val subjectById = remember(subjects) { subjects.associateBy { it.id } }

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
            Column(Modifier.weight(1f)) {
                Text("回收站", style = MaterialTheme.typography.titleLarge)
                Text(
                    "删除满 ${CardTextUtils.TRASH_RETENTION_DAYS} 天自动清除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trashCards.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text("清空") }
            }
        }

        if (trashCards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "回收站是空的",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(trashCards, key = { it.id }) { card ->
                    TrashItem(
                        card = card,
                        subject = subjectById[card.subjectId],
                        onRestore = { viewModel.restore(card) },
                        onPurge = { purgeTarget = card }
                    )
                }
            }
        }
    }

    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("彻底删除？") },
            text = { Text("「${target.title}」及其图片将被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.purge(target)
                    purgeTarget = null
                }) { Text("彻底删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { purgeTarget = null }) { Text("取消") }
            }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("清空回收站？") },
            text = { Text("回收站内全部卡片将被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.purgeAll()
                    confirmEmpty = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TrashItem(
    card: KnowledgeCard,
    subject: Subject?,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subject != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(subject.color))
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "删除于 ${trashDateFormat.format(Date(card.deletedAt ?: 0L))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Outlined.Restore,
                    contentDescription = "恢复",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onPurge) {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = "彻底删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
