package com.antgskds.calendarassistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.antgskds.calendarassistant.core.util.AccessibilityGuardian
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.data.model.HomeEntryKey
import com.antgskds.calendarassistant.data.model.sanitizeHomeBottomItems
import com.antgskds.calendarassistant.data.model.sanitizeHomeStartPageKey
import com.antgskds.calendarassistant.data.model.UiStyle
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.core.util.CrashHandler
import com.antgskds.calendarassistant.core.util.DensityConfigManager
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.navigation.AppRoutes
import com.antgskds.calendarassistant.ui.navigation.navBackwardEnterTransition
import com.antgskds.calendarassistant.ui.navigation.navBackwardExitTransition
import com.antgskds.calendarassistant.ui.navigation.navForwardEnterTransition
import com.antgskds.calendarassistant.ui.navigation.navForwardExitTransition
import com.antgskds.calendarassistant.ui.page_display.HomeScreen
import com.antgskds.calendarassistant.ui.page_display.NoteEditorScreen
import com.antgskds.calendarassistant.ui.page_display.SettingsDetailScreen
import com.antgskds.calendarassistant.ui.page_display.settings.WeatherDetailScreen
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantStyleTheme
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import com.antgskds.calendarassistant.widget.WidgetActions

private data class PendingWidgetLaunchAction(
    val action: String,
    val nonce: Long = System.nanoTime()
)

class MainActivity : ComponentActivity() {

    // 取件码时间戳
    private var pickupEventTimestamp = mutableStateOf(0L)

    // ViewModel 实例，供 onResume 使用
    private lateinit var mainViewModel: MainViewModel

    private val pendingWidgetAction = mutableStateOf<PendingWidgetLaunchAction?>(null)

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
        consumeWidgetAction(intent)

        enableEdgeToEdge()

        // 小窗模式：显式关闭导航栏对比度保护，防止系统自动加白色底色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val app = application as App

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(
                        appContext = app.applicationContext,
                        scheduleCenter = app.scheduleCenter,
                        noteCenter = app.noteCenter,
                        settingsQueryApi = app.settingsQueryApi,
                        homeQueryApi = app.homeQueryApi,
                        scheduleInsightsQueryApi = app.scheduleInsightsQueryApi,
                        weatherQueryApi = app.weatherQueryApi,
                        weatherOperationApi = app.weatherOperationApi,
                        attachmentManager = app.eventAttachmentManager
                    ) as T
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                        scheduleCenter = app.scheduleCenter,
                        backupCenter = app.backupCenter,
                        syncCenter = app.syncCenter,
                        diagnosticLogCenter = app.diagnosticLogCenter,
                        settingsOperationApi = app.settingsOperationApi,
                        settingsQueryApi = app.settingsQueryApi,
                        settingsTransformApi = app.settingsTransformApi,
                        scheduleInsightsQueryApi = app.scheduleInsightsQueryApi,
                        legacyNoteMigrationCenter = app.legacyNoteMigrationCenter,
                        duplicateEventCleanupCenter = app.duplicateEventCleanupCenter
                    ) as T
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
            val clipboardPrompt by app.clipboardCodeCenter.pendingPrompt.collectAsState()
            val localModelResiduePrompt by app.localModelResidueCenter.pendingPrompt.collectAsState()
            val uiStyle = UiStyle.fromName(settings.uiStyle)

            LaunchedEffect(settings.uiSize) {
                if (currentUiSize != settings.uiSize) {
                    currentUiSize = settings.uiSize
                    recreate()
                }
            }

            LaunchedEffect(settings.themeMode, settings.themeColorScheme, settings.customThemeColorHex, settings.uiStyle) {
                if (
                    currentThemeMode != settings.themeMode ||
                    currentThemeColorScheme != settings.themeColorScheme ||
                    currentUiStyle != settings.uiStyle
                ) {
                    currentThemeMode = settings.themeMode
                    currentThemeColorScheme = settings.themeColorScheme
                    currentUiStyle = settings.uiStyle
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
            CalendarAssistantStyleTheme(
                uiStyle = uiStyle,
                darkTheme = isDarkTheme,
                dynamicColor = themeColorSchemeEnum == ThemeColorScheme.DEFAULT,
                themeColorScheme = themeColorSchemeEnum,
                customThemeColorHex = settings.customThemeColorHex
            ) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    val bgColor = MaterialTheme.colorScheme.background.toArgb()
                    SideEffect {
                        val window = (view.context as Activity).window
                        window.statusBarColor = Color.Transparent.toArgb()
                        window.navigationBarColor = Color.Transparent.toArgb()
                        window.setBackgroundDrawable(ColorDrawable(bgColor))
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
                        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }

                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val predictiveBackEnabled = settings.predictiveBackEnabled
                val homeBottomItems = remember(settings.homeBottomItems) {
                    sanitizeHomeBottomItems(settings.homeBottomItems)
                }
                val homeStartPageKey = remember(settings.homeStartPageKey, homeBottomItems) {
                    sanitizeHomeStartPageKey(settings.homeStartPageKey, homeBottomItems)
                }
                var selectedHomePageKey by rememberSaveable { mutableStateOf(homeStartPageKey) }
                var lastPickupEventTimestamp by rememberSaveable { mutableLongStateOf(0L) }
                var lastWidgetActionNonce by rememberSaveable { mutableLongStateOf(0L) }
                var openCourseRequestId by rememberSaveable { mutableLongStateOf(0L) }
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val floatingActionCardBottomPadding = if (currentBackStackEntry?.destination?.route == AppRoutes.Home) {
                    IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset + 16.dp
                } else {
                    bottomInset + 16.dp
                }

                LaunchedEffect(homeBottomItems, homeStartPageKey, selectedHomePageKey) {
                    if (selectedHomePageKey !in homeBottomItems) {
                        selectedHomePageKey = homeStartPageKey
                    }
                }

                LaunchedEffect(pickupEventTimestamp.value) {
                    val timestamp = pickupEventTimestamp.value
                    if (timestamp > 0L && timestamp != lastPickupEventTimestamp) {
                        selectedHomePageKey = HomeEntryKey.ALL
                        lastPickupEventTimestamp = timestamp
                    }
                }

                LaunchedEffect(pendingWidgetAction.value) {
                    val pending = pendingWidgetAction.value ?: return@LaunchedEffect
                    if (pending.nonce == lastWidgetActionNonce) return@LaunchedEffect
                    lastWidgetActionNonce = pending.nonce
                    when (pending.action) {
                        WidgetActions.ACTION_OPEN_WEATHER -> {
                            navController.navigate(AppRoutes.WeatherDetail) { launchSingleTop = true }
                        }
                        WidgetActions.ACTION_OPEN_HOME -> {
                            if (currentBackStackEntry?.destination?.route != AppRoutes.Home) {
                                navController.navigate(AppRoutes.Home) {
                                    launchSingleTop = true
                                    popUpTo(AppRoutes.Home) { inclusive = false }
                                }
                            }
                        }
                        WidgetActions.ACTION_OPEN_COURSE -> {
                            if (currentBackStackEntry?.destination?.route != AppRoutes.Home) {
                                navController.navigate(AppRoutes.Home) {
                                    launchSingleTop = true
                                    popUpTo(AppRoutes.Home) { inclusive = false }
                                }
                            }
                            if (HomeEntryKey.TODAY in homeBottomItems) {
                                selectedHomePageKey = HomeEntryKey.TODAY
                            }
                            if (settings.courseFeatureEnabled) {
                                openCourseRequestId++
                            }
                        }
                    }
                }

                // 最外层容器（包裹 NavHost 和所有弹窗）
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavHost(
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                        navController = navController,
                        startDestination = AppRoutes.Home
                    ) {
                        composable(
                            route = AppRoutes.Home,
                            enterTransition = { navBackwardEnterTransition() },
                            exitTransition = { navForwardExitTransition() },
                            popEnterTransition = { navBackwardEnterTransition() },
                            popExitTransition = { null }
                        ) {
                            when (uiStyle) {
                                UiStyle.MIUI -> com.antgskds.calendarassistant.miui.page_display.HomeScreen(
                                    mainViewModel = mainViewModel,
                                    settingsViewModel = settingsViewModel,
                                    pickupTimestamp = pickupEventTimestamp.value,
                                    onNavigateToSettings = { destination ->
                                        if (destination.name == SettingsDestination.Logout.name) {
                                            finish()
                                        } else {
                                            navController.navigate(AppRoutes.settings(destination.name))
                                        }
                                    }
                                )
                                UiStyle.MATERIAL3 -> HomeScreen(
                                    mainViewModel = mainViewModel,
                                    settingsViewModel = settingsViewModel,
                                     pickupTimestamp = pickupEventTimestamp.value,
                                     openCourseRequestId = openCourseRequestId,
                                     selectedPageKey = selectedHomePageKey,
                                    onSelectedPageKeyChange = { pageKey ->
                                        selectedHomePageKey = if (pageKey in homeBottomItems) pageKey else homeStartPageKey
                                    },
                                    onOpenWeatherDetail = {
                                        navController.navigate(AppRoutes.WeatherDetail)
                                    },
                                    onOpenNoteEditor = { noteId ->
                                        navController.navigate(AppRoutes.noteEditor(noteId))
                                    },
                                    onNavigateToSettings = { destination ->
                                        if (destination == SettingsDestination.Logout) {
                                            finish()
                                        } else {
                                            navController.navigate(AppRoutes.settings(destination.name))
                                        }
                                    }
                                )
                            }
                        }

                        composable(
                            route = AppRoutes.NoteEditorPattern,
                            arguments = listOf(navArgument(AppRoutes.NoteEditorArg) { type = NavType.LongType }),
                            enterTransition = { navForwardEnterTransition() },
                            exitTransition = { null },
                            popEnterTransition = { null },
                            popExitTransition = { navBackwardExitTransition() }
                        ) { backStackEntry ->
                            BackHandler(enabled = !predictiveBackEnabled) {
                                navController.popBackStack()
                            }
                            val noteId = backStackEntry.arguments?.getLong(AppRoutes.NoteEditorArg) ?: AppRoutes.NoteEditorNewArg
                            val uiState by mainViewModel.uiState.collectAsState()
                            var initialNote by remember(noteId) { mutableStateOf<com.antgskds.calendarassistant.core.note.NoteEntity?>(null) }
                            var noteLoaded by remember(noteId) { mutableStateOf(noteId == AppRoutes.NoteEditorNewArg) }
                            LaunchedEffect(noteId) {
                                initialNote = if (noteId == AppRoutes.NoteEditorNewArg) null else mainViewModel.getNoteById(noteId)
                                noteLoaded = true
                            }
                            if (noteLoaded) {
                                NoteEditorScreen(
                                    initialNote = initialNote,
                                    editorSessionKey = noteId.hashCode(),
                                    settings = uiState.settings,
                                    onDismiss = { navController.popBackStack() },
                                    onSave = { id, title, document, createdAt, onSaved ->
                                        mainViewModel.saveNote(id, title, document, createdAt, onSaved)
                                    },
                                    onDelete = { id, onDeleted ->
                                        mainViewModel.deleteNote(id, onDeleted)
                                    },
                                    onSetPinned = { id, pinned ->
                                        mainViewModel.setNotePinned(id, pinned)
                                    },
                                    onExportNote = { id, uri, onResult ->
                                        mainViewModel.exportNote(id, uri, onResult)
                                    },
                                    onExportMarkdownNote = { id, uri, onResult ->
                                        mainViewModel.exportMarkdownNote(id, uri, onResult)
                                    },
                                    onImportNote = { uri, onResult ->
                                        mainViewModel.importNote(uri, onResult)
                                    },
                                    onOpenImportedNote = { importedId ->
                                        navController.navigate(AppRoutes.noteEditor(importedId))
                                    },
                                    onShowMessage = { message, _ ->
                                        android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        composable(
                            route = AppRoutes.WeatherDetail,
                            enterTransition = { navForwardEnterTransition() },
                            exitTransition = { null },
                            popEnterTransition = { null },
                            popExitTransition = { navBackwardExitTransition() }
                        ) {
                            BackHandler(enabled = !predictiveBackEnabled) {
                                navController.popBackStack()
                            }
                            WeatherDetailScreen(
                                uiSize = settings.uiSize,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = AppRoutes.SettingsPattern,
                            arguments = listOf(navArgument(AppRoutes.SettingsTypeArg) { type = NavType.StringType }),
                            enterTransition = { navForwardEnterTransition() },
                            exitTransition = { null },
                            popEnterTransition = { null },
                            popExitTransition = { navBackwardExitTransition() }
                        ) { backStackEntry ->
                            BackHandler(enabled = !predictiveBackEnabled) {
                                navController.popBackStack()
                            }
                            val typeName = backStackEntry.arguments?.getString(AppRoutes.SettingsTypeArg) ?: ""
                            when (uiStyle) {
                                UiStyle.MIUI -> com.antgskds.calendarassistant.miui.page_display.SettingsDetailScreen(
                                    destinationStr = typeName,
                                    mainViewModel = mainViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onExitSettings = { navController.popBackStack() },
                                    onLogout = { finish() },
                                    uiSize = settings.uiSize
                                )
                                UiStyle.MATERIAL3 -> SettingsDetailScreen(
                                    destinationStr = typeName,
                                    mainViewModel = mainViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onExitSettings = { navController.popBackStack() },
                                    onLogout = { finish() },
                                    uiSize = settings.uiSize
                                )
                            }
                        }
                    }

                // --- 弹窗区域 ---

                // ✅ 致命修复点：补上了 remember { }，防止每次界面刷新重置变量并抛出编译错误
                var crashDialogShown by remember { mutableStateOf(false) }
                var cleanupDialogShown by remember { mutableStateOf(false) }
                var cleanupInfo by remember { mutableStateOf("") }

                val showCrashDialog = crashDialogShown
                val showCleanupDialog = cleanupDialogShown && cleanupInfo.isNotEmpty()
                val showLocalModelResiduePrompt = localModelResiduePrompt != null && !showCrashDialog && !showCleanupDialog
                val showClipboardPrompt = clipboardPrompt != null && !showCrashDialog && !showCleanupDialog && !showLocalModelResiduePrompt
                val showPromptDialog = promptUpdateDialogState != null && !showCrashDialog && !showCleanupDialog && !showLocalModelResiduePrompt && !showClipboardPrompt

                val handleCrashDismiss = {
                    crashDialogShown = false
                    cleanupInfo = CrashHandler.getCleanupInfo(this@MainActivity) ?: ""
                    if (cleanupInfo.isNotEmpty()) {
                        cleanupDialogShown = true
                    }
                    CrashHandler.clearCrashState(this@MainActivity)
                }

                // 1. Prompt 更新弹窗（优先级最低）
                if (showPromptDialog) {
                    val dialogState = promptUpdateDialogState!!
                    PredictiveFloatingActionCard(
                        visible = showPromptDialog,
                        title = "Prompt 更新",
                        content = "本地版本：v${dialogState.localVersion}\n云端版本：v${dialogState.remoteVersion}",
                        confirmText = "更新",
                        dismissText = "取消",
                        isDestructive = false,
                        isLoading = false,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onConfirm = { mainViewModel.confirmPromptUpdate() },
                        onDismiss = { mainViewModel.dismissPromptUpdate() }
                    )
                }

                if (showLocalModelResiduePrompt) {
                    val prompt = localModelResiduePrompt!!
                    PredictiveFloatingActionCard(
                        visible = showLocalModelResiduePrompt,
                        title = "检测到本地模型文件",
                        content = "本地模型文件占用约 ${formatLocalModelResidueSize(prompt.sizeBytes)}，标准版不会使用它们，是否清理以释放空间？",
                        confirmText = "清理",
                        dismissText = "稍后",
                        isDestructive = true,
                        isLoading = false,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onConfirm = { app.localModelResidueCenter.clearResidue() },
                        onDismiss = { app.localModelResidueCenter.dismissPendingPrompt() },
                        modifier = Modifier.padding(bottom = floatingActionCardBottomPadding)
                    )
                }

                if (showClipboardPrompt) {
                    val prompt = clipboardPrompt!!
                    PredictiveFloatingActionCard(
                        visible = showClipboardPrompt,
                        title = "识别到剪贴板中的${prompt.candidate.type.displayLabel}",
                        content = "${prompt.candidate.type.displayLabel}：${prompt.candidate.code}",
                        confirmText = "入库",
                        dismissText = "忽略",
                        isDestructive = false,
                        isLoading = false,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onConfirm = { app.clipboardCodeCenter.confirmPendingPrompt() },
                        onDismiss = { app.clipboardCodeCenter.dismissPendingPrompt() },
                        modifier = Modifier.padding(bottom = floatingActionCardBottomPadding)
                    )
                }

                LaunchedEffect(Unit) {
                    if (CrashHandler.isCrashedLastTime(this@MainActivity)) {
                        crashDialogShown = true
                    }
                }

                // 2. 崩溃提示弹窗
                if (showCrashDialog) {
                    PredictiveFloatingActionCard(
                        visible = showCrashDialog,
                        title = "APP发生异常",
                        content = "APP刚刚发生了崩溃，崩溃日志已记录到:\n\n/Download/WillDo/crash/exception.log\n\n您可以通过文件管理器查看并分享给开发者。",
                        confirmText = "确定",
                        dismissText = "关闭",
                        isDestructive = false,
                        isLoading = false,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onConfirm = handleCrashDismiss,
                        onDismiss = handleCrashDismiss
                    )
                }

                // 3. 异常数据清理弹窗
                if (showCleanupDialog) {
                    PredictiveFloatingActionCard(
                        visible = showCleanupDialog,
                        title = "异常数据已清除",
                        content = "检测到异常$cleanupInfo，当前已清除。",
                        confirmText = "确定",
                        dismissText = "关闭",
                        isDestructive = false,
                        isLoading = false,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onConfirm = { cleanupDialogShown = false },
                        onDismiss = { cleanupDialogShown = false }
                    )
                }
            }
        }
    }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("openPickupList", false)) {
            pickupEventTimestamp.value = System.currentTimeMillis()
        }
        consumeWidgetAction(intent)
    }

    private fun consumeWidgetAction(intent: Intent?) {
        val action = intent?.getStringExtra(WidgetActions.EXTRA_WIDGET_ACTION)?.takeIf { it.isNotBlank() } ?: return
        pendingWidgetAction.value = PendingWidgetLaunchAction(action)
    }

    override fun onResume() {
        super.onResume()
        if (::mainViewModel.isInitialized) {
            mainViewModel.refreshData()
        }
        (application as App).clipboardCodeCenter.checkClipboardForPrompt("app_resume")
        (application as App).localModelResidueCenter.checkForResidue()
        AccessibilityGuardian.checkAndRestoreIfNeeded(this, lifecycleScope)
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
        private var currentUiStyle: String = ""
    }
}

private fun formatLocalModelResidueSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mib = bytes / 1024.0 / 1024.0
    return if (mib >= 1024.0) {
        String.format(java.util.Locale.US, "%.2f GB", mib / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.0f MB", mib)
    }
}
