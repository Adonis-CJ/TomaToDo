package com.tomatodo.ui.cards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.CardRepository
import com.tomatodo.data.db.TagWithCount
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 卡片浏览模式：网格平铺 / 按科目分组 */
enum class CardsViewMode { GRID, GROUPED }

/** 「未分类」科目的哨兵 id（subjectId == null） */
const val UNASSIGNED_SUBJECT_ID = -1L

/**
 * 卡片列表 ViewModel（KMS v1.2）：摘要卡浏览、组合筛选（科目/标签/类型/待复习）、
 * 搜索（标题/摘要/来源/标签/正文兜底）、回收站。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CardsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: CardRepository = (application as TomaTodoApplication).container.cardRepository
    private val subjectDao = (application as TomaTodoApplication).container.database.subjectDao()

    init {
        viewModelScope.launch {
            repo.ensureMigrated()
            repo.purgeExpired()
        }
    }

    val cards: StateFlow<List<KnowledgeCard>> = repo.observeCards()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trashCards: StateFlow<List<KnowledgeCard>> = repo.observeTrash()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjects: StateFlow<List<Subject>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tagsWithCount: StateFlow<List<TagWithCount>> = repo.observeTagsWithCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val cardTagLinks = repo.observeCardTagLinks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 卡片 → 标签 id 列表（列表项展示与筛选共用） */
    val tagsByCard: StateFlow<Map<Long, List<Long>>> = cardTagLinks
        .map { list -> list.groupBy({ it.cardId }, { it.tagId }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ---- 组合筛选 ----

    private val _selectedSubjectId = MutableStateFlow<Long?>(null)
    val selectedSubjectId: StateFlow<Long?> = _selectedSubjectId.asStateFlow()

    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTagIds: StateFlow<Set<Long>> = _selectedTagIds.asStateFlow()

    private val _typeFilter = MutableStateFlow<CardType?>(null)
    val typeFilter: StateFlow<CardType?> = _typeFilter.asStateFlow()

    private val _dueOnly = MutableStateFlow(false)
    val dueOnly: StateFlow<Boolean> = _dueOnly.asStateFlow()

    private val _viewMode = MutableStateFlow(CardsViewMode.GRID)
    val viewMode: StateFlow<CardsViewMode> = _viewMode.asStateFlow()

    private val _filterSheetOpen = MutableStateFlow(false)
    val filterSheetOpen: StateFlow<Boolean> = _filterSheetOpen.asStateFlow()

    val subjectCounts: StateFlow<Map<Long, Int>> = cards
        .map { list -> list.filter { it.subjectId != null }.groupingBy { it.subjectId!! }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val unassignedCount: StateFlow<Int> = cards
        .map { list -> list.count { it.subjectId == null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val baseFiltered: StateFlow<List<KnowledgeCard>> = combine(
        cards, _selectedSubjectId, _typeFilter, _dueOnly
    ) { list, subject, type, due ->
        list.filter { card ->
            val subjectOk = when (subject) {
                null -> true
                UNASSIGNED_SUBJECT_ID -> card.subjectId == null
                else -> card.subjectId == subject
            }
            val typeOk = type == null || card.type == type
            val dueOk = !due || card.nextReviewAt <= System.currentTimeMillis()
            subjectOk && typeOk && dueOk
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredCards: StateFlow<List<KnowledgeCard>> = combine(
        baseFiltered, _selectedTagIds, cardTagLinks
    ) { list, tagIds, links ->
        if (tagIds.isEmpty()) list
        else {
            val tagsByCard = links.groupBy({ it.cardId }, { it.tagId })
            list.filter { card ->
                tagIds.all { t -> tagsByCard[card.id]?.contains(t) == true }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectSubject(id: Long?) { _selectedSubjectId.value = id }
    fun toggleTag(id: Long) {
        _selectedTagIds.value = _selectedTagIds.value.let { if (id in it) it - id else it + id }
    }
    fun setTypeFilter(type: CardType?) { _typeFilter.value = type }
    fun setDueOnly(value: Boolean) { _dueOnly.value = value }
    fun setViewMode(mode: CardsViewMode) { _viewMode.value = mode }
    fun setFilterSheet(open: Boolean) { _filterSheetOpen.value = open }
    fun clearFilters() {
        _selectedSubjectId.value = null
        _selectedTagIds.value = emptySet()
        _typeFilter.value = null
        _dueOnly.value = false
    }

    // ---- 搜索 ----

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<KnowledgeCard>>(emptyList())
    val searchResults: StateFlow<List<KnowledgeCard>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(250) // 输入防抖
            _searchResults.value = repo.search(query)
        }
    }

    // ---- 回收站 ----

    private var lastTrashed: KnowledgeCard? = null

    fun moveToTrash(card: KnowledgeCard) {
        lastTrashed = card
        viewModelScope.launch { repo.moveToTrash(card.id) }
    }

    /** Snackbar 撤销：恢复最近一次移入回收站的卡片 */
    fun undoTrash() {
        val card = lastTrashed ?: return
        lastTrashed = null
        viewModelScope.launch { repo.restore(card.id) }
    }

    fun restore(card: KnowledgeCard) = viewModelScope.launch { repo.restore(card.id) }
    fun purge(card: KnowledgeCard) = viewModelScope.launch { repo.purge(card.id) }
    fun purgeAll() = viewModelScope.launch { repo.purgeAll() }

    // ---- 标签管理 ----

    fun renameTag(tagId: Long, newName: String) = viewModelScope.launch {
        repo.renameTag(tagId, newName)
    }

    fun deleteTag(tagId: Long) = viewModelScope.launch { repo.deleteTag(tagId) }
}
