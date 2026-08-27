package com.tomatodo.ui.cards

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 待保存的图片（uri 统一在保存时压缩转正式文件；tempFile 需在取消/完成后清理） */
data class PendingImage(val uri: Uri, val tempFile: File? = null)

/** 编辑初始数据 */
data class CardEditData(val card: KnowledgeCard?, val images: List<CardImage>)

/** 卡片浏览模式：网格平铺 / 按科目分组 */
enum class CardsViewMode { GRID, GROUPED }

/** 「未分类」科目的哨兵 id（subjectId == null） */
const val UNASSIGNED_SUBJECT_ID = -1L

class CardsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TomaTodoApplication).container
    private val db = container.database
    private val cardDao = db.knowledgeCardDao()
    private val imageDao = db.cardImageDao()
    private val subjectDao = db.subjectDao()
    private val imageDir: File = File(application.filesDir, "images").apply { mkdirs() }
    private val tempDir: File = File(application.cacheDir, "camera").apply { mkdirs() }

    val cards: StateFlow<List<KnowledgeCard>> = cardDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjects: StateFlow<List<Subject>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val imagesByCard: StateFlow<Map<Long, List<CardImage>>> = imageDao.observeAllImages()
        .map { list: List<CardImage> -> list.groupBy { it.cardId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ---- 分科目管理（OPTIMIZATION §4.1）----

    private val _selectedSubjectId = MutableStateFlow<Long?>(null)
    val selectedSubjectId: StateFlow<Long?> = _selectedSubjectId.asStateFlow()

    private val _viewMode = MutableStateFlow(CardsViewMode.GRID)
    val viewMode: StateFlow<CardsViewMode> = _viewMode.asStateFlow()

    /** 各科目卡片数（不含"未分类"） */
    val subjectCounts: StateFlow<Map<Long, Int>> = cards
        .map { list: List<KnowledgeCard> ->
            list.filter { it.subjectId != null }
                .groupingBy { it.subjectId!! }
                .eachCount()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 未分配科目的卡片数 */
    val unassignedCount: StateFlow<Int> = cards
        .map { list: List<KnowledgeCard> -> list.count { it.subjectId == null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 筛选后的卡片（null = 全部；UNASSIGNED_ID = 未分类） */
    val filteredCards: StateFlow<List<KnowledgeCard>> = combine(cards, _selectedSubjectId) { list, sel ->
        when (sel) {
            null -> list
            UNASSIGNED_SUBJECT_ID -> list.filter { it.subjectId == null }
            else -> list.filter { it.subjectId == sel }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectSubject(id: Long?) { _selectedSubjectId.value = id }
    fun setViewMode(mode: CardsViewMode) { _viewMode.value = mode }

    // ---- 编辑 / 撰写（OPTIMIZATION §4.2）----

    /** 载入编辑数据；cardId 为 null 表示新建 */
    suspend fun loadForEdit(cardId: Long?): CardEditData =
        withContext(Dispatchers.IO) {
            if (cardId == null) {
                CardEditData(card = null, images = emptyList())
            } else {
                CardEditData(
                    card = cardDao.getById(cardId),
                    images = imageDao.getAll().filter { it.cardId == cardId }.sortedBy { it.sortOrder }
                )
            }
        }

    fun saveCard(
        existing: KnowledgeCard?,
        front: String,
        back: String,
        subjectId: Long?,
        type: CardType,
        source: String?,
        tags: List<String>,
        pendingImages: List<PendingImage>,
        keptImages: List<CardImage>,
        removedImages: List<CardImage>,
        onDone: () -> Unit = {}
    ) {
        if (front.isBlank() && back.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val card = (existing?.copy(
                front = front.trim(),
                back = back.trim(),
                subjectId = subjectId,
                type = type,
                source = source?.takeIf { it.isNotBlank() },
                tags = tags,
                updatedAt = now
            ) ?: KnowledgeCard(
                front = front.trim(),
                back = back.trim(),
                subjectId = subjectId,
                type = type,
                source = source?.takeIf { it.isNotBlank() },
                tags = tags,
                masteryLevel = 0,
                reviewCount = 0,
                nextReviewAt = now,
                createdAt = now,
                updatedAt = now
            ))
            val cardId = cardDao.upsert(card)

            // 删除的图片：删文件 + 删记录
            removedImages.forEach { img ->
                runCatching { File(getApplication<Application>().filesDir, img.filePath).delete() }
                imageDao.delete(img.id)
            }
            // 保留的图片：更新排序
            keptImages.forEachIndexed { index, img ->
                if (img.sortOrder != index) imageDao.updateSortOrder(img.id, index)
            }
            // 新增图片：压缩后落盘
            pendingImages.forEachIndexed { index, pending ->
                val path = compressToImages(pending.uri)
                if (path != null) {
                    imageDao.insert(
                        CardImage(
                            cardId = cardId,
                            filePath = path,
                            sortOrder = keptImages.size + index,
                            createdAt = now
                        )
                    )
                }
                pending.tempFile?.delete()
            }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun deleteCard(card: KnowledgeCard) = viewModelScope.launch(Dispatchers.IO) {
        // 先删物理文件（记录由外键级联删除）
        imageDao.getAll().filter { it.cardId == card.id }.forEach { img ->
            runCatching { File(getApplication<Application>().filesDir, img.filePath).delete() }
        }
        cardDao.delete(card.id)
    }

    fun imageFile(filePath: String): File = File(getApplication<Application>().filesDir, filePath)

    /** 为相机拍照创建临时文件（cacheDir），返回 FileProvider Uri */
    fun newCameraImage(): Pair<Uri, File> {
        val file = File(tempDir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "com.tomatodo.fileprovider",
            file
        )
        return uri to file
    }

    /** 把 uri 指向的图片压缩（长边 1600px / JPEG 85）写入内部存储，返回相对路径 */
    private suspend fun compressToImages(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            // 粗采样：避免整图解码进内存
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

            // 精确缩放到长边 1600
            val maxDim = maxOf(decoded.width, decoded.height)
            val bitmap = if (maxDim > 1600) {
                val scale = 1600f / maxDim
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it != decoded) decoded.recycle() }
            } else {
                decoded
            }

            val target = File(imageDir, "${UUID.randomUUID()}.jpg")
            target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            bitmap.recycle()
            "images/${target.name}"
        }.getOrNull()
    }
}
