package com.tomatodo.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomatodo.TomaTodoApplication
import com.tomatodo.data.BackupManager
import com.tomatodo.data.model.Subject
import com.tomatodo.data.preferences.ThemeMode
import com.tomatodo.data.preferences.TimerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TomaTodoApplication).container
    private val prefs = container.settingsPreferences
    private val subjectDao = container.database.subjectDao()
    private val backupManager = BackupManager(container.database, application.filesDir)

    val settings: StateFlow<TimerSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerSettings())

    val subjects: StateFlow<List<Subject>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setFocus(m: Int) = viewModelScope.launch { prefs.setFocusMinutes(m) }
    fun setShort(m: Int) = viewModelScope.launch { prefs.setShortBreakMinutes(m) }
    fun setLong(m: Int) = viewModelScope.launch { prefs.setLongBreakMinutes(m) }
    fun setPomodoros(n: Int) = viewModelScope.launch { prefs.setPomodorosBeforeLongBreak(n) }
    fun setRingtone(id: String) = viewModelScope.launch { prefs.setRingtone(id) }
    fun setVolume(v: Float) = viewModelScope.launch { prefs.setVolume(v) }
    fun setVibrationOnly(v: Boolean) = viewModelScope.launch { prefs.setVibrationOnly(v) }
    fun setThemeMode(m: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(m) }

    /** 沉浸模式切换（翻页钟 / 背景图时钟） */
    fun setImmersionMode(mode: com.tomatodo.data.preferences.ImmersionMode) =
        viewModelScope.launch { prefs.setImmersionMode(mode) }

    /** 上传沉浸背景图：压缩拷贝到内部存储并记录路径 */
    fun setWallpaper(uri: Uri?) = viewModelScope.launch(Dispatchers.IO) {
        if (uri == null) {
            prefs.setWallpaperPath(null)
            return@launch
        }
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val target = java.io.File(getApplication<Application>().filesDir, "wallpaper.jpg")
            resolver.openInputStream(uri)?.use { input ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                    ?: return@launch
                // 长边压到 2000px，控制体积
                val maxDim = maxOf(bitmap.width, bitmap.height)
                val scaled = if (maxDim > 2000) {
                    val scale = 2000f / maxDim
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else bitmap
                target.outputStream().use { out ->
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                }
            }
            prefs.setWallpaperPath("wallpaper.jpg")
        }
    }

    fun addSubject(name: String, color: Long) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        subjectDao.upsert(
            Subject(name = name.trim(), color = color, isBuiltIn = false, sortOrder = 100)
        )
    }

    fun deleteSubject(subject: Subject) = viewModelScope.launch {
        if (!subject.isBuiltIn) subjectDao.delete(subject.id)
    }

    /** ZIP 备份导出（含图片文件） */
    fun exportTo(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                backupManager.exportZip(out)
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    /** 导入：按文件头自动识别 ZIP（新）或纯 JSON（旧） */
    fun importFrom(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { it.readBytes() } ?: return@launch
            val isZip = bytes.size >= 2 &&
                bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
            if (isZip) backupManager.importZip(bytes)
            else backupManager.import(bytes.decodeToString())
        } catch (_: Exception) {
            // ignore
        }
    }
}
