package com.tomatodo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.tomatodo.data.db.TomaTodoDatabase
import com.tomatodo.data.model.CardTag
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 知识卡片仓库（KMS v1.2）：文件为源、数据库为索引的唯一写入口。
 * 保存 = 原子写 note.md → 回填派生索引 → 同步标签；读列表只查 Room，不触文件 IO。
 */
class CardRepository(
    private val context: Context,
    private val db: TomaTodoDatabase
) {
    private val filesDir: File get() = context.filesDir
    private val cardDao = db.knowledgeCardDao()
    private val tagDao = db.tagDao()

    private companion object {
        const val TAG = "CardRepository"
    }

    // ---- 一次性启动迁移 ----

    private val migrateOnce = AtomicBoolean(false)
    private val migrateMutex = Mutex()

    suspend fun ensureMigrated() {
        if (migrateOnce.get()) return
        migrateMutex.withLock {
            if (migrateOnce.get()) return
            CardFileMigrator.migrateIfNeeded(db, filesDir)
            migrateOnce.set(true)
        }
    }

    // ---- 观察 ----

    fun observeCards(): Flow<List<KnowledgeCard>> = cardDao.observeAll()
    fun observeTrash(): Flow<List<KnowledgeCard>> = cardDao.observeTrash()
    fun observeTagsWithCount() = tagDao.observeAllWithCount()
    fun observeCardTagLinks() = tagDao.observeAllLinks()

    suspend fun getCard(cardId: Long): KnowledgeCard? = cardDao.getById(cardId)
    suspend fun tagsForCard(cardId: Long): List<Tag> = tagDao.tagsForCard(cardId)
    suspend fun readNote(cardId: Long): String = withContext(Dispatchers.IO) {
        val f = CardTextUtils.noteFileFor(filesDir, cardId)
        if (f.exists()) f.readText() else ""
    }

    // ---- 保存（新建 / 编辑）----

    data class SaveInput(
        val cardId: Long?,             // null = 新建
        val content: String,
        val subjectId: Long?,
        val type: com.tomatodo.data.model.CardType,
        val source: String?,
        val tagNames: List<String>
    )

    suspend fun save(input: SaveInput): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        db.withTransaction {
            val existing = input.cardId?.let { cardDao.getById(it) }
            // 允许空正文落盘（先插图后写字的流程），但空内容 + 新建时不再拒绝
            val id = existing?.id ?: cardDao.insert(
                KnowledgeCard(
                    subjectId = input.subjectId,
                    type = input.type,
                    source = input.source?.takeIf { it.isNotBlank() },
                    nextReviewAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
            writeNoteAtomically(id, input.content)

            val card = (existing ?: cardDao.getById(id)!!).copy(
                title = CardTextUtils.deriveTitle(input.content),
                excerpt = CardTextUtils.deriveExcerpt(input.content),
                wordCount = CardTextUtils.deriveWordCount(input.content),
                mdPath = CardTextUtils.mdPathFor(id),
                subjectId = input.subjectId,
                type = input.type,
                source = input.source?.takeIf { it.isNotBlank() },
                deletedAt = null,
                updatedAt = now
            )
            cardDao.update(card)
            syncTags(id, input.tagNames)
            invalidateCache(id)
            id
        }
    }

    /** 原子写：tmp + rename，进程被杀不产生半截文件 */
    private fun writeNoteAtomically(cardId: Long, content: String) {
        val dir = CardTextUtils.cardDirFor(filesDir, cardId)
        dir.mkdirs()
        val note = File(dir, CardTextUtils.NOTE_FILE_NAME)
        val tmp = File(dir, "${CardTextUtils.NOTE_FILE_NAME}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(note)) {
            note.delete()
            if (!tmp.renameTo(note)) tmp.copyTo(note, overwrite = true)
        }
    }

    private suspend fun syncTags(cardId: Long, names: List<String>) {
        tagDao.clearForCard(cardId)
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { name ->
            var tag = tagDao.getByName(name)
            if (tag == null) {
                val newId = tagDao.insert(Tag(name = name, createdAt = System.currentTimeMillis()))
                tag = if (newId != -1L) Tag(newId, name, 0) else tagDao.getByName(name)
            }
            if (tag != null) tagDao.insertCardTag(CardTag(cardId, tag.id))
        }
        invalidateTagCache()
    }

    // ---- 图片（卡片 assets 内相对引用）----

    /** 把 uri 指向的图片压缩（长边 1600px / JPEG 85）写入卡片 assets，返回相对引用名 */
    suspend fun insertImage(cardId: Long, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            // inJustDecodeBounds 时 decodeStream 恒返回 null（只填 bounds），判空对象必须是流而非解码结果
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.e(TAG, "insertImage: unreadable image uri=$uri")
                return@runCatching null
            }

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: run {
                Log.e(TAG, "insertImage: stream closed between passes uri=$uri")
                return@runCatching null
            }

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

            val assets = CardTextUtils.assetsDirFor(filesDir, cardId)
            assets.mkdirs()
            val target = File(assets, "${UUID.randomUUID()}.jpg")
            target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            bitmap.recycle()
            "${CardTextUtils.ASSETS_DIR_NAME}/${target.name}"
        }.onFailure {
            Log.e(TAG, "insertImage: card=$cardId uri=$uri", it)
        }.getOrNull()
    }

    /** 相对引用 → 物理文件（渲染 / 全屏查看） */
    fun assetFile(cardId: Long, relativeRef: String): File =
        File(CardTextUtils.cardDirFor(filesDir, cardId), relativeRef.removePrefix("./"))

    suspend fun deleteAsset(cardId: Long, relativeRef: String) = withContext(Dispatchers.IO) {
        assetFile(cardId, relativeRef).delete()
        Unit
    }

    // ---- 回收站 ----

    suspend fun moveToTrash(cardId: Long) {
        cardDao.setDeletedAt(cardId, System.currentTimeMillis(), System.currentTimeMillis())
        invalidateCache(cardId)
    }

    suspend fun restore(cardId: Long) {
        cardDao.setDeletedAt(cardId, null, System.currentTimeMillis())
        invalidateCache(cardId)
    }

    /** 彻底删除：行（级联 card_tags / review_records）+ 物理目录 */
    suspend fun purge(cardId: Long) = withContext(Dispatchers.IO) {
        cardDao.delete(cardId)
        CardTextUtils.cardDirFor(filesDir, cardId).deleteRecursively()
        invalidateCache(cardId)
    }

    suspend fun purgeAll() = withContext(Dispatchers.IO) {
        cardDao.getAll().forEach { card ->
            if (card.deletedAt != null) {
                cardDao.delete(card.id)
                CardTextUtils.cardDirFor(filesDir, card.id).deleteRecursively()
            }
        }
        contentCache.clear()
    }

    /** 删除满 30 天的回收站卡片自动清除（惰性触发） */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val threshold = now - CardTextUtils.TRASH_RETENTION_DAYS * 86_400_000L
        cardDao.getAll().filter { (it.deletedAt ?: Long.MAX_VALUE) < threshold }.forEach {
            cardDao.delete(it.id)
            CardTextUtils.cardDirFor(filesDir, it.id).deleteRecursively()
        }
        Unit
    }

    // ---- 标签管理 ----

    /** 重命名；目标名已存在则合并（关联迁移后被并标签删除） */
    suspend fun renameTag(tagId: Long, newName: String) = db.withTransaction {
        val name = newName.trim()
        if (name.isBlank()) return@withTransaction
        val conflict = tagDao.getByName(name)
        if (conflict != null && conflict.id != tagId) {
            tagDao.reassignCardTags(tagId, conflict.id)
            tagDao.delete(tagId)
        } else {
            tagDao.rename(tagId, name)
        }
        invalidateTagCache()
    }

    suspend fun deleteTag(tagId: Long) {
        tagDao.delete(tagId)
        invalidateTagCache()
    }

    // ---- 搜索（标题/摘要/来源快搜 + 正文文件兜底扫描，中文友好）----

    private val contentCache = HashMap<Long, String>()

    private fun invalidateCache(cardId: Long? = null) {
        if (cardId == null) contentCache.clear() else contentCache.remove(cardId)
    }

    private suspend fun allBodyContents(): Map<Long, String> = withContext(Dispatchers.IO) {
        if (contentCache.isEmpty()) {
            cardDao.getAll().filter { it.deletedAt == null }.forEach { card ->
                val f = CardTextUtils.noteFileFor(filesDir, card.id)
                if (f.exists()) contentCache[card.id] = f.readText()
            }
        }
        contentCache
    }

    suspend fun search(query: String): List<KnowledgeCard> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        // 一级：标题/摘要/来源；二级：标签；三级：正文（LIKE 兜底，卡片量级 10²–10³ 可接受）
        val meta = cardDao.searchMeta(q).associateBy { it.id }

        val all = cardDao.getAll().filter { it.deletedAt == null }
        val byId = all.associateBy { it.id }
        val tagHitIds = mutableSetOf<Long>()
        val bodyHitIds = mutableSetOf<Long>()
        val bodies = allBodyContents()
        all.forEach { card ->
            if (card.id in meta) return@forEach
            val names = tagNames(card.id)
            if (names.any { it.contains(q, ignoreCase = true) }) {
                tagHitIds += card.id
            } else if (bodies[card.id]?.contains(q, ignoreCase = true) == true) {
                bodyHitIds += card.id
            }
        }
        listOf(meta.values, tagHitIds.mapNotNull { byId[it] }, bodyHitIds.mapNotNull { byId[it] })
            .flatten()
    }

    private val tagNamesCache = HashMap<Long, List<String>>()

    suspend fun tagNames(cardId: Long): List<String> =
        tagNamesCache.getOrPut(cardId) { tagDao.tagNamesForCard(cardId) }

    fun invalidateTagCache() = tagNamesCache.clear()
}
