package com.tomatodo.data

import androidx.room.withTransaction
import com.tomatodo.data.db.TomaTodoDatabase
import com.tomatodo.data.model.CardTag
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.PomodoroSession
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.data.model.ReviewRecord
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Tag
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 全量数据导出/导入（PRD §F7；v1.1 ZIP 打包；KMS v1.2 打包 cards/ 目录）。
 *
 * 格式：backup.json + cards/{id}/note.md + cards/{id}/assets/ 下图片。
 * 兼容导入 v1.1.1 旧备份（front/back/tags JSON + images/ 目录），导入时自动文件化。
 */
class BackupManager(
    private val db: TomaTodoDatabase,
    private val filesDir: File
) {

    companion object {
        private const val SCHEMA_VERSION = 3
        private const val ZIP_PREFIX_CARDS = "cards/"
        private const val ZIP_PREFIX_IMAGES = "images/"
    }

    // ---- JSON 导出 / 导入 ----

    suspend fun export(): String = db.withTransaction {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("subjects", db.subjectDao().getAll().toJsonArray { it.toJson() })
        root.put("tasks", db.taskDao().getAll().toJsonArray { it.toJson() })
        root.put("pomodoroSessions", db.pomodoroSessionDao().getAll().toJsonArray { it.toJson() })
        root.put("knowledgeCards", db.knowledgeCardDao().getAll().toJsonArray { it.toJson() })
        root.put("tags", db.tagDao().getAll().toJsonArray { it.toJson() })
        root.put("cardTags", db.tagDao().getAllLinks().toJsonArray { it.toJson() })
        root.put("reviewRecords", db.reviewRecordDao().getAll().toJsonArray { it.toJson() })
        root.toString(2)
    }

    /**
     * 导入 JSON：v3 格式直接还原；旧版（无 schemaVersion）按 v2 字段解析并文件化。
     * 返回文件化的卡片目录数（供 ZIP 导入后写入物理文件）。
     */
    suspend fun import(json: String): Int = db.withTransaction {
        val root = JSONObject(json)
        val subjectDao = db.subjectDao()
        val taskDao = db.taskDao()
        val sessionDao = db.pomodoroSessionDao()
        val cardDao = db.knowledgeCardDao()
        val tagDao = db.tagDao()
        val recordDao = db.reviewRecordDao()

        recordDao.deleteAll()
        tagDao.clearAllCardTags()
        tagDao.deleteAll()
        cardDao.deleteAll()
        sessionDao.deleteAll()
        taskDao.deleteAll()
        subjectDao.deleteAll()

        subjectDao.insertAll(root.optJSONArray("subjects").parseArray { it.toSubject() })
        taskDao.insertAll(root.optJSONArray("tasks").parseArray { it.toTask() })
        sessionDao.insertAll(root.optJSONArray("pomodoroSessions").parseArray { it.toSession() })

        val version = root.optInt("schemaVersion", 2)
        var fileized = 0
        if (version >= 3) {
            cardDao.insertAll(root.optJSONArray("knowledgeCards").parseArray { it.toCard() })
            tagDao.insertAll(root.optJSONArray("tags").parseArray { it.toTag() })
            tagDao.insertCardTags(
                root.optJSONArray("cardTags").parseArray { it.toCardTag() }
            )
        } else {
            // v2 兼容：front/back/tags JSON → 知识卡片行 + note.md 文件
            data class LegacyImage(val cardId: Long, val filePath: String, val sortOrder: Int)
            val legacyImages = root.optJSONArray("cardImages").parseArray { obj ->
                LegacyImage(obj.optLong("cardId"), obj.optString("filePath"), obj.optInt("sortOrder"))
            }.groupBy { it.cardId }

            val cards = root.optJSONArray("knowledgeCards").parseArray { it.toLegacyCard() }
            cards.forEach { card ->
                val refs = legacyImages[card.id].orEmpty()
                    .sortedBy { it.sortOrder }
                    .map { img -> img.filePath.substringAfterLast('/') }
                val md = CardTextUtils.buildLegacyNote(card.front, card.back, refs)
                val dir = CardTextUtils.cardDirFor(filesDir, card.id)
                File(dir, CardTextUtils.ASSETS_DIR_NAME).mkdirs()
                File(dir, CardTextUtils.NOTE_FILE_NAME).writeText(md)
                fileized++
                cardDao.insert(
                    KnowledgeCard(
                        id = card.id,
                        title = CardTextUtils.deriveTitle(md),
                        excerpt = CardTextUtils.deriveExcerpt(md),
                        wordCount = CardTextUtils.deriveWordCount(md),
                        mdPath = CardTextUtils.mdPathFor(card.id),
                        deletedAt = null,
                        subjectId = card.subjectId,
                        type = card.type,
                        source = card.source,
                        masteryLevel = card.masteryLevel,
                        reviewCount = card.reviewCount,
                        nextReviewAt = card.nextReviewAt,
                        lastReviewedAt = card.lastReviewedAt,
                        createdAt = card.createdAt,
                        updatedAt = card.updatedAt
                    )
                )
            }
            // 标签 JSON → 标签表
            val nameToId = HashMap<String, Long>()
            cards.forEach { card ->
                card.tags.forEach { name ->
                    val tagId = nameToId.getOrPut(name) {
                        tagDao.insert(Tag(name = name, createdAt = System.currentTimeMillis()))
                    }
                    if (tagId != -1L) tagDao.insertCardTag(CardTag(card.id, tagId))
                }
            }
        }

        recordDao.insertAll(root.optJSONArray("reviewRecords").parseArray { it.toReviewRecord() })
        fileized
    }

    // ---- ZIP 导出 / 导入 ----

    /** ZIP 导出：backup.json + cards/（每卡 note.md + assets/） */
    suspend fun exportZip(output: java.io.OutputStream) = db.withTransaction {
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zip.write(export().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val cardsRoot = File(filesDir, "cards")
            cardsRoot.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val rel = file.relativeTo(filesDir).invariantSeparatorsPath
                    zip.putNextEntry(java.util.zip.ZipEntry(rel))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** ZIP 导入：还原 JSON + 恢复 cards/ 文件；旧版备份按 images/ 路径恢复并文件化 */
    suspend fun importZip(bytes: ByteArray) {
        var jsonText: String? = null
        val cardFiles = mutableListOf<Pair<String, ByteArray>>()   // 新格式：cards/ 路径
        val legacyImages = mutableListOf<Pair<String, ByteArray>>() // 旧格式：images/ 路径
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val payload = if (!entry.isDirectory) zip.readBytes() else null
                when {
                    name == "backup.json" && payload != null -> jsonText = payload.decodeToString()
                    name.startsWith(ZIP_PREFIX_CARDS) && payload != null -> cardFiles += name to payload
                    name.startsWith(ZIP_PREFIX_IMAGES) && payload != null -> legacyImages += name to payload
                }
                entry = zip.nextEntry
            }
        }
        val json = jsonText ?: return
        CardFileMigrator.migrateIfNeeded(db, filesDir)

        val isV3 = JSONObject(json).optInt("schemaVersion", 2) >= 3
        // 导入前清空卡片文件目录，避免旧文件残留
        if (isV3) File(filesDir, "cards").deleteRecursively()
        val fileized = import(json)
        if (isV3) {
            cardFiles.forEach { (name, bytesData) ->
                safeWrite(filesDir, name, bytesData)
            }
        } else {
            // 旧版：images/{name} → cards/{id}/assets/{name}
            val byCard = mutableMapOf<Long, MutableList<Pair<String, ByteArray>>>()
            JSONObject(json).optJSONArray("cardImages")?.let { arr ->
                (0 until arr.length()).forEach { i ->
                    val obj = arr.optJSONObject(i) ?: return@forEach
                    val cardId = obj.optLong("cardId")
                    val file = obj.optString("filePath").substringAfterLast('/')
                    legacyImages.firstOrNull { it.first.endsWith(file) }?.let {
                        byCard.getOrPut(cardId) { mutableListOf() }.add(file to it.second)
                    }
                }
            }
            byCard.forEach { (cardId, files) ->
                files.forEach { (name, data) ->
                    safeWrite(filesDir, "cards/$cardId/${CardTextUtils.ASSETS_DIR_NAME}/$name", data)
                }
            }
            if (fileized == 0 && byCard.isEmpty()) {
                // 无卡片数据，清掉新建的空目录
            }
        }
    }

    private fun safeWrite(filesDir: File, relative: String, data: ByteArray) {
        // 防路径穿越
        val target = File(filesDir, relative)
        if (!target.canonicalPath.startsWith(filesDir.canonicalPath)) return
        target.parentFile?.mkdirs()
        target.writeBytes(data)
    }
}

// ---- JSON 序列化 / 反序列化（文件级私有扩展）----

private inline fun <T> List<T>.toJsonArray(block: (T) -> JSONObject): JSONArray =
    JSONArray().also { arr -> forEach { arr.put(block(it)) } }

private inline fun <T> JSONArray?.parseArray(block: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(block) }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key)

private fun JSONObject.optNullableLong(key: String): Long? =
    if (isNull(key)) null else optLong(key)

private fun Subject.toJson() = JSONObject()
    .put("id", id).put("name", name).put("color", color)
    .put("isBuiltIn", isBuiltIn).put("sortOrder", sortOrder)

private fun Task.toJson() = JSONObject()
    .put("id", id).put("content", content).put("startTime", startTime)
    .put("endTime", endTime).put("subjectId", subjectId ?: JSONObject.NULL)
    .put("status", status.name).put("isCompleted", isCompleted)
    .put("pomodoroCount", pomodoroCount).put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun PomodoroSession.toJson() = JSONObject()
    .put("id", id).put("taskId", taskId ?: JSONObject.NULL)
    .put("subjectId", subjectId ?: JSONObject.NULL).put("type", type.name)
    .put("startAt", startAt).put("endAt", endAt)
    .put("plannedDuration", plannedDuration).put("actualDuration", actualDuration)

private fun KnowledgeCard.toJson() = JSONObject()
    .put("id", id).put("title", title).put("excerpt", excerpt)
    .put("wordCount", wordCount).put("mdPath", mdPath)
    .put("deletedAt", deletedAt ?: JSONObject.NULL)
    .put("subjectId", subjectId ?: JSONObject.NULL).put("type", type.name)
    .put("source", source ?: JSONObject.NULL)
    .put("masteryLevel", masteryLevel)
    .put("reviewCount", reviewCount).put("nextReviewAt", nextReviewAt)
    .put("lastReviewedAt", lastReviewedAt ?: JSONObject.NULL)
    .put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun Tag.toJson() = JSONObject()
    .put("id", id).put("name", name).put("createdAt", createdAt)

private fun CardTag.toJson() = JSONObject()
    .put("cardId", cardId).put("tagId", tagId)

private fun ReviewRecord.toJson() = JSONObject()
    .put("id", id).put("cardId", cardId).put("result", result).put("reviewedAt", reviewedAt)

private fun JSONObject.toSubject(): Subject = Subject(
    id = optLong("id"), name = optString("name"), color = optLong("color"),
    isBuiltIn = optBoolean("isBuiltIn"), sortOrder = optInt("sortOrder")
)

private fun JSONObject.toTask(): Task = Task(
    id = optLong("id"), content = optString("content"), startTime = optLong("startTime"),
    endTime = optLong("endTime"), subjectId = optNullableLong("subjectId"),
    status = TaskStatus.valueOf(optString("status")), isCompleted = optBoolean("isCompleted"),
    pomodoroCount = optInt("pomodoroCount"), createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt")
)

private fun JSONObject.toSession(): PomodoroSession = PomodoroSession(
    id = optLong("id"), taskId = optNullableLong("taskId"),
    subjectId = optNullableLong("subjectId"),
    type = PomodoroType.valueOf(optString("type")), startAt = optLong("startAt"),
    endAt = optLong("endAt"), plannedDuration = optLong("plannedDuration"),
    actualDuration = optLong("actualDuration")
)

private fun JSONObject.toCard(): KnowledgeCard = KnowledgeCard(
    id = optLong("id"), title = optString("title", "无标题"),
    excerpt = optString("excerpt"), wordCount = optInt("wordCount"),
    mdPath = optString("mdPath"), deletedAt = optNullableLong("deletedAt"),
    subjectId = optNullableLong("subjectId"), type = CardType.valueOf(optString("type")),
    source = optNullableString("source"),
    masteryLevel = optInt("masteryLevel"),
    reviewCount = optInt("reviewCount"), nextReviewAt = optLong("nextReviewAt"),
    lastReviewedAt = optNullableLong("lastReviewedAt"),
    createdAt = optLong("createdAt"), updatedAt = optLong("updatedAt")
)

private fun JSONObject.toTag(): Tag = Tag(
    id = optLong("id"), name = optString("name"), createdAt = optLong("createdAt")
)

private fun JSONObject.toCardTag(): CardTag = CardTag(
    cardId = optLong("cardId"), tagId = optLong("tagId")
)

private fun JSONObject.toReviewRecord(): ReviewRecord = ReviewRecord(
    id = optLong("id"), cardId = optLong("cardId"),
    result = optString("result"), reviewedAt = optLong("reviewedAt")
)

/** 旧版卡片（front/back/tags JSON），仅用于导入转换 */
private data class LegacyCard(
    val id: Long, val front: String, val back: String, val tags: List<String>,
    val subjectId: Long?, val type: CardType, val source: String?,
    val masteryLevel: Int, val reviewCount: Int, val nextReviewAt: Long,
    val lastReviewedAt: Long?, val createdAt: Long, val updatedAt: Long
)

private fun JSONObject.toLegacyCard(): LegacyCard = LegacyCard(
    id = optLong("id"), front = optString("front"), back = optString("back"),
    tags = runCatching {
        val arr = optJSONArray("tags")
        if (arr == null) emptyList()
        else (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList()),
    subjectId = optNullableLong("subjectId"),
    type = runCatching { CardType.valueOf(optString("type")) }.getOrDefault(CardType.KNOWLEDGE),
    source = optNullableString("source"),
    masteryLevel = optInt("masteryLevel"),
    reviewCount = optInt("reviewCount"), nextReviewAt = optLong("nextReviewAt"),
    lastReviewedAt = optNullableLong("lastReviewedAt"),
    createdAt = optLong("createdAt"), updatedAt = optLong("updatedAt")
)
