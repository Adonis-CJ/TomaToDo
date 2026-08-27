package com.tomatodo.data

import com.tomatodo.data.model.Subject

/** 预置科目（PRD §2.4，暖色系） */
fun defaultSubjects(): List<Subject> = listOf(
    Subject(name = "数据结构", color = 0xFF3F6B5F, isBuiltIn = true, sortOrder = 0),
    Subject(name = "计算机组成原理", color = 0xFFB05C42, isBuiltIn = true, sortOrder = 1),
    Subject(name = "操作系统", color = 0xFFA8893C, isBuiltIn = true, sortOrder = 2),
    Subject(name = "计算机网络", color = 0xFF6E7A3F, isBuiltIn = true, sortOrder = 3),
    Subject(name = "数学", color = 0xFF7A5C45, isBuiltIn = true, sortOrder = 4),
    Subject(name = "英语", color = 0xFF8A857C, isBuiltIn = true, sortOrder = 5),
    Subject(name = "政治", color = 0xFFB08D6A, isBuiltIn = true, sortOrder = 6)
)
