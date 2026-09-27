package com.antgskds.calendarassistant.platform.entry.image

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.imagepin.ImagePinPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 分享、选图、岛上点击共用的透明入口，权限返回后再次检查，不拉起应用主页面。 */
class ImagePinHandleActivity : ComponentActivity() {
    private val app get() = application as App
    private var imageUris: List<Uri> = emptyList()
    private var running = false
    private var awaitingResult = false
    private val picker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        awaitingResult = false
        imageUris = uris
        if (uris.isEmpty()) finish() else proceed()
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        awaitingResult = false
        if (Settings.canDrawOverlays(this)) proceed() else stop("查看图片需要悬浮窗权限")
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        awaitingResult = false
        if (granted) proceed() else stop("图片挂起需要通知权限")
    }
    private val notificationSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        awaitingResult = false
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) proceed() else stop("图片挂起需要通知权限")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (!ImagePinPolicy.enabled(app.settingsQueryApi.settings.value)) { stop("请先在实验室开启图片挂起"); return }
        imageUris = savedInstanceState?.getParcelableArrayList<Uri>("image_uris").orEmpty()
        awaitingResult = savedInstanceState?.getBoolean("awaiting_result") == true
        if (awaitingResult) return // 选择器或权限页的结果由 ActivityResultRegistry 接回。
        if (imageUris.isNotEmpty()) { proceed(); return }
        when (intent.action) {
            ACTION_PICK -> { awaitingResult = true; picker.launch("image/*") }
            ACTION_VIEW -> proceed()
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE)
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                imageUris = streams.ifEmpty {
                    intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
                }.distinct()
                if (imageUris.isEmpty()) stop("未找到图片") else proceed()
            }
            else -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelableArrayList("image_uris", ArrayList(imageUris))
        outState.putBoolean("awaiting_result", awaitingResult)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun proceed() {
        if (running) return
        val settings = app.settingsQueryApi.settings.value
        if (!ImagePinPolicy.enabled(settings)) { stop("图片挂起已关闭"); return }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请允许悬浮窗权限，以便点击岛查看图片", Toast.LENGTH_LONG).show()
            awaitingResult = true
            overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        if (intent.action != ACTION_VIEW) {
            if (!ImagePinPolicy.canPublish(settings)) { stop("请先开启实况通知"); return }
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                awaitingResult = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); return
            }
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                awaitingResult = true
                notificationSettings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)); return
            }
            val channel = getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(App.CHANNEL_ID_LIVE)
            if (channel?.importance == android.app.NotificationManager.IMPORTANCE_NONE) { stop("请先开启实况通知渠道"); return }
        }
        running = true
        lifecycleScope.launch {
            try {
                if (intent.action == ACTION_VIEW) {
                    val id = intent.getLongExtra(EXTRA_ID, 0)
                    val images = app.imagePinController.currentImages(id)
                    check(images.isNotEmpty()) { "图片已结束挂起或已更新，请重新点击胶囊" }
                    check(app.floatingCenter.startImagePinMediaCard(id, images.map { it.absolutePath })) { "图片弹窗无法打开" }
                } else {
                    app.imagePinController.pin(imageUris)
                    Toast.makeText(this@ImagePinHandleActivity, "图片已挂起", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Toast.makeText(this@ImagePinHandleActivity, e.message ?: "图片挂起失败", Toast.LENGTH_LONG).show()
            } finally { if (!isChangingConfigurations) finish() }
        }
    }

    private fun stop(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); finish() }

    companion object {
        const val ACTION_PICK = "com.antgskds.calendarassistant.image_pin.PICK"
        const val ACTION_VIEW = "com.antgskds.calendarassistant.image_pin.VIEW"
        const val EXTRA_ID = "image_pin_id"
        fun shortcut(context: Context) = ShortcutInfoCompat.Builder(context, "image_pin")
            .setShortLabel("图片挂起").setLongLabel("选择图片并挂起在岛上")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_share_image_pin))
            .setIntent(Intent(context, ImagePinHandleActivity::class.java).setAction(ACTION_PICK))
            .setRank(1).build()
        fun viewPendingIntent(context: Context, id: Long): PendingIntent = PendingIntent.getActivity(context,
            id.hashCode(), Intent(context, ImagePinHandleActivity::class.java).setAction(ACTION_VIEW)
                .setData(Uri.parse("willdo-image-pin:" + id)).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
