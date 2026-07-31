package com.antgskds.calendarassistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
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
import com.antgskds.calendarassistant.shared.util.AccessibilityGuardian
import com.antgskds.calendarassistant.shared.util.PrivilegeManager
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey
import com.antgskds.calendarassistant.feature.home.domain.sanitizeHomeStartPageKey
import com.antgskds.calendarassistant.feature.home.domain.visibleHomeBottomItems
import com.antgskds.calendarassistant.feature.home.ui.render.editionHomeEntries
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.shared.util.CrashHandler
import com.antgskds.calendarassistant.shared.util.DensityConfigManager
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.app.ui.prompt.contract.GlobalPromptKind
import com.antgskds.calendarassistant.app.ui.prompt.contract.GlobalPromptUiAction
import com.antgskds.calendarassistant.app.ui.prompt.contract.GlobalPromptUiModel
import com.antgskds.calendarassistant.app.ui.prompt.contract.GlobalPromptUiState
import com.antgskds.calendarassistant.app.ui.prompt.render.GlobalPromptHost
import com.antgskds.calendarassistant.app.ui.navigation.AppRoutes
import com.antgskds.calendarassistant.app.ui.navigation.navBackwardEnterTransition
import com.antgskds.calendarassistant.app.ui.navigation.navBackwardExitTransition
import com.antgskds.calendarassistant.app.ui.navigation.navForwardEnterTransition
import com.antgskds.calendarassistant.app.ui.navigation.navForwardExitTransition
import com.antgskds.calendarassistant.feature.home.ui.connector.HomeScreen
import com.antgskds.calendarassistant.feature.note.ui.connector.NoteEditorRoute
import com.antgskds.calendarassistant.feature.quickmemo.ui.connector.QuickMemoDetailPage
import com.antgskds.calendarassistant.feature.settings.data.SettingsDataSource
import com.antgskds.calendarassistant.feature.settings.onboarding.ui.connector.OnboardingGuidePage
import com.antgskds.calendarassistant.feature.settings.shell.ui.connector.SettingsDetailRoute
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundRootSize
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundWallpaperBitmap
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppWallpaperImage
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundAverageLuminance
import com.antgskds.calendarassistant.app.ui.theme.material.background.shouldUseLightSystemBarsForAppBackground
import com.antgskds.calendarassistant.shared.ui.material.component.AppGlassSettings
import com.antgskds.calendarassistant.shared.ui.material.component.AppGlassSettingsProvider
import com.antgskds.calendarassistant.feature.weather.ui.connector.WeatherDetailScreen
import com.antgskds.calendarassistant.app.ui.theme.CalendarAssistantStyleTheme
import com.antgskds.calendarassistant.app.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.platform.widget.WidgetActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.roundToInt

private data class PendingWidgetLaunchAction(
    val action: String,
    val nonce: Long = System.nanoTime()
)

private data class PendingQuickMemoDetailLaunch(
    val memoId: Long,
    val nonce: Long = System.nanoTime()
)

private data class PendingEventDialogLaunch(
    val eventId: Long,
    val nonce: Long = System.nanoTime()
)

private data class ActivityAppearanceConfig(
    val uiSize: Int,
    val themeMode: Int,
    val themeColorScheme: String,
    val customThemeColorHex: String,
)

class MainActivity : ComponentActivity() {

    // 取件码时间戳
    private var pickupEventTimestamp = mutableStateOf(0L)

    // ViewModel 实例，供 onResume 使用
    private lateinit var mainViewModel: MainViewModel

    private val pendingWidgetAction = mutableStateOf<PendingWidgetLaunchAction?>(null)
    private val pendingQuickMemoDetailLaunch = mutableStateOf<PendingQuickMemoDetailLaunch?>(null)
    private val pendingEventDialogLaunch = mutableStateOf<PendingEventDialogLaunch?>(null)

    private fun shouldShowOnboardingOnFirstLaunch(): Boolean {
        val onboardingPrefs = getSharedPreferences(ONBOARDING_PREFS_NAME, Context.MODE_PRIVATE)
        if (onboardingPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)) return false

        val settingsPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val hasModernSettings = settingsPrefs.contains(SettingsDataSource.KEY_JSON)
        val hasLegacySettings = settingsPrefs.contains("model_key") || settingsPrefs.contains("semester_start_date")
        return !hasModernSettings && !hasLegacySettings
    }

    private fun markOnboardingCompleted() {
        getSharedPreferences(ONBOARDING_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
    }

    override fun attachBaseContext(newBase: Context) {
        val uiSizeIndex = DensityConfigManager.getUiSizeFromPrefs(newBase)
        val systemMetrics = Resources.getSystem().displayMetrics
        val systemConfig = Resources.getSystem().configuration
        val scale = DensityConfigManager.getAppScaleFactor(newBase, uiSizeIndex)

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
        requestRecordAudioPermissionIfNeeded(intent)
        consumeWidgetAction(intent)
        consumeQuickMemoDetailIntent(intent)
        consumeEventDialogIntent(intent)

        enableEdgeToEdge()

        // 小窗模式：显式关闭导航栏对比度保护，防止系统自动加白色底色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val app = application as App
        val shouldShowInitialOnboarding = shouldShowOnboardingOnFirstLaunch()

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(
                        appContext = app.applicationContext,
                        scheduleCenter = app.scheduleCenter,
                        noteCenter = app.noteCenter,
                        quickMemoCenter = app.quickMemoCenter,
                        audioPlaybackCenter = app.audioPlaybackCenter,
                        capsuleQueryApi = app.capsuleQueryApi,
                        settingsQueryApi = app.settingsQueryApi,
                        homeQueryApi = app.homeQueryApi,
                        scheduleInsightsQueryApi = app.scheduleInsightsQueryApi,
                        weatherQueryApi = app.weatherQueryApi,
                        weatherOperationApi = app.weatherOperationApi,
                        attachmentManager = app.eventAttachmentManager
                    ) as T
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                        appContext = app.applicationContext,
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

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val settings by settingsViewModel.settings.collectAsState()
            val promptUpdateDialogState by mainViewModel.promptUpdateDialogState.collectAsState()
            val clipboardPrompt by app.clipboardCodeCenter.pendingPrompt.collectAsState()
            val localModelResiduePrompt by app.localModelResidueCenter.pendingPrompt.collectAsState()
            val appearanceConfig = ActivityAppearanceConfig(
                uiSize = settings.uiSize,
                themeMode = settings.themeMode,
                themeColorScheme = settings.themeColorScheme,
                customThemeColorHex = settings.customThemeColorHex
            )
            var appliedAppearanceConfig by remember { mutableStateOf<ActivityAppearanceConfig?>(null) }
            LaunchedEffect(appearanceConfig) {
                val previous = appliedAppearanceConfig
                appliedAppearanceConfig = appearanceConfig
                if (previous != null && previous != appearanceConfig) {
                    recreate()
                }
            }
            LaunchedEffect(settings.themeMode) {
                setupDynamicShortcuts()
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
                darkTheme = isDarkTheme,
                dynamicColor = themeColorSchemeEnum == ThemeColorScheme.DEFAULT,
                themeColorScheme = themeColorSchemeEnum,
                customThemeColorHex = settings.customThemeColorHex
            ) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    val bgColor = MaterialTheme.colorScheme.background.toArgb()
                    val hasAppBackground = settings.appBackgroundImagePath.isNotBlank()
                    val lightSystemBars = if (hasAppBackground) {
                        shouldUseLightSystemBarsForAppBackground(defaultLight = !isDarkTheme)
                    } else {
                        !isDarkTheme
                    }
                    SideEffect {
                        val window = (view.context as Activity).window
                        window.statusBarColor = Color.Transparent.toArgb()
                        window.navigationBarColor = Color.Transparent.toArgb()
                        window.setBackgroundDrawable(ColorDrawable(bgColor))
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightSystemBars
                        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = lightSystemBars
                    }
                }

                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val predictiveBackEnabled = settings.predictiveBackEnabled
                val homeBottomItems = remember(settings.homeBottomItems, settings.voiceInputEnabled) {
                    editionHomeEntries(
                        visibleHomeBottomItems(
                            settings.homeBottomItems,
                            quickMemoEnabled = settings.voiceInputEnabled,
                        ),
                    )
                }
                val homeStartPageKey = remember(settings.homeStartPageKey, homeBottomItems) {
                    sanitizeHomeStartPageKey(settings.homeStartPageKey, homeBottomItems)
                }
                var selectedHomePageKey by rememberSaveable { mutableStateOf(homeStartPageKey) }
                var lastPickupEventTimestamp by rememberSaveable { mutableLongStateOf(0L) }
                var lastWidgetActionNonce by rememberSaveable { mutableLongStateOf(0L) }
                var lastQuickMemoDetailNonce by rememberSaveable { mutableLongStateOf(0L) }
                var lastEventDialogNonce by rememberSaveable { mutableLongStateOf(0L) }
                var openCourseRequestId by rememberSaveable { mutableLongStateOf(0L) }
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val floatingActionCardBottomPadding = if (currentBackStackEntry?.destination?.route == AppRoutes.Home) {
                    IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset + 16.dp
                } else {
                    bottomInset + 16.dp
                }
                val showClipboardPromptOnMaterialHome =
                    currentBackStackEntry?.destination?.route == AppRoutes.Home

                var crashDialogShown by remember { mutableStateOf(false) }
                var cleanupDialogShown by remember { mutableStateOf(false) }
                var cleanupInfo by remember { mutableStateOf("") }

                val showCrashDialog = crashDialogShown
                val showCleanupDialog = cleanupDialogShown && cleanupInfo.isNotEmpty()
                val showLocalModelResiduePrompt = localModelResiduePrompt != null && !showCrashDialog && !showCleanupDialog
                val showClipboardPrompt = clipboardPrompt != null && !showCrashDialog && !showCleanupDialog && !showLocalModelResiduePrompt
                val showClipboardPromptGlobally = showClipboardPrompt && !showClipboardPromptOnMaterialHome
                val homeClipboardPrompt = clipboardPrompt.takeIf { showClipboardPrompt && showClipboardPromptOnMaterialHome }
                val showPromptDialog = promptUpdateDialogState != null && !showCrashDialog && !showCleanupDialog && !showLocalModelResiduePrompt && !showClipboardPrompt

                val handleCrashDismiss = {
                    crashDialogShown = false
                    cleanupInfo = CrashHandler.getCleanupInfo(this@MainActivity) ?: ""
                    if (cleanupInfo.isNotEmpty()) {
                        cleanupDialogShown = true
                    }
                    CrashHandler.clearCrashState(this@MainActivity)
                }

                val activeGlobalPrompt = when {
                    showCrashDialog -> GlobalPromptUiModel(
                        kind = GlobalPromptKind.CRASH_REPORT,
                        title = "APP发生异常",
                        content = "APP刚刚发生了崩溃，崩溃日志已记录到:\n\n/Download/WillDo/crash/exception.log\n\n您可以通过文件管理器查看并分享给开发者。",
                        confirmText = "确定",
                        dismissText = "关闭"
                    )

                    showCleanupDialog -> GlobalPromptUiModel(
                        kind = GlobalPromptKind.CLEANUP_REPORT,
                        title = "异常数据已清除",
                        content = "检测到异常$cleanupInfo，当前已清除。",
                        confirmText = "确定",
                        dismissText = "关闭"
                    )

                    showLocalModelResiduePrompt -> {
                        val prompt = localModelResiduePrompt!!
                        GlobalPromptUiModel(
                            kind = GlobalPromptKind.LOCAL_MODEL_RESIDUE,
                            title = "检测到本地模型文件",
                            content = "本地模型文件占用约 ${formatLocalModelResidueSize(prompt.sizeBytes)}，标准版不会使用它们，是否清理以释放空间？",
                            confirmText = "清理",
                            dismissText = "稍后",
                            isDestructive = true,
                            useFloatingBottomPadding = true
                        )
                    }

                    showClipboardPromptGlobally -> {
                        val prompt = clipboardPrompt!!
                        GlobalPromptUiModel(
                            kind = GlobalPromptKind.CLIPBOARD_CODE,
                            title = "识别到剪贴板中的${prompt.candidate.type.displayLabel}",
                            content = "${prompt.candidate.type.displayLabel}：${prompt.candidate.code}",
                            confirmText = "入库",
                            dismissText = "忽略",
                            useFloatingBottomPadding = true
                        )
                    }

                    showPromptDialog -> {
                        val dialogState = promptUpdateDialogState!!
                        GlobalPromptUiModel(
                            kind = GlobalPromptKind.PROMPT_UPDATE,
                            title = "Prompt 更新",
                            content = "本地版本：v${dialogState.localVersion}\n云端版本：v${dialogState.remoteVersion}",
                            confirmText = "更新",
                            dismissText = "取消"
                        )
                    }

                    else -> null
                }
                val globalPromptState = GlobalPromptUiState(
                    prompt = activeGlobalPrompt,
                    predictiveBackEnabled = predictiveBackEnabled
                )

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

                LaunchedEffect(pendingQuickMemoDetailLaunch.value) {
                    val pending = pendingQuickMemoDetailLaunch.value ?: return@LaunchedEffect
                    if (pending.nonce == lastQuickMemoDetailNonce) return@LaunchedEffect
                    lastQuickMemoDetailNonce = pending.nonce
                    navController.navigate(AppRoutes.quickMemoDetail(pending.memoId)) { launchSingleTop = true }
                }

                LaunchedEffect(pendingEventDialogLaunch.value) {
                    val pending = pendingEventDialogLaunch.value ?: return@LaunchedEffect
                    if (pending.nonce == lastEventDialogNonce) return@LaunchedEffect
                    lastEventDialogNonce = pending.nonce
                    selectedHomePageKey = HomeEntryKey.ALL
                    navController.navigate(AppRoutes.Home) {
                        launchSingleTop = true
                        popUpTo(AppRoutes.Home) { inclusive = false }
                    }
                }

                val pendingEventDialog = pendingEventDialogLaunch.value

                val appBackgroundBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                    initialValue = null,
                    key1 = settings.appBackgroundImagePath
                ) {
                    value = if (settings.appBackgroundImagePath.isBlank()) {
                        null
                    } else {
                        withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(settings.appBackgroundImagePath)?.asImageBitmap()
                        }
                    }
                }
                var appBackgroundRootSize by remember { mutableStateOf(IntSize.Zero) }
                val appGlassBackdrop = rememberLayerBackdrop()
                val appSceneBackdrop = rememberLayerBackdrop()
                val useMiuiBlurMaterial = settings.appBackgroundMiuiBlurTestEnabled &&
                    appBackgroundBitmap != null
                val useHyperosSceneBlur = BuildConfig.UI_EDITION == "hyperos"
                val captureSceneBackdrop = useMiuiBlurMaterial || useHyperosSceneBlur

                AppGlassSettingsProvider(
                    settings = AppGlassSettings(
                        enabled = captureSceneBackdrop,
                        backdrop = appGlassBackdrop.takeIf { useMiuiBlurMaterial },
                        overlayBackdrop = appSceneBackdrop,
                        darkTheme = isDarkTheme
                    )
                ) {
                    CompositionLocalProvider(
                        LocalAppBackgroundWallpaperBitmap provides appBackgroundBitmap,
                        LocalAppBackgroundRootSize provides appBackgroundRootSize,
                        LocalAppBackgroundAverageLuminance provides settings.appBackgroundAverageLuminance
                    ) {
                    // 最外层容器（包裹 NavHost 和所有弹窗）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .onSizeChanged { appBackgroundRootSize = it }
                            .then(
                                if (captureSceneBackdrop) {
                                    Modifier.layerBackdrop(appSceneBackdrop)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        AppBackgroundLayer(
                            enabled = settings.appBackgroundImagePath.isNotBlank(),
                            imageBitmap = appBackgroundBitmap,
                            imageScale = settings.appBackgroundImageScale,
                            imageOffsetX = settings.appBackgroundImageOffsetX,
                            imageOffsetY = settings.appBackgroundImageOffsetY,
                            blurEnabled = settings.appBackgroundWallpaperBlurEnabled,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (useMiuiBlurMaterial) {
                                        Modifier.layerBackdrop(appGlassBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                        NavHost(
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                            startDestination = if (shouldShowInitialOnboarding) AppRoutes.OnboardingGuide else AppRoutes.Home
                        ) {
                        composable(
                            route = AppRoutes.OnboardingGuide,
                            enterTransition = { navForwardEnterTransition() },
                            exitTransition = { null },
                            popEnterTransition = { null },
                            popExitTransition = { navBackwardExitTransition() }
                        ) {
                            OnboardingGuidePage(
                                settingsViewModel = settingsViewModel,
                                uiSize = settings.uiSize,
                                onFinish = {
                                    markOnboardingCompleted()
                                    navController.navigate(AppRoutes.Home) {
                                        launchSingleTop = true
                                        popUpTo(AppRoutes.OnboardingGuide) { inclusive = true }
                                    }
                                },
                                onImportConfig = {
                                    markOnboardingCompleted()
                                    navController.navigate(AppRoutes.settings(SettingsDestination.Backup.name)) {
                                        launchSingleTop = true
                                        popUpTo(AppRoutes.OnboardingGuide) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = AppRoutes.Home,
                            enterTransition = { navBackwardEnterTransition() },
                            exitTransition = { navForwardExitTransition() },
                            popEnterTransition = { navBackwardEnterTransition() },
                            popExitTransition = { null }
                        ) {
                            HomeScreen(
                                    mainViewModel = mainViewModel,
                                    settingsViewModel = settingsViewModel,
                                     pickupTimestamp = pickupEventTimestamp.value,
                                     openCourseRequestId = openCourseRequestId,
                                      openEventId = pendingEventDialog?.eventId,
                                      openEventRequestId = pendingEventDialog?.nonce ?: 0L,
                                     selectedPageKey = selectedHomePageKey,
                                    clipboardPrompt = homeClipboardPrompt,
                                    onConfirmClipboardPrompt = { app.clipboardCodeCenter.confirmPendingPrompt() },
                                    onDismissClipboardPrompt = { app.clipboardCodeCenter.dismissPendingPrompt() },
                                    onSelectedPageKeyChange = { pageKey ->
                                        selectedHomePageKey = if (pageKey in homeBottomItems) pageKey else homeStartPageKey
                                    },
                                    onOpenWeatherDetail = {
                                        navController.navigate(AppRoutes.WeatherDetail)
                                    },
                                    onOpenNoteEditor = { noteId ->
                                        navController.navigate(AppRoutes.noteEditor(noteId))
                                    },
                                    onOpenQuickMemoDetail = { memoId ->
                                        navController.navigate(AppRoutes.quickMemoDetail(memoId))
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
                            NoteEditorRoute(
                                noteId = noteId,
                                newNoteId = AppRoutes.NoteEditorNewArg,
                                viewModel = mainViewModel,
                                onDismiss = { navController.popBackStack() },
                                onOpenImportedNote = { importedId ->
                                    navController.navigate(AppRoutes.noteEditor(importedId))
                                },
                                onShowMessage = { message, _ ->
                                    android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        composable(
                            route = AppRoutes.QuickMemoDetailPattern,
                            arguments = listOf(navArgument(AppRoutes.QuickMemoDetailArg) { type = NavType.LongType }),
                            enterTransition = { navForwardEnterTransition() },
                            exitTransition = { null },
                            popEnterTransition = { null },
                            popExitTransition = { navBackwardExitTransition() }
                        ) { backStackEntry ->
                            BackHandler(enabled = !predictiveBackEnabled) {
                                navController.popBackStack()
                            }
                            val memoId = backStackEntry.arguments?.getLong(AppRoutes.QuickMemoDetailArg) ?: -1L
                            val uiState by mainViewModel.uiState.collectAsState()
                            QuickMemoDetailPage(
                                memoId = memoId,
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() },
                                uiSize = uiState.settings.uiSize,
                                hapticEnabled = uiState.settings.hapticFeedbackEnabled,
                                backgroundMode = settings.appBackgroundImagePath.isNotBlank(),
                                miuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
                                cardAlphaPercent = settings.appBackgroundCardAlphaPercent
                            )
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
                            SettingsDetailRoute(
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

                LaunchedEffect(Unit) {
                    if (CrashHandler.isCrashedLastTime(this@MainActivity)) {
                        crashDialogShown = true
                    }
                }

                GlobalPromptHost(
                    state = globalPromptState,
                    floatingBottomPadding = floatingActionCardBottomPadding,
                    onAction = { action ->
                        when (action) {
                            is GlobalPromptUiAction.Confirm -> when (action.kind) {
                                GlobalPromptKind.PROMPT_UPDATE -> mainViewModel.confirmPromptUpdate()
                                GlobalPromptKind.LOCAL_MODEL_RESIDUE -> app.localModelResidueCenter.clearResidue()
                                GlobalPromptKind.CLIPBOARD_CODE -> app.clipboardCodeCenter.confirmPendingPrompt()
                                GlobalPromptKind.CRASH_REPORT -> handleCrashDismiss()
                                GlobalPromptKind.CLEANUP_REPORT -> cleanupDialogShown = false
                            }

                            is GlobalPromptUiAction.Dismiss -> when (action.kind) {
                                GlobalPromptKind.PROMPT_UPDATE -> mainViewModel.dismissPromptUpdate()
                                GlobalPromptKind.LOCAL_MODEL_RESIDUE -> app.localModelResidueCenter.dismissPendingPrompt()
                                GlobalPromptKind.CLIPBOARD_CODE -> app.clipboardCodeCenter.dismissPendingPrompt()
                                GlobalPromptKind.CRASH_REPORT -> handleCrashDismiss()
                                GlobalPromptKind.CLEANUP_REPORT -> cleanupDialogShown = false
                            }
                        }
                    }
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
        requestRecordAudioPermissionIfNeeded(intent)
        consumeWidgetAction(intent)
        consumeQuickMemoDetailIntent(intent)
        consumeEventDialogIntent(intent)
    }

    private fun requestRecordAudioPermissionIfNeeded(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, false) != true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
        intent.removeExtra(EXTRA_REQUEST_RECORD_AUDIO_PERMISSION)
    }

    private fun consumeWidgetAction(intent: Intent?) {
        val action = intent?.getStringExtra(WidgetActions.EXTRA_WIDGET_ACTION)?.takeIf { it.isNotBlank() } ?: return
        pendingWidgetAction.value = PendingWidgetLaunchAction(action)
    }

    private fun consumeQuickMemoDetailIntent(intent: Intent?) {
        val memoId = intent?.getLongExtra(EXTRA_OPEN_QUICK_MEMO_ID, -1L)?.takeIf { it > 0L } ?: return
        pendingQuickMemoDetailLaunch.value = PendingQuickMemoDetailLaunch(memoId)
        intent.removeExtra(EXTRA_OPEN_QUICK_MEMO_ID)
    }

    private fun consumeEventDialogIntent(intent: Intent?) {
        val eventId = intent?.getLongExtra(EXTRA_OPEN_EVENT_ID, -1L)?.takeIf { it > 0L } ?: return
        pendingEventDialogLaunch.value = PendingEventDialogLaunch(eventId)
        intent.removeExtra(EXTRA_OPEN_EVENT_ID)
    }

    override fun onResume() {
        super.onResume()
        val app = application as App
        if (::mainViewModel.isInitialized) {
            mainViewModel.refreshData()
        }
        app.runtimeCenter.startEdgeBarIfNeeded()
        app.runtimeCenter.restoreSmsNotificationListenerIfNeeded()
        app.localModelResidueCenter.checkForResidue()
        AccessibilityGuardian.checkAndRestoreIfNeeded(this, lifecycleScope)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            (application as App).clipboardCodeCenter.checkClipboardForPrompt("window_focus")
        }
    }

    private fun setupDynamicShortcuts() {
        val settings = (application as App).settingsQueryApi.settings.value
        val useLightShortcutIcons = when (settings.themeMode) {
            1 -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            3 -> true
            else -> false
        }
        val quickRecognitionIcon = if (useLightShortcutIcons) R.drawable.ic_qs_quick_recognition_light else R.drawable.ic_qs_quick_recognition
        val eventIcon = if (useLightShortcutIcons) R.drawable.ic_shortcut_event_light else R.drawable.ic_shortcut_event
        val quickMemoIcon = if (useLightShortcutIcons) R.drawable.ic_shortcut_quickmemo_light else R.drawable.ic_shortcut_quickmemo
        val voiceMemoIcon = if (useLightShortcutIcons) R.drawable.ic_shortcut_voice_memo_light else R.drawable.ic_shortcut_voice_memo

        fun shortcutIntent(actionName: String) = Intent(this, com.antgskds.calendarassistant.core.service.shortcut.ShortcutHandleActivity::class.java).apply {
            action = actionName
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val quickCaptureShortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "quick_capture")
            .setShortLabel(getString(R.string.shortcut_quick_recognition))
            .setLongLabel(getString(R.string.shortcut_quick_recognition_long))
            .setIcon(createShortcutIcon(quickRecognitionIcon, useLightShortcutIcons))
            .setIntent(shortcutIntent(com.antgskds.calendarassistant.core.service.shortcut.ShortcutHandleActivity.ACTION_QUICK_CAPTURE))
            .setRank(0)
            .build()

        val floatingShortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "open_floating")
            .setShortLabel(getString(R.string.shortcut_open_floating))
            .setLongLabel(getString(R.string.shortcut_open_floating_long))
            .setIcon(createShortcutIcon(eventIcon, useLightShortcutIcons))
            .setIntent(shortcutIntent(com.antgskds.calendarassistant.core.service.shortcut.ShortcutHandleActivity.ACTION_OPEN_FLOATING))
            .setRank(1)
            .build()

        val quickMemoShortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "open_floating_quick_memo")
            .setShortLabel(getString(R.string.shortcut_open_quick_memo))
            .setLongLabel(getString(R.string.shortcut_open_quick_memo_long))
            .setIcon(createShortcutIcon(quickMemoIcon, useLightShortcutIcons))
            .setIntent(shortcutIntent(com.antgskds.calendarassistant.core.service.shortcut.ShortcutHandleActivity.ACTION_OPEN_FLOATING_NOTE))
            .setRank(2)
            .build()

        val startQuickMemoVoiceShortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "start_quick_memo_voice")
            .setShortLabel(getString(R.string.shortcut_start_quick_memo_voice))
            .setLongLabel(getString(R.string.shortcut_start_quick_memo_voice_long))
            .setIcon(createShortcutIcon(voiceMemoIcon, useLightShortcutIcons))
            .setIntent(shortcutIntent(com.antgskds.calendarassistant.core.service.shortcut.ShortcutHandleActivity.ACTION_START_QUICK_MEMO_VOICE))
            .setRank(3)
            .build()

        val shortcuts = listOf(quickCaptureShortcut, floatingShortcut, quickMemoShortcut, startQuickMemoVoiceShortcut)
        ShortcutManagerCompat.removeDynamicShortcuts(
            this,
            listOf("quick_capture", "open_floating", "open_floating_quick_memo", "start_quick_memo_voice")
        )
        ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts)
        ShortcutManagerCompat.updateShortcuts(this, shortcuts)
    }

    private fun createShortcutIcon(
        @DrawableRes iconRes: Int,
        useLightIcon: Boolean
    ): IconCompat {
        val drawable = ContextCompat.getDrawable(this, iconRes)?.mutate()
            ?: return IconCompat.createWithResource(this, iconRes)
        val wrapped = DrawableCompat.wrap(drawable).mutate()
        val tintColor = if (useLightIcon) {
            android.graphics.Color.rgb(244, 239, 244)
        } else {
            android.graphics.Color.rgb(29, 27, 32)
        }
        DrawableCompat.setTint(wrapped, tintColor)

        val density = resources.displayMetrics.density
        val sizePx = (48f * density).roundToInt().coerceAtLeast(1)
        val paddingPx = (8f * density).roundToInt().coerceAtLeast(0)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        wrapped.setBounds(paddingPx, paddingPx, sizePx - paddingPx, sizePx - paddingPx)
        wrapped.draw(canvas)
        return IconCompat.createWithBitmap(bitmap)
    }

    companion object {
        private const val ONBOARDING_PREFS_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val EXTRA_REQUEST_RECORD_AUDIO_PERMISSION = "request_record_audio_permission"
        const val EXTRA_OPEN_QUICK_MEMO_ID = "open_quick_memo_id"
        const val EXTRA_OPEN_EVENT_ID = "open_event_id"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 2401
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

@Composable
private fun AppBackgroundLayer(
    enabled: Boolean,
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val blurRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
    val bitmap = imageBitmap ?: return
    Box(modifier = modifier) {
        AppWallpaperImage(
            imageBitmap = bitmap,
            scale = imageScale,
            offsetX = imageOffsetX,
            offsetY = imageOffsetY,
            blurRadiusPx = if (blurEnabled) blurRadiusPx else 0f,
            modifier = Modifier.fillMaxSize()
        )
    }
}
