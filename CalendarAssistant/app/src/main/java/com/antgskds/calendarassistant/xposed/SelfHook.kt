package com.antgskds.calendarassistant.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook 模块自身进程：将 XposedModuleStatus.isActive() 替换为 true，
 * 使 App 可检测到模块已激活。
 */
class SelfHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.antgskds.calendarassistant") return
        try {
            val statusClass = lpparam.classLoader
                .loadClass("com.antgskds.calendarassistant.xposed.XposedModuleStatus")
            XposedHelpers.findAndHookMethod(
                statusClass,
                "isActive",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
            XposedBridge.log("CalendarAssistant[SelfHook]: XposedModuleStatus.isActive hooked")
        } catch (e: Throwable) {
            XposedBridge.log("CalendarAssistant[SelfHook]: hook failed: ${e.message}")
        }
    }
}
