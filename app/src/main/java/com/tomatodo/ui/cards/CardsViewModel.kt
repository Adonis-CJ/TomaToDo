package com.tomatodo.ui.cards

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.model.CardImage
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 待保存的图片：拍照已落盘（needsCopy=false），选图需从 content Uri 拷贝（needsCopy=true） */
data class PendingImage(val uri: Uri, val relativePath: String?, val needsCopy: Boolean)

class CardsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TomaTodoApplication).container
    private val db = container.database
    private val cardDao = db.knowledgeCardDao()
    private val imageDao = db.cardImageDao()
    private val subjectDao = db.subjectDao()
    private val imageDir: File = File(application.filesDir, "images").apply { mkdirs() }

    val cards: StateFlow<List<KnowledgeCard>> = cardDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjects: StateFlow<List<Subject>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val imagesByCard: StateFlow<Map<Long, List<CardImage>>> = imageDao.observeAllImages()
        .map { list: List<CardImage> -> list.groupBy { it.cardId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 为相机拍照创建目标文件与 FileProvider Uri */
    fun newCameraImage(): Pair<Uri, String> {
        val file = File(imageDir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "com.tomatodo.fileprovider",
            file
        )
        val relativePath = "images/${file.name}"
        return uri to relativePath
    }

    fun addCard(
        front: String,
        back: String,
        subjectId: Long?,
        type: CardType,
        source: String?,
        pendingImages: List<PendingImage> = emptyList()
    ) {
        if (front.isBlank() && back.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cardId = cardDao.upsert(
                KnowledgeCard(
                    front = front.trim(),
                    back = back.trim(),
                    subjectId = subjectId,
                    type = type,
                    source = source?.takeIf { it.isNotBlank() },
                    masteryLevel = 0,
                    reviewCount = 0,
                    nextReviewAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
            val entities = pendingImages.mapIndexedNotNull { index, pending ->
                val path = resolvePending(pending) ?: return@mapIndexedNotNull null
                CardImage(cardId = cardId, filePath = path, sortOrder = index, createdAt = now)
            }
            if (entities.isNotEmpty()) imageDao.insertAll(entities)
        }
    }

    fun deleteCard(card: KnowledgeCard) = viewModelScope.launch(Dispatchers.IO) {
        // 先删物理文件（记录由外键级联删除）
        imageDao.getAll().filter { it.cardId == card.id }.forEach { img ->
            runCatching { File(getApplication<Application>().filesDir, img.filePath).delete() }
        }
        cardDao.delete(card.id)
    }

    fun imageFile(filePath: String): File = File(getApplication<Application>().filesDir, filePath)

    private suspend fun resolvePending(pending: PendingImage): String? = withContext(Dispatchers.IO) {
        if (!pending.needsCopy && pending.relativePath != null) {
            return@withContext pending.relativePath
        }
        return@withContext runCatching {
            val target = File(imageDir, "${UUID.randomUUID()}.jpg")
            getApplication<Application>().contentResolver.openInputStream(pending.uri)
                ?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return@withContext null
            "images/${target.name}"
        }.getOrNull()
    }
}
