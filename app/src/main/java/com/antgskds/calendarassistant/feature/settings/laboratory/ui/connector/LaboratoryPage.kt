package com.antgskds.calendarassistant.feature.settings.laboratory.ui.connector

import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import androidx.compose.foundation.layout.height

import com.antgskds.calendarassistant.shared.ui.material.settings.*
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract.LaboratoryUiAction
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract.LaboratoryUiState
import kotlinx.coroutines.delay

@Composable
fun LaboratoryPage(
    uiSize: Int = 2,
    settingsViewModel: SettingsViewModel? = null,
    mainViewModel: MainViewModel? = null,
    onNavigateToDeveloper: () -> Unit = {}
) {
    val settings by settingsViewModel?.settings?.collectAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(settings?.developerOptionsUnlocked, settings?.developerOptionsEnabled, settings?.developerOptionsDisabledAtMillis) {
        val current = settings ?: return@LaunchedEffect
        if (
            current.developerOptionsUnlocked &&
            !current.developerOptionsEnabled &&
            current.developerOptionsDisabledAtMillis > 0L
        ) {
            val remaining = DEVELOPER_OPTION_HIDE_DELAY_MS - (System.currentTimeMillis() - current.developerOptionsDisabledAtMillis)
            if (remaining > 0L) {
                delay(remaining)
            }
            val latest = settingsViewModel?.settings?.value ?: return@LaunchedEffect
            if (
                latest.developerOptionsUnlocked &&
                !latest.developerOptionsEnabled &&
                latest.developerOptionsDisabledAtMillis > 0L &&
                System.currentTimeMillis() - latest.developerOptionsDisabledAtMillis >= DEVELOPER_OPTION_HIDE_DELAY_MS
            ) {
                settingsViewModel.expireDeveloperOptionsUnlock()
            }
        }
    }

    MaterialLaboratoryScreen(
        state = LaboratoryUiState(settings = settings),
        uiSize = uiSize,
        onAction = { action ->
            when (action) {
                is LaboratoryUiAction.SetBraceletMode -> settingsViewModel?.updatePreference(braceletModeEnabled = action.enabled)
                is LaboratoryUiAction.SetForceInstantCodeTime -> settingsViewModel?.updatePreference(forceInstantCodeTimeToNow = action.enabled)
                is LaboratoryUiAction.SetPredictiveBack -> settingsViewModel?.updatePreference(predictiveBackEnabled = action.enabled)
                is LaboratoryUiAction.SetClipboardRecognition -> {
                    settingsViewModel?.updatePreference(clipboardCodeRecognitionEnabled = action.enabled)
                }
                LaboratoryUiAction.OpenDeveloper -> onNavigateToDeveloper()
            }
        }
    )
}

@Composable
fun MaterialLaboratoryScreen(
    state: LaboratoryUiState,
    uiSize: Int = 2,
    onAction: (LaboratoryUiAction) -> Unit
) {
    val settings = state.settings
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionGate = rememberPermissionGate(snackbarHostState)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        permissionGate.resumePending()
    }

    fun hasNotificationPermission(): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionGate.resumePending()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppHapticsEnabled provides (settings?.hapticFeedbackEnabled ?: true)) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LaboratorySettingsContent(
                state = state,
                onAction = onAction,
                onBraceletModeChange = { enabled ->
                    if (enabled) {
                        permissionGate.require(
                            permissionName = "通知权限",
                            isGranted = ::hasNotificationPermission,
                            requestPermission = ::requestNotificationPermission,
                            onGranted = { onAction(LaboratoryUiAction.SetBraceletMode(true)) },
                        )
                    } else {
                        onAction(LaboratoryUiAction.SetBraceletMode(false))
                    }
                },
            )

            Spacer(modifier = Modifier.height(LocalAppPageBottomPadding.current))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
    }
}

data class LaboratoryItemVisibility(
    val showForceInstantCodeTime: Boolean = true,
    val showClipboardRecognition: Boolean = true,
    val showPredictiveBack: Boolean = true,
    val showBraceletMode: Boolean = true,
)

@Composable
fun LaboratorySettingsContent(
    state: LaboratoryUiState,
    onAction: (LaboratoryUiAction) -> Unit,
    showDeveloperEntry: Boolean = true,
    itemVisibility: LaboratoryItemVisibility = LaboratoryItemVisibility(),
    onBraceletModeChange: ((Boolean) -> Unit)? = null,
) {
    val settings = state.settings
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (settings != null) {
            Text(
                text = "实验功能",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            if (itemVisibility.showForceInstantCodeTime) LaboratorySwitchCard(
                title = "取件类事件使用当前时间",
                subtitle = "开启后取件码、取餐码、取票码、寄件码会忽略 AI 返回时间，入库时改为当前时间",
                checked = settings.forceInstantCodeTimeToNow,
                onCheckedChange = { enabled ->
                    onAction(LaboratoryUiAction.SetForceInstantCodeTime(enabled))
                }
            )

            if (itemVisibility.showClipboardRecognition) LaboratorySwitchCard(
                title = "剪贴板取件类识别（Beta）",
                subtitle = "打开 WillDo 时检查剪贴板中的取件码、取餐码、取票码和寄件码，命中后询问是否创建日程",
                checked = settings.clipboardCodeRecognitionEnabled,
                onCheckedChange = { enabled ->
                    onAction(LaboratoryUiAction.SetClipboardRecognition(enabled))
                }
            )

            if (itemVisibility.showPredictiveBack) LaboratorySwitchCard(
                title = "预测性返回手势",
                subtitle = "侧滑返回时页面支持跟手动画效果",
                checked = settings.predictiveBackEnabled,
                onCheckedChange = { enabled ->
                    onAction(LaboratoryUiAction.SetPredictiveBack(enabled))
                }
            )

            if (itemVisibility.showBraceletMode) LaboratorySwitchCard(
                title = "手环模式",
                subtitle = "开启后，将同步发送一条普通通知以同步到手环",
                checked = settings.braceletModeEnabled,
                onCheckedChange = { enabled ->
                    onBraceletModeChange?.invoke(enabled)
                        ?: onAction(LaboratoryUiAction.SetBraceletMode(enabled))
                }
            )

            if (showDeveloperEntry && settings.developerOptionsUnlocked) {
                Text(
                    text = "开发者",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                AppCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        ActionSettingItem(
                            title = "开发者页",
                            subtitle = "调试动作、注册台账、通知模板与日志导出",
                            value = "",
                            icon = Icons.Default.ChevronRight,
                            enabled = true,
                            onClick = { onAction(LaboratoryUiAction.OpenDeveloper) },
                            cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            cardValueStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaboratorySwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SwitchSettingItem(
                title = title,
                subtitle = subtitle,
                checked = checked,
                onCheckedChange = onCheckedChange,
                cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private const val DEVELOPER_OPTION_HIDE_DELAY_MS = 5 * 60 * 1000L
