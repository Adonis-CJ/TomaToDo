package com.tomatodo.ui.theme

import androidx.compose.ui.graphics.Color

// 「墨 · 纸」暖色设计系统（详见 PRD §6.2）
// 刻意避开冷色蓝紫，采用暖纸白底 + 墨色文字 + 朱砂单一强调色。

// 基础令牌
val PaperWhite = Color(0xFFF6F3EC)      // bg 纸白
val PaperCard = Color(0xFFFDFCF8)       // surface 纸卡
val Ink = Color(0xFF2B2A26)             // ink 墨
val InkMuted = Color(0xFF7A766B)        // ink-muted 灰墨
val Line = Color(0xFFE8E3D8)            // line 浅描边
val Cinnabar = Color(0xFFB4553A)        // accent 朱砂
val PineGreen = Color(0xFF4A6B5D)       // success 松绿
val Ochre = Color(0xFFA8893C)           // warn 赭黄

// 科目标识色（暖色、去蓝紫）
val SubjectDS = Color(0xFF3F6B5F)       // 数据结构 青绿
val SubjectCO = Color(0xFFB05C42)       // 计算机组成原理 陶土
val SubjectOS = Color(0xFFA8893C)       // 操作系统 赭黄
val SubjectCN = Color(0xFF6E7A3F)       // 计算机网络 橄榄
val SubjectMath = Color(0xFF7A5C45)     // 数学 墨褐
val SubjectEnglish = Color(0xFF8A857C)  // 英语 灰墨
val SubjectPolitics = Color(0xFFB08D6A) // 政治 砂金

// 深色模式（暖黑底 + 米白字）
val WarmDark = Color(0xFF1E1C19)        // 暖黑背景
val WarmDarkSurface = Color(0xFF262421) // 深色卡片
val WarmIvory = Color(0xFFEDE8DD)       // 米白文字
