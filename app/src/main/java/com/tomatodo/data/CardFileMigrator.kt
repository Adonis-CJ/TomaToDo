package com.tomatodo.data

import android.util.Log
import androidx.room.withTransaction
import com.tomatodo.data.db.TomaTodoDatabase
import java.io.File

/**
 * KMS v1.2 文件迁移器（配合 DatabaseMigrations.MIGRATION_2_3）：
 * 把 legacy_cards_v2 / legacy_card_images_v2 影子表中的旧正文与图片落盘为
 * `cards/{id}/note.md` + `assets/`，回填 title/excerpt/wordCount/mdPath 索引后删除影子表。
 *
 * 幂等：影子表存在为迁移标记；中断后重跑会覆盖 note.md 并按需补拷图片，无重复副作用。
 * 旧文件 `images/` 原样保留（复制而非移动），见升级文档 §10 Q2。
 */
object CardFileMigrator {

    private const val TAG = "CardFileMigrator"

    suspend fun migrateIfNeeded(db: TomaTodoDatabase, filesDir: File) {
        val legacyExists = db.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type='table' AND name='legacy_cards_v2'")
            .use { it.moveToFirst() }
        if (!legacyExists) return

        Log.i(TAG, "legacy cards detected, migrating to note.md files...")
        db.withTransaction {
            val raw = db.openHelper.writableDatabase

            data class Row(val id: Long, val front: String, val back: String)
            val rows = mutableListOf<Row>()
            raw.query("SELECT id, front, back FROM legacy_cards_v2").use { c ->
                while (c.moveToNext()) rows += Row(c.getLong(0), c.getString(1), c.getString(2))
            }
            val imagesByCard = mutableMapOf<Long, MutableList<String>>()
            raw.query("SELECT cardId, filePath FROM legacy_card_images_v2 ORDER BY cardId, sortOrder").use { c ->
                while (c.moveToNext()) {
                    imagesByCard.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getString(1))
                }
            }

            rows.forEach { row ->
                val dir = CardTextUtils.cardDirFor(filesDir, row.id)
                val assets = CardTextUtils.assetsDirFor(filesDir, row.id)
                assets.mkdirs()

                val refs = imagesByCard[row.id].orEmpty().map { rel ->
                    val src = File(filesDir, rel)
                    val dst = File(assets, src.name)
                    if (src.exists() && !dst.exists()) {
                        runCatching { src.copyTo(dst) }
                            .onFailure { Log.w(TAG, "copy image failed: $rel", it) }
                    }
                    "${CardTextUtils.ASSETS_DIR_NAME}/${src.name}"
                }

                val md = CardTextUtils.buildLegacyNote(row.front, row.back, refs)
                val note = File(dir, CardTextUtils.NOTE_FILE_NAME)
                val tmp = File(dir, "${CardTextUtils.NOTE_FILE_NAME}.tmp")
                dir.mkdirs()
                tmp.writeText(md)
                if (!tmp.renameTo(note)) {
                    // 极端文件系统不支持覆盖 rename
                    note.delete()
                    tmp.renameTo(note)
                }

                raw.execSQL(
                    "UPDATE knowledge_cards SET title = ?, excerpt = ?, wordCount = ?, mdPath = ? WHERE id = ?",
                    arrayOf(
                        CardTextUtils.deriveTitle(md),
                        CardTextUtils.deriveExcerpt(md),
                        CardTextUtils.deriveWordCount(md),
                        CardTextUtils.mdPathFor(row.id),
                        row.id
                    )
                )
            }

            raw.execSQL("DROP TABLE IF EXISTS legacy_cards_v2")
            raw.execSQL("DROP TABLE IF EXISTS legacy_card_images_v2")
        }
        Log.i(TAG, "file migration done")
    }
}
