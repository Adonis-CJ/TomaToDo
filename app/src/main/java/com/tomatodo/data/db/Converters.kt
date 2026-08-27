package com.tomatodo.data.db

import androidx.room.TypeConverter
import com.tomatodo.data.model.CardType
import com.tomatodo.data.model.PomodoroType
import com.tomatodo.data.model.TaskStatus

/** Room 枚举 <-> String 转换 */
class Converters {
    @TypeConverter fun taskStatusToString(value: TaskStatus): String = value.name
    @TypeConverter fun stringToTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter fun pomodoroTypeToString(value: PomodoroType): String = value.name
    @TypeConverter fun stringToPomodoroType(value: String): PomodoroType = PomodoroType.valueOf(value)

    @TypeConverter fun cardTypeToString(value: CardType): String = value.name
    @TypeConverter fun stringToCardType(value: String): CardType = CardType.valueOf(value)
}
