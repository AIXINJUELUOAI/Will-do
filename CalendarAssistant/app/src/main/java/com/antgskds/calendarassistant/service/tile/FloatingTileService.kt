package com.antgskds.calendarassistant.service.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.service.floating.FloatingScheduleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 悬浮窗快捷磁贴服务
 *
 * 职责：用户点击通知栏磁贴时收起控制中心并显示悬浮窗。
 */
class FloatingTileService : TileService() {

    private val TAG = "FloatingTileDebug"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return

        tile.state = Tile.STATE_INACTIVE
        tile.label = "悬浮窗"

        // 使用专用图标（需要后续添加 ic_qs_floating.xml）
        try {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_floating)
        } catch (e: Exception) {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, ">>> 悬浮窗磁贴被点击了! <<<")

        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }

        serviceScope.launch {
            // 获取无障碍服务实例（用于关闭控制中心）
            val service = TextAccessibilityService.instance

            if (service != null) {
                Log.d(TAG, "无障碍服务实例存在，关闭控制中心并启动悬浮窗")

                // 关闭控制中心
                service.closeNotificationPanel()

                // 等待控制中心收起动画完成
                delay(350.milliseconds)

                // 启动悬浮窗服务
                val intent = Intent(this@FloatingTileService, FloatingScheduleService::class.java)
                startService(intent)
            } else {
                Log.w(TAG, "无障碍服务实例为 NULL，仅启动悬浮窗（控制中心可能不会自动收起）")

                // 即使无障碍服务不可用，也尝试启动悬浮窗
                val intent = Intent(this@FloatingTileService, FloatingScheduleService::class.java)
                startService(intent)
            }

            // 恢复磁贴状态
            if (tile != null) {
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            }
        }
    }
}
