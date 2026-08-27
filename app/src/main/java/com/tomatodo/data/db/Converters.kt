package com.tomatodo.data.db

import androidx.room.TypeConverter
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.data.model.TaskStatus
import org.json.JSONArray

/** Room 枚举 <-> String 转换；v2 增加 tags 列表（存 JSON 文本） */
class Converters {
    @TypeConverter fun taskStatusToString(value: TaskStatus): String = value.name
    @TypeConverter fun stringToTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter fun pomodoroTypeToString(value: PomodoroType): String = value.name
    @TypeConverter fun stringToPomodoroType(value: String): PomodoroType = PomodoroType.valueOf(value)

    @TypeConverter fun cardTypeToString(value: CardType): String = value.name
    @TypeConverter fun stringToCardType(value: String): CardType = CardType.valueOf(value)

    @TypeConverter fun tagsToJson(tags: List<String>): String = JSONArray(tags).toString()

    @TypeConverter fun jsonToTags(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
