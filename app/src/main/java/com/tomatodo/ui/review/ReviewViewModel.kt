package com.tomatodo.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.ReviewResult
import com.tomatodo.data.computeReviewOutcome
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.ReviewRecord
import com.tomatodo.data.model.Subject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as TomaTodoApplication).container.database
    private val cardDao = db.knowledgeCardDao()
    private val subjectDao = db.subjectDao()
    private val recordDao = db.reviewRecordDao()

    val dueCards: StateFlow<List<KnowledgeCard>> = cardDao.observeDue(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjects: StateFlow<List<Subject>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun review(card: KnowledgeCard, result: ReviewResult) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val outcome = computeReviewOutcome(result, card.reviewCount, now)
            val mastery = when (result) {
                ReviewResult.REMEMBER -> 2
                ReviewResult.VAGUE -> 1
                ReviewResult.FORGET -> 0
            }
            cardDao.updateReview(card.id, mastery, outcome.newReviewCount, outcome.nextReviewAt, now)
            recordDao.insert(
                ReviewRecord(cardId = card.id, result = result.name, reviewedAt = now)
            )
        }
    }
}
