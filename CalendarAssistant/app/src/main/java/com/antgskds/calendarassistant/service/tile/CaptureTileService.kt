package com.antgskds.calendarassistant.service.tile

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import kotlin.time.Duration.Companion.milliseconds

/**
 * 快捷设置磁贴服务
 *
 * 职责：用户点击通知栏磁贴时触发，调用 [TextAccessibilityService] 进行截图分析。
 */
class CaptureTileService : TileService() {

    private val TAG = "CaptureTileDebug"

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return

        tile.state = Tile.STATE_INACTIVE
        tile.label = "识别事件"

        // 尝试使用专用图标，如果没有则回退
        try {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_recognition)
        } catch (e: Exception) {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        Log.e(TAG, ">>> 磁贴被点击了! <<<")

        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }

        // 获取无障碍服务实例 (静态变量)
        val service = TextAccessibilityService.instance

        if (service != null) {
            Log.d(TAG, "无障碍服务实例存在，准备调用 startAnalysis")

            // 1. 收起通知栏 (必须先做，否则截图全是通知栏)
            service.closeNotificationPanel()

            // 2. 开始分析 (传递延迟时间)
            service.startAnalysis(500.milliseconds)

            if (tile != null) {
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            }
        } else {
            Log.e(TAG, "无障碍服务实例为 NULL，发送提示通知")
            sendEnableServiceNotification()

            if (tile != null) {
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            }
        }
    }

    private fun sendEnableServiceNotification() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 使用 App 中定义的全局 Channel ID
        val notification = NotificationCompat.Builder(this, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("服务未开启")
            .setContentText("点击此处前往设置开启“无障碍服务”以使用识别功能")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2001, notification)
    }
}