package com.tomatodo.data

import androidx.room.withTransaction
import com.tomatodo.data.db.TomaTodoDatabase
import com.tomatodo.data.model.CardImage
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.PomodoroSession
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.data.model.ReviewRecord
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Task
import com.tomatodo.data.model.TaskStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 全量数据导出/导入（JSON，PRD §F7；v1.1 增加 ZIP 打包含图片，OPTIMIZATION §9 技术债 #4） */
class BackupManager(
    private val db: TomaTodoDatabase,
    private val filesDir: File
) {

    suspend fun export(): String = db.withTransaction {
        val root = JSONObject()
        root.put("subjects", db.subjectDao().getAll().toJsonArray { it.toJson() })
        root.put("tasks", db.taskDao().getAll().toJsonArray { it.toJson() })
        root.put("pomodoroSessions", db.pomodoroSessionDao().getAll().toJsonArray { it.toJson() })
        root.put("knowledgeCards", db.knowledgeCardDao().getAll().toJsonArray { it.toJson() })
        root.put("cardImages", db.cardImageDao().getAll().toJsonArray { it.toJson() })
        root.put("reviewRecords", db.reviewRecordDao().getAll().toJsonArray { it.toJson() })
        root.toString(2)
    }

    suspend fun import(json: String) = db.withTransaction {
        val root = JSONObject(json)
        val subjectDao = db.subjectDao()
        val taskDao = db.taskDao()
        val sessionDao = db.pomodoroSessionDao()
        val cardDao = db.knowledgeCardDao()
        val imageDao = db.cardImageDao()
        val recordDao = db.reviewRecordDao()

        recordDao.deleteAll()
        imageDao.deleteAll()
        cardDao.deleteAll()
        sessionDao.deleteAll()
        taskDao.deleteAll()
        subjectDao.deleteAll()

        subjectDao.insertAll(root.optJSONArray("subjects").parseArray { it.toSubject() })
        taskDao.insertAll(root.optJSONArray("tasks").parseArray { it.toTask() })
        sessionDao.insertAll(root.optJSONArray("pomodoroSessions").parseArray { it.toSession() })
        cardDao.insertAll(root.optJSONArray("knowledgeCards").parseArray { it.toCard() })
        imageDao.insertAll(root.optJSONArray("cardImages").parseArray { it.toImage() })
        recordDao.insertAll(root.optJSONArray("reviewRecords").parseArray { it.toReviewRecord() })
    }

    /** ZIP 导出：backup.json + images/ 目录（技术债 #4） */
    suspend fun exportZip(output: java.io.OutputStream) = db.withTransaction {
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zip.write(export().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            db.cardImageDao().getAll().forEach { img ->
                val file = java.io.File(filesDir, img.filePath)
                if (file.exists()) {
                    zip.putNextEntry(java.util.zip.ZipEntry(img.filePath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** ZIP 导入：还原 JSON 数据并恢复图片文件（须为 exportZip 的产物） */
    suspend fun importZip(bytes: ByteArray) {
        var jsonText: String? = null
        val images = mutableListOf<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "backup.json" -> jsonText = zip.readBytes().decodeToString()
                    entry.name.startsWith("images/") -> images += entry.name to zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        jsonText?.let { import(it) }
        images.forEach { (path, bytes) ->
            val target = java.io.File(filesDir, path)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
    }
}

// ---- JSON 序列化 / 反序列化（文件级私有扩展，避免 companion 作用域问题）----

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
    .put("id", id).put("front", front).put("back", back)
    .put("subjectId", subjectId ?: JSONObject.NULL).put("type", type.name)
    .put("source", source ?: JSONObject.NULL)
    .put("tags", org.json.JSONArray(tags))
    .put("masteryLevel", masteryLevel)
    .put("reviewCount", reviewCount).put("nextReviewAt", nextReviewAt)
    .put("lastReviewedAt", lastReviewedAt ?: JSONObject.NULL)
    .put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun ReviewRecord.toJson() = JSONObject()
    .put("id", id).put("cardId", cardId).put("result", result).put("reviewedAt", reviewedAt)

private fun CardImage.toJson() = JSONObject()
    .put("id", id).put("cardId", cardId).put("filePath", filePath)
    .put("sortOrder", sortOrder).put("createdAt", createdAt)

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
    id = optLong("id"), front = optString("front"), back = optString("back"),
    subjectId = optNullableLong("subjectId"), type = CardType.valueOf(optString("type")),
    source = optNullableString("source"),
    tags = runCatching {
        val arr = optJSONArray("tags")
        if (arr == null) emptyList()
        else (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList()),
    masteryLevel = optInt("masteryLevel"),
    reviewCount = optInt("reviewCount"), nextReviewAt = optLong("nextReviewAt"),
    lastReviewedAt = optNullableLong("lastReviewedAt"),
    createdAt = optLong("createdAt"), updatedAt = optLong("updatedAt")
)

private fun JSONObject.toReviewRecord(): ReviewRecord = ReviewRecord(
    id = optLong("id"), cardId = optLong("cardId"),
    result = optString("result"), reviewedAt = optLong("reviewedAt")
)

private fun JSONObject.toImage(): CardImage = CardImage(
    id = optLong("id"), cardId = optLong("cardId"), filePath = optString("filePath"),
    sortOrder = optInt("sortOrder"), createdAt = optLong("createdAt")
)
