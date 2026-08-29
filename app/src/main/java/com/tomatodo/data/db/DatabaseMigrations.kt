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

    /**
     * v2 -> v3（KMS）：卡片正文文件化（front/back/tags 列退役 → legacy 影子表 + cards/{id}/note.md，
     * 文件落盘由启动时 CardFileMigrator 完成）。card_images 表退役（图片并入 md 相对引用）。
     *
     * 注意：SQLite 不支持 DROP COLUMN，需重建 knowledge_cards；重建前必须先摘除子表
     * review_records 的 FK（否则 DROP 父表触发隐式 DELETE 级联清空复习记录），card_images
     * 行会随级联清空，但已先复制进 legacy_card_images_v2。
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 影子表：保留旧正文 / 标签 / 图片映射，供启动时文件迁移器消费（完成后再删除）
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `legacy_cards_v2` (" +
                    "`id` INTEGER PRIMARY KEY, " +
                    "`front` TEXT NOT NULL, " +
                    "`back` TEXT NOT NULL, " +
                    "`tags` TEXT NOT NULL)"
            )
            db.execSQL("INSERT INTO legacy_cards_v2 (id, front, back, tags) SELECT id, front, back, tags FROM knowledge_cards")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `legacy_card_images_v2` (" +
                    "`id` INTEGER PRIMARY KEY, " +
                    "`cardId` INTEGER NOT NULL, " +
                    "`filePath` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO legacy_card_images_v2 (id, cardId, filePath, sortOrder, createdAt) SELECT id, cardId, filePath, sortOrder, createdAt FROM card_images")

            // 2. 摘除子表 review_records（复制-删除-稍后恢复）
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `review_records_tmp` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`cardId` INTEGER NOT NULL, " +
                    "`result` TEXT NOT NULL, " +
                    "`reviewedAt` INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO review_records_tmp (id, cardId, result, reviewedAt) SELECT id, cardId, result, reviewedAt FROM review_records")
            db.execSQL("DROP TABLE review_records")

            // 3. 重建 knowledge_cards（列结构须与 v3 实体完全一致）
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `knowledge_cards_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`excerpt` TEXT NOT NULL, " +
                    "`wordCount` INTEGER NOT NULL, " +
                    "`mdPath` TEXT NOT NULL, " +
                    "`deletedAt` INTEGER, " +
                    "`subjectId` INTEGER, " +
                    "`type` TEXT NOT NULL, " +
                    "`source` TEXT, " +
                    "`masteryLevel` INTEGER NOT NULL, " +
                    "`reviewCount` INTEGER NOT NULL, " +
                    "`nextReviewAt` INTEGER NOT NULL, " +
                    "`lastReviewedAt` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO knowledge_cards_new (id, title, excerpt, wordCount, mdPath, deletedAt, subjectId, type, source, masteryLevel, reviewCount, nextReviewAt, lastReviewedAt, createdAt, updatedAt) " +
                    "SELECT id, '', '', 0, '', NULL, subjectId, type, source, masteryLevel, reviewCount, nextReviewAt, lastReviewedAt, createdAt, updatedAt FROM knowledge_cards"
            )
            // card_images 旧行随级联清空（已备份），父表方可安全 DROP
            db.execSQL("DROP TABLE knowledge_cards")
            db.execSQL("ALTER TABLE knowledge_cards_new RENAME TO knowledge_cards")

            // 4. 恢复 review_records（FK 重新指向 knowledge_cards）
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `review_records` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`cardId` INTEGER NOT NULL, " +
                    "`result` TEXT NOT NULL, " +
                    "`reviewedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`cardId`) REFERENCES `knowledge_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("INSERT INTO review_records (id, cardId, result, reviewedAt) SELECT id, cardId, result, reviewedAt FROM review_records_tmp")
            db.execSQL("DROP TABLE review_records_tmp")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_records_cardId` ON `review_records` (`cardId`)")

            // 5. card_images 表退役
            db.execSQL("DROP TABLE IF EXISTS card_images")

            // 6. 标签体系
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tags` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `card_tags` (" +
                    "`cardId` INTEGER NOT NULL, " +
                    "`tagId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`cardId`, `tagId`), " +
                    "FOREIGN KEY(`cardId`) REFERENCES `knowledge_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_tags_tagId` ON `card_tags` (`tagId`)")
        }
    }

    val ALL = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3)
}
