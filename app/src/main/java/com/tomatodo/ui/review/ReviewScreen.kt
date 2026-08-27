package com.tomatodo.ui.review

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomatodo.data.ReviewResult
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject

@Composable
fun ReviewScreen(viewModel: ReviewViewModel = viewModel()) {
    val dueCards by viewModel.dueCards.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    var revealed by remember { mutableStateOf(setOf<Long>()) }

    val subjectById = remember(subjects) { subjects.associateBy { it.id } }

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
                    "${dueCards.size} 张卡片待复习",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (dueCards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "今天没有待复习的卡片",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(dueCards, key = { it.id }) { card ->
                    ReviewCard(
                        card = card,
                        subject = subjectById[card.subjectId],
                        revealed = card.id in revealed,
                        onReveal = { revealed = revealed + card.id },
                        onResult = { viewModel.review(card, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    card: KnowledgeCard,
    subject: Subject?,
    revealed: Boolean,
    onReveal: () -> Unit,
    onResult: (ReviewResult) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(20.dp)) {
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
            Spacer(Modifier.height(12.dp))
            Text(card.front, style = MaterialTheme.typography.bodyLarge)
            if (revealed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    card.back,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onResult(ReviewResult.FORGET) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("忘记") }
                    FilledTonalButton(onClick = { onResult(ReviewResult.VAGUE) }) { Text("模糊") }
                    Button(
                        onClick = { onResult(ReviewResult.REMEMBER) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) { Text("记得") }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onReveal) { Text("查看答案") }
            }
        }
    }
}
