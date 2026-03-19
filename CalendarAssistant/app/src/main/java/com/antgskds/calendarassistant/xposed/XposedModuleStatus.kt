package com.antgskds.calendarassistant.xposed

/**
 * Xposed 模块激活状态。
 * 默认返回 false，由 LSPosed/Xposed 在模块进程内 Hook 为 true。
 */
object XposedModuleStatus {
    @JvmStatic
    fun isActive(): Boolean = false
}
