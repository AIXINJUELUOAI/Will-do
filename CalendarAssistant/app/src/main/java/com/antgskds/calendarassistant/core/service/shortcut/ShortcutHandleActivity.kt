package com.antgskds.calendarassistant.core.service.shortcut

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.platform.floating.FloatingScheduleService
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.core.util.AccessibilityGuardian
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 快捷方式中转 Activity
 * 作用：接收快捷方式 Intent，触发无障碍服务截图分析，然后立即销毁
 * 特点：透明、无动画、独立任务栈，不会影响当前应用的使用体验
 */
class ShortcutHandleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 去除入场动画
        overridePendingTransition(0, 0)

        when (intent?.action) {
            ACTION_OPEN_FLOATING -> {
                openFloatingWindow(FloatingScheduleService.INPUT_MODE_SCHEDULE)
                finishWithoutAnimation()
            }
            ACTION_OPEN_FLOATING_NOTE -> {
                openFloatingWindow(FloatingScheduleService.INPUT_MODE_NOTE)
                finishWithoutAnimation()
            }
            else -> lifecycleScope.launch { runQuickCapture() }
        }
    }

    private suspend fun runQuickCapture() {
        var service = TextAccessibilityService.instance
        if (service == null) {
            AccessibilityGuardian.restoreIfNeeded(this@ShortcutHandleActivity)
            service = TextAccessibilityService.instance
        }

        if (service == null) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } else {
            val delayMs = (applicationContext as App)
                .settingsQueryApi
                .settings
                .value
                .screenshotDelayMs
                .let(MySettings::normalizeScreenshotDelayMs)
            service.startAnalysis(delayDuration = delayMs.milliseconds, fromShortcut = true)
        }

        finishWithoutAnimation()
    }

    private fun openFloatingWindow(initialInputMode: String) {
        val app = applicationContext as App
        if (!app.permissionCenter.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            return
        }

        startService(Intent(this, FloatingScheduleService::class.java).apply {
            putExtra(FloatingScheduleService.EXTRA_INITIAL_INPUT_MODE, initialInputMode)
        })
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保窗口完全移除
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_QUICK_CAPTURE = "com.antgskds.calendarassistant.shortcut.QUICK_CAPTURE"
        const val ACTION_OPEN_FLOATING = "com.antgskds.calendarassistant.shortcut.OPEN_FLOATING"
        const val ACTION_OPEN_FLOATING_NOTE = "com.antgskds.calendarassistant.shortcut.OPEN_FLOATING_NOTE"
    }
}
