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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as TomaTodoApplication).container.database
    private val cardDao = db.knowledgeCardDao()
    private val subjectDao = db.subjectDao()
    private val recordDao = db.reviewRecordDao()

    /** 每分钟心跳，驱动 due 查询跨天刷新（OPTIMIZATION 技术债 #9） */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                _now.value = System.currentTimeMillis()
            }
        }
    }

    val dueCards: StateFlow<List<KnowledgeCard>> = _now
        .flatMapLatest { cardDao.observeDue(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjects: StateFlow<List<Subject>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _reviewedToday = MutableStateFlow(0)
    val reviewedToday: StateFlow<Int> = _reviewedToday.asStateFlow()

    init {
        refreshReviewedToday()
    }

    fun refreshReviewedToday() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val from = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            _reviewedToday.value = recordDao.getInRange(from, System.currentTimeMillis()).size
        }
    }

    fun review(card: KnowledgeCard, result: ReviewResult, onReviewed: () -> Unit = {}) {
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
            _reviewedToday.value += 1
            onReviewed()
        }
    }
}
