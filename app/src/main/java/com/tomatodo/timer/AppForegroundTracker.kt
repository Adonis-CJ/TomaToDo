package com.tomatodo.timer

/** 应用前台/后台标记（MainActivity onStart/onStop 维护），用于区分完成提醒策略 */
object AppForegroundTracker {
    @Volatile
    var isForeground: Boolean = true
}
