package com.antgskds.calendarassistant.core.service.shortcut

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.core.util.AccessibilityGuardian
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
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

        lifecycleScope.launch {
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
                service.startAnalysis(delayDuration = 400.milliseconds, fromShortcut = true)
            }

            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保窗口完全移除
        overridePendingTransition(0, 0)
    }
}
