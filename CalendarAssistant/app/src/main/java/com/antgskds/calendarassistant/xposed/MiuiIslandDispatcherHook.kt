package com.antgskds.calendarassistant.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 在 SystemUI 进程中注册 MiuiIslandDispatcher。
 */
class MiuiIslandDispatcherHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? android.app.Application ?: return
                        MiuiIslandDispatcher.register(app)
                    }
                }
            )
            XposedBridge.log("CalendarAssistant[MiuiDispatcherHook]: hooked Application.onCreate")
        } catch (e: Throwable) {
            XposedBridge.log("CalendarAssistant[MiuiDispatcherHook]: hook failed: ${e.message}")
        }
    }
}
