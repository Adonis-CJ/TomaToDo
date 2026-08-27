package com.tomatodo.ui.cards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as TomaTodoApplication).container.database
    private val cardDao = db.knowledgeCardDao()
    private val subjectDao = db.subjectDao()

    val cards: StateFlow<List<KnowledgeCard>> = cardDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjects: StateFlow<List<Subject>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addCard(front: String, back: String, subjectId: Long?, type: CardType, source: String?) {
        if (front.isBlank() && back.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            cardDao.upsert(
                KnowledgeCard(
                    front = front.trim(),
                    back = back.trim(),
                    subjectId = subjectId,
                    type = type,
                    source = source?.takeIf { it.isNotBlank() },
                    masteryLevel = 0,
                    reviewCount = 0,
                    nextReviewAt = now,
                    createdAt = now
                )
            )
        }
    }

    fun deleteCard(card: KnowledgeCard) = viewModelScope.launch { cardDao.delete(card.id) }
}
