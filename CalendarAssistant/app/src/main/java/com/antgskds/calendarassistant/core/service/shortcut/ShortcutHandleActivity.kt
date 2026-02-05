package com.antgskds.calendarassistant.core.service.shortcut

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
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

        // 检查无障碍服务是否开启
        val service = TextAccessibilityService.instance
        if (service == null) {
            // 服务未开启，跳转到无障碍设置页面
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
            overridePendingTransition(0, 0)
            return
        }

        // 服务已开启，触发截图分析
        // 延迟 400ms 给透明 Activity 销毁和侧滑动画消失的时间，确保截图画面纯净
        service.startAnalysis(delayDuration = 400.milliseconds, fromShortcut = true)

        // 立即销毁当前 Activity
        finish()
        // 去除退场动画
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保窗口完全移除
        overridePendingTransition(0, 0)
    }
}
