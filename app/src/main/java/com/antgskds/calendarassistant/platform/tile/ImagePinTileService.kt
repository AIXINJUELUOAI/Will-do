package com.antgskds.calendarassistant.platform.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.imagepin.ImagePinPolicy
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.shared.util.AccessibilityGuardian
import kotlinx.coroutines.*

/** 用户主动点击后截图追加到图片挂起，截图任务由无障碍服务持有。 */
class ImagePinTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clickJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "截图挂起"
            icon = Icon.createWithResource(this@ImagePinTileService, R.drawable.ic_stat_image_pin)
            // 权限不足仍可点击并获得提示。
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { capture() } else capture()
    }

    private fun capture() {
        if (clickJob?.isActive == true) return
        val app = applicationContext as App
        ImagePinPolicy.captureBlockReason(this, app.settingsQueryApi.settings.value)?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            return
        }
        clickJob = scope.launch {
            try {
                if (TextAccessibilityService.instance == null) AccessibilityGuardian.restoreIfNeeded(this@ImagePinTileService)
                val service = TextAccessibilityService.instance
                if (service != null) {
                    service.startImagePinCapture()
                } else {
                    Toast.makeText(this@ImagePinTileService, "请开启无障碍服务，再回到目标页面点击截图挂起", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    if (Build.VERSION.SDK_INT >= 34) {
                        startActivityAndCollapse(PendingIntent.getActivity(this@ImagePinTileService, 0, intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(intent)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Toast.makeText(this@ImagePinTileService, "截图服务暂不可用，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
