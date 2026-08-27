package com.tomatodo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tomatodo.data.model.CardImage
import com.tomatodo.data.model.KnowledgeCard
import com.tomatodo.data.model.PomodoroSession
import com.tomatodo.data.model.Subject
import com.tomatodo.data.model.Task

@Database(
    entities = [
        Subject::class,
        Task::class,
        PomodoroSession::class,
        KnowledgeCard::class,
        CardImage::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TomaTodoDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun taskDao(): TaskDao
    abstract fun pomodoroSessionDao(): PomodoroSessionDao
    abstract fun knowledgeCardDao(): KnowledgeCardDao
    abstract fun cardImageDao(): CardImageDao
}
