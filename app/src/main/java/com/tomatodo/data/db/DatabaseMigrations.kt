package com.tomatodo.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** DB 版本迁移（schema JSON 见 app/schemas/） */
object DatabaseMigrations {

    /** v1 -> v2：卡片 tags/updatedAt；会话 subjectId；新表 review_records */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE knowledge_cards ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE knowledge_cards ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE knowledge_cards SET updatedAt = createdAt")
            db.execSQL("ALTER TABLE pomodoro_sessions ADD COLUMN subjectId INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `review_records` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`cardId` INTEGER NOT NULL, " +
                    "`result` TEXT NOT NULL, " +
                    "`reviewedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`cardId`) REFERENCES `knowledge_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_records_cardId` ON `review_records` (`cardId`)")
        }
    }

    val ALL = arrayOf<Migration>(MIGRATION_1_2)
}
