package com.tomatodo.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 沉浸模式（OPTIMIZATION v1.1 用户新增）：翻页钟 / 背景图时钟 */
enum class ImmersionMode { FLIP, PHOTO }

/** 番茄钟 + 通知 + 主题设置（PRD §5.3） */
data class TimerSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val pomodorosBeforeLongBreak: Int = 4,
    val ringtoneId: String = "default",
    val volume: Float = 0.6f,
    val vibrationOnly: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val immersionMode: ImmersionMode = ImmersionMode.FLIP,
    val wallpaperPath: String? = null,
    val motto: String? = null
)

class SettingsPreferences(private val context: Context) {

    private object Keys {
        val FOCUS = intPreferencesKey("focus_minutes")
        val SHORT_BREAK = intPreferencesKey("short_break_minutes")
        val LONG_BREAK = intPreferencesKey("long_break_minutes")
        val POMODOROS = intPreferencesKey("pomodoros_before_long_break")
        val RINGTONE = stringPreferencesKey("ringtone_id")
        val VOLUME = floatPreferencesKey("volume")
        val VIBRATION_ONLY = booleanPreferencesKey("vibration_only")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val IMMERSION_MODE = stringPreferencesKey("immersion_mode")
        val WALLPAPER_PATH = stringPreferencesKey("wallpaper_path")
        val MOTTO = stringPreferencesKey("motto")
    }

    val settings: Flow<TimerSettings> = context.dataStore.data.map { prefs ->
        TimerSettings(
            focusMinutes = prefs[Keys.FOCUS] ?: 25,
            shortBreakMinutes = prefs[Keys.SHORT_BREAK] ?: 5,
            longBreakMinutes = prefs[Keys.LONG_BREAK] ?: 15,
            pomodorosBeforeLongBreak = prefs[Keys.POMODOROS] ?: 4,
            ringtoneId = prefs[Keys.RINGTONE] ?: "default",
            volume = prefs[Keys.VOLUME] ?: 0.6f,
            vibrationOnly = prefs[Keys.VIBRATION_ONLY] ?: false,
            themeMode = ThemeMode.entries.getOrElse(prefs[Keys.THEME_MODE] ?: 0) { ThemeMode.SYSTEM },
            immersionMode = runCatching {
                ImmersionMode.valueOf(prefs[Keys.IMMERSION_MODE] ?: ImmersionMode.FLIP.name)
            }.getOrDefault(ImmersionMode.FLIP),
            wallpaperPath = prefs[Keys.WALLPAPER_PATH],
            motto = prefs[Keys.MOTTO]
        )
    }

    val motto: Flow<String?> = context.dataStore.data.map { prefs -> prefs[Keys.MOTTO] }

    suspend fun setFocusMinutes(value: Int) = edit { it[Keys.FOCUS] = value }
    suspend fun setShortBreakMinutes(value: Int) = edit { it[Keys.SHORT_BREAK] = value }
    suspend fun setLongBreakMinutes(value: Int) = edit { it[Keys.LONG_BREAK] = value }
    suspend fun setPomodorosBeforeLongBreak(value: Int) = edit { it[Keys.POMODOROS] = value }
    suspend fun setRingtone(value: String) = edit { it[Keys.RINGTONE] = value }
    suspend fun setVolume(value: Float) = edit { it[Keys.VOLUME] = value }
    suspend fun setVibrationOnly(value: Boolean) = edit { it[Keys.VIBRATION_ONLY] = value }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.ordinal }
    suspend fun setImmersionMode(value: ImmersionMode) = edit { it[Keys.IMMERSION_MODE] = value.name }
    suspend fun setWallpaperPath(value: String?) = edit {
        if (value == null) it.remove(Keys.WALLPAPER_PATH) else it[Keys.WALLPAPER_PATH] = value
    }
    suspend fun setMotto(value: String?) = edit {
        if (value.isNullOrBlank()) it.remove(Keys.MOTTO) else it[Keys.MOTTO] = value.trim()
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
