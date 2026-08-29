package com.tomatodo.data

import android.content.Context
import androidx.room.Room
import com.tomatodo.data.db.DatabaseMigrations
import com.tomatodo.data.db.TomaTodoDatabase
import com.tomatodo.data.preferences.SettingsPreferences

/** 手动依赖注入容器（PRD §7 未采用 Hilt，保持简单） */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: TomaTodoDatabase by lazy {
        Room.databaseBuilder(appContext, TomaTodoDatabase::class.java, "tomatodo.db")
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
    }

    val settingsPreferences: SettingsPreferences by lazy {
        SettingsPreferences(appContext)
    }

    /** 知识卡片仓库（KMS v1.2 文件+索引唯一写入口） */
    val cardRepository: CardRepository by lazy {
        CardRepository(appContext, database)
    }
}
