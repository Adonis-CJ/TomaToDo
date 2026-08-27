package com.tomatodo.data

import android.content.Context
import androidx.room.Room
import com.tomatodo.data.db.TomaTodoDatabase
import com.tomatodo.data.preferences.SettingsPreferences

/** 手动依赖注入容器（PRD §7 未采用 Hilt，保持简单） */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: TomaTodoDatabase by lazy {
        Room.databaseBuilder(appContext, TomaTodoDatabase::class.java, "tomatodo.db").build()
    }

    val settingsPreferences: SettingsPreferences by lazy {
        SettingsPreferences(appContext)
    }
}
