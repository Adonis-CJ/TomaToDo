package com.tomatodo.ui

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 考研目标日（可在设置中配置，此处先定常量） */
val EXAM_DATE: LocalDate = LocalDate.of(2026, 12, 19)

/** 距考研还剩天数（含今天） */
fun daysToExam(): Long =
    ChronoUnit.DAYS.between(LocalDate.now(), EXAM_DATE).coerceAtLeast(0)

/** 看板寄语（按日期轮换） */
private val MOTTOS = listOf(
    "日拱一卒，功不唐捐。",
    "慢慢来，比较快。",
    "你所向往的，都在更高处。",
    "种一棵树最好的时间是十年前，其次是现在。",
    "乾坤未定，你我皆是黑马。",
    "不积跬步，无以至千里。",
    "坚持的意义，是你在低谷仍向上走。",
    "但行好事，莫问前程。",
    "此刻打盹，你将做梦；此刻学习，你将圆梦。",
    "书山有路勤为径，学海无涯苦作舟。"
)

/** 今日寄语 */
fun todayMotto(): String = MOTTOS[
    (LocalDate.now().dayOfYear % MOTTOS.size).toInt()
]
