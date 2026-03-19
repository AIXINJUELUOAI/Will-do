package com.antgskds.calendarassistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.antgskds.calendarassistant.core.util.AccessibilityGuardian
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.ui.components.FloatingActionCard
import com.antgskds.calendarassistant.core.util.CrashHandler
import com.antgskds.calendarassistant.core.util.DensityConfigManager
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.navigation.navBackwardEnterTransition
import com.antgskds.calendarassistant.ui.navigation.navBackwardExitTransition
import com.antgskds.calendarassistant.ui.navigation.navForwardEnterTransition
import com.antgskds.calendarassistant.ui.navigation.navForwardExitTransition
import com.antgskds.calendarassistant.ui.page_display.HomeScreen
import com.antgskds.calendarassistant.ui.page_display.SettingsDetailScreen
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantTheme
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    // 取件码时间戳
    private var pickupEventTimestamp = mutableStateOf(0L)

    // ViewModel 实例，供 onResume 使用
    private lateinit var mainViewModel: MainViewModel

    override fun attachBaseContext(newBase: Context) {
        val uiSizeIndex = DensityConfigManager.getUiSizeFromPrefs(newBase)
        val systemMetrics = Resources.getSystem().displayMetrics
        val systemConfig = Resources.getSystem().configuration
        val scale = DensityConfigManager.getScaleFactor(uiSizeIndex)

        val targetDensity = systemMetrics.density * scale
        val targetDpi = (systemMetrics.densityDpi * scale).toInt()
        val targetScaledDensity = targetDensity * systemConfig.fontScale

        val config = Configuration(newBase.resources.configuration)
        config.densityDpi = targetDpi
        config.fontScale = systemConfig.fontScale

        val newContext = newBase.createConfigurationContext(config)

        newContext.resources.displayMetrics.apply {
            density = targetDensity
            scaledDensity = targetScaledDensity
            densityDpi = targetDpi
        }

        super.attachBaseContext(newContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PrivilegeManager.initCheck()

        if (intent.getBooleanExtra("openPickupList", false)) {
            pickupEventTimestamp.value = System.currentTimeMillis()
        }

        enableEdgeToEdge()

        val app = application as App
        val repository = app.repository

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(repository) as T
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repository) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }

        mainViewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
        setupDynamicShortcuts()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val settings by settingsViewModel.settings.collectAsState()
            val promptUpdateDialogState by mainViewModel.promptUpdateDialogState.collectAsState()

            LaunchedEffect(settings.uiSize) {
                if (currentUiSize != settings.uiSize) {
                    currentUiSize = settings.uiSize
                    recreate()
                }
            }

            LaunchedEffect(settings.themeMode, settings.themeColorScheme) {
                if (currentThemeMode != settings.themeMode || currentThemeColorScheme != settings.themeColorScheme) {
                    currentThemeMode = settings.themeMode
                    currentThemeColorScheme = settings.themeColorScheme
                    recreate()
                }
            }

            val isDarkTheme = when (settings.themeMode) {
                1 -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                2 -> false
                3 -> true
                else -> false
            }

            val themeColorSchemeEnum = ThemeColorScheme.fromName(settings.themeColorScheme)

            // ✅ 修复缩进问题
            CalendarAssistantTheme(
                darkTheme = isDarkTheme,
                dynamicColor = themeColorSchemeEnum == ThemeColorScheme.DEFAULT,
                themeColorScheme = themeColorSchemeEnum
            ) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        window.statusBarColor = Color.Transparent.toArgb()
                        window.navigationBarColor = Color.Transparent.toArgb()
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
                        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }

                val navController = rememberNavController()

                // 最外层容器（包裹 NavHost 和所有弹窗）
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable(
                            route = "home",
                            enterTransition = { navBackwardEnterTransition() },
                            exitTransition = { navForwardExitTransition() },
                            popEnterTransition = { navBackwardEnterTransition() },
                            popExitTransition = { null }
                        ) {
                            HomeScreen(
                                mainViewModel = mainViewModel,
                                settingsViewModel = settingsViewModel,
                                pickupTimestamp = pickupEventTimestamp.value,
                                onNavigateToSettings = { destination ->
                                    if (destination == SettingsDestination.Logout) {
                                        finish()
                                    } else {
                                        navController.navigate("settings/${destination.name}")
                                    }
                                }
                            )
                        }

                        composable(
                            route = "settings/{type}",
                            arguments = listOf(navArgument("type") { type = NavType.StringType }),
                            enterTransition = { navForwardEnterTransition() },
                            exitTransition = { null },
                            popEnterTransition = { null },
                            popExitTransition = { navBackwardExitTransition() }
                        ) { backStackEntry ->
                            val typeName = backStackEntry.arguments?.getString("type") ?: ""
                            SettingsDetailScreen(
                                destinationStr = typeName,
                                mainViewModel = mainViewModel,
                                settingsViewModel = settingsViewModel,
                                onExitSettings = { navController.popBackStack() },
                                onLogout = { finish() },
                                uiSize = settings.uiSize
                            )
                        }
                    }

                // --- 弹窗区域 ---

                // ✅ 致命修复点：补上了 remember { }，防止每次界面刷新重置变量并抛出编译错误
                var crashDialogShown by remember { mutableStateOf(false) }
                var cleanupDialogShown by remember { mutableStateOf(false) }
                var cleanupInfo by remember { mutableStateOf("") }

                val showCrashDialog = crashDialogShown
                val showCleanupDialog = cleanupDialogShown && cleanupInfo.isNotEmpty()
                val showPromptDialog = promptUpdateDialogState != null && !showCrashDialog && !showCleanupDialog

                val handleCrashDismiss = {
                    crashDialogShown = false
                    cleanupInfo = CrashHandler.getCleanupInfo(this@MainActivity) ?: ""
                    if (cleanupInfo.isNotEmpty()) {
                        cleanupDialogShown = true
                    }
                    CrashHandler.clearCrashState(this@MainActivity)
                }

                val scrimOnClick: (() -> Unit)? = when {
                    showCrashDialog -> handleCrashDismiss
                    showCleanupDialog -> ({ cleanupDialogShown = false })
                    showPromptDialog -> ({ mainViewModel.dismissPromptUpdate() })
                    else -> null
                }

                AnimatedVisibility(
                    visible = scrimOnClick != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { scrimOnClick?.invoke() }
                            )
                    )
                }

                // 1. Prompt 更新弹窗（优先级最低）
                if (showPromptDialog) {
                    val dialogState = promptUpdateDialogState!!
                    FloatingActionCard(
                        visible = showPromptDialog,
                        title = "Prompt 更新",
                        content = "本地版本：v${dialogState.localVersion}\n云端版本：v${dialogState.remoteVersion}",
                        confirmText = "更新",
                        dismissText = "取消",
                        isDestructive = false,
                        isLoading = false,
                        onConfirm = { mainViewModel.confirmPromptUpdate() },
                        onDismiss = { mainViewModel.dismissPromptUpdate() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                LaunchedEffect(Unit) {
                    if (CrashHandler.isCrashedLastTime(this@MainActivity)) {
                        crashDialogShown = true
                    }
                }

                // 2. 崩溃提示弹窗
                if (showCrashDialog) {
                    FloatingActionCard(
                        visible = showCrashDialog,
                        title = "APP发生异常",
                        content = "APP刚刚发生了崩溃，崩溃日志已记录到:\n\n/Download/CrashLogs/exception.log\n\n您可以通过文件管理器查看并分享给开发者。",
                        confirmText = "确定",
                        dismissText = "关闭",
                        isDestructive = false,
                        isLoading = false,
                        onConfirm = handleCrashDismiss,
                        onDismiss = handleCrashDismiss,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // 3. 异常数据清理弹窗
                if (showCleanupDialog) {
                    FloatingActionCard(
                        visible = showCleanupDialog,
                        title = "异常数据已清除",
                        content = "检测到异常$cleanupInfo，当前已清除。",
                        confirmText = "确定",
                        dismissText = "关闭",
                        isDestructive = false,
                        isLoading = false,
                        onConfirm = { cleanupDialogShown = false },
                        onDismiss = { cleanupDialogShown = false },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
    }

    override fun onResume() {
        super.onResume()
        if (::mainViewModel.isInitialized) {
            mainViewModel.refreshData()
        }
        AccessibilityGuardian.checkAndRestoreIfNeeded(this, lifecycleScope)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("openPickupList", false)) {
            pickupEventTimestamp.value = System.currentTimeMillis()
        }
    }

    private fun setupDynamicShortcuts() {
        val shortcutIntent = Intent(this, com.antgskds.calendarassistant.core.service.shortcut.ShortcutHandleActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "quick_capture")
            .setShortLabel(getString(R.string.shortcut_quick_recognition))
            .setLongLabel(getString(R.string.shortcut_quick_recognition_long))
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, R.drawable.ic_qs_quick_recognition))
            .setIntent(shortcutIntent)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }

    companion object {
        private var currentUiSize: Int = -1
        private var currentThemeMode: Int = -1
        private var currentThemeColorScheme: String = ""
    }
}
