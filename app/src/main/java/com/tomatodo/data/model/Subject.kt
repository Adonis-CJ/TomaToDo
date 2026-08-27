package com.tomatodo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 科目（PRD §5.1） */
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,          // 0xAARRGGBB，如 0xFF3F6B5F
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0
)
