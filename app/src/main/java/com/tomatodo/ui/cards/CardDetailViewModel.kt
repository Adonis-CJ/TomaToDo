package com.tomatodo.ui.cards

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.CardRepository
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 详情/编辑页状态 */
data class CardDetailState(
    val card: KnowledgeCard?,
    val content: String,
    val tags: List<String>
)

/**
 * 卡片详情（阅读/编辑）ViewModel（KMS v1.2）：
 * 加载卡片正文（note.md），防抖保存由 UI 层调度；图片压缩落盘到卡片 assets。
 */
class CardDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TomaTodoApplication).container
    private val repo: CardRepository = container.cardRepository
    private val subjectDao = container.database.subjectDao()
    private val tempDir: File = File(application.cacheDir, "camera").apply { mkdirs() }

    private val _detail = MutableStateFlow<CardDetailState?>(null)
    val detail: StateFlow<CardDetailState?> = _detail.asStateFlow()

    val subjects: StateFlow<List<Subject>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _allTagNames = MutableStateFlow<List<String>>(emptyList())
    val allTagNames: StateFlow<List<String>> = _allTagNames.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _lastSavedAt = MutableStateFlow<Long?>(null)
    val lastSavedAt: StateFlow<Long?> = _lastSavedAt.asStateFlow()

    fun load(cardId: Long?) {
        viewModelScope.launch {
            repo.ensureMigrated()
            refreshTags()
            _detail.value = if (cardId == null) {
                CardDetailState(null, "", emptyList())
            } else {
                val card = repo.getCard(cardId)
                CardDetailState(
                    card = card,
                    content = repo.readNote(cardId),
                    tags = repo.tagsForCard(cardId).map { it.name }
                )
            }
        }
    }

    private suspend fun refreshTags() {
        _allTagNames.value = withContext(Dispatchers.IO) {
            container.database.tagDao().getAll().map { it.name }
        }
    }

    fun save(
        cardId: Long?,
        content: String,
        subjectId: Long?,
        type: CardType,
        source: String?,
        tags: List<String>,
        onSaved: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            _saving.value = true
            val id = repo.save(
                CardRepository.SaveInput(
                    cardId = cardId,
                    content = content,
                    subjectId = subjectId,
                    type = type,
                    source = source,
                    tagNames = tags
                )
            )
            _saving.value = false
            _lastSavedAt.value = System.currentTimeMillis()
            refreshTags()
            if (id != 0L) onSaved(id)
        }
    }

    /**
     * 插入图片：新建未保存的卡片先落一次盘拿到 id，再压缩图片进 assets。
     * 返回 (卡片id, 相对引用)。
     */
    fun insertImage(
        cardId: Long?,
        pendingContent: String,
        subjectId: Long?,
        type: CardType,
        source: String?,
        tags: List<String>,
        uri: Uri,
        onResult: (Long, String?) -> Unit
    ) {
        viewModelScope.launch {
            val id = cardId ?: repo.save(
                CardRepository.SaveInput(null, pendingContent, subjectId, type, source, tags)
            ).takeIf { it != 0L } ?: return@launch
            val ref = repo.insertImage(id, uri)
            if (ref != null) _lastSavedAt.value = System.currentTimeMillis()
            onResult(id, ref)
        }
    }

    fun moveToTrash(cardId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.moveToTrash(cardId)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun assetFile(cardId: Long, ref: String): File = repo.assetFile(cardId, ref)

    // ---- 相机临时文件（拍照契约）----

    fun newCameraImage(): Pair<Uri, File> {
        val file = File(tempDir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(getApplication(), "com.tomatodo.fileprovider", file)
        return uri to file
    }

    fun discardCameraTemp(file: File?) {
        file?.delete()
    }
}
