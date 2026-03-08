package com.antgskds.calendarassistant.ui.page_display.settings

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.service.capsule.LiveProbeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LaboratoryPage(uiSize: Int = 2) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = (context.applicationContext as App).repository
    val settings by repository.settings.collectAsState()

    var networkSpeedEnabled by remember(settings) {
        mutableStateOf(settings.isNetworkSpeedCapsuleEnabled)
    }

    var liveProbeEnabled by remember(settings) {
        mutableStateOf(settings.isLiveNotificationProbeEnabled)
    }

    var floatingWindowEnabled by remember(settings) {
        mutableStateOf(settings.isFloatingWindowEnabled)
    }

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var canPostPromotedNotifications by remember { mutableStateOf<Boolean?>(null) }

    fun refreshPromotedNotificationStatus() {
        canPostPromotedNotifications = if (Build.VERSION.SDK_INT >= 36) {
            runCatching {
                context.getSystemService(NotificationManager::class.java)
                    ?.canPostPromotedNotifications()
                    ?: false
            }.getOrDefault(false)
        } else {
            null
        }
    }

    LaunchedEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        refreshPromotedNotificationStatus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                refreshPromotedNotificationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    fun openPromotedNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 36) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
        context.startActivity(intent)
    }

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Android 16 胶囊探针",
                            style = cardTitleStyle
                        )
                        Text(
                            text = "开启后给真实胶囊卡片附加探针字段，不改变小胶囊主文案",
                            style = cardSubtitleStyle
                        )
                    }
                    Switch(
                        checked = liveProbeEnabled,
                        enabled = Build.VERSION.SDK_INT >= 36,
                        onCheckedChange = { enabled ->
                            liveProbeEnabled = enabled
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.updateSettings(
                                    settings.copy(isLiveNotificationProbeEnabled = enabled)
                                )
                            }
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val promotionStatusText = when (canPostPromotedNotifications) {
                        true -> "提升通知权限：已允许"
                        false -> "提升通知权限：未允许，可能只显示普通通知"
                        null -> "提升通知权限：仅 Android 16+ 支持检查"
                    }

                    Text(
                        text = promotionStatusText,
                        style = cardSubtitleStyle,
                        color = if (canPostPromotedNotifications == false) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = "需要先有真实胶囊在显示，探针只增强卡片字段，不单独造一个假胶囊",
                        style = cardSubtitleStyle
                    )
                    Text(
                        text = "卡片探针：${LiveProbeSpec.CONTENT_TITLE} / ${LiveProbeSpec.CONTENT_TEXT} / ${LiveProbeSpec.CONTENT_INFO} / BigText（不再覆盖按钮和第三行）",
                        style = cardSubtitleStyle
                    )
                    OutlinedButton(
                        onClick = { openPromotedNotificationSettings() },
                        enabled = Build.VERSION.SDK_INT >= 36
                    ) {
                        Text("打开提升通知设置")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "网速胶囊",
                            style = cardTitleStyle
                        )
                        Text(
                            text = "在状态栏显示下载速度",
                            style = cardSubtitleStyle
                        )
                    }
                    Switch(
                        checked = networkSpeedEnabled,
                        onCheckedChange = { enabled ->
                            networkSpeedEnabled = enabled
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.updateSettings(
                                    settings.copy(isNetworkSpeedCapsuleEnabled = enabled)
                                )
                            }
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "网速胶囊会覆盖其他胶囊显示",
                        style = cardSubtitleStyle,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "悬浮日程",
                            style = cardTitleStyle
                        )
                        Text(
                            text = "长按音量+键呼出悬浮窗",
                            style = cardSubtitleStyle
                        )
                    }
                    Switch(
                        checked = floatingWindowEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (!hasOverlayPermission) {
                                    openOverlayPermissionSettings()
                                    return@Switch
                                }
                            }
                            floatingWindowEnabled = enabled
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.updateSettings(
                                    settings.copy(isFloatingWindowEnabled = enabled)
                                )
                            }
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "需要悬浮窗权限才能正常使用",
                        style = cardSubtitleStyle,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
