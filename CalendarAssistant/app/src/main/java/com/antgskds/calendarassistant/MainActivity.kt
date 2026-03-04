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
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import com.antgskds.calendarassistant.core.util.DensityConfigManager
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.page_display.HomeScreen
import com.antgskds.calendarassistant.ui.page_display.SettingsDetailScreen
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantTheme
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    // 取件码时间戳
    private var pickupEventTimestamp = mutableStateOf(0L)

    // ViewModel 实例，供 onResume 使用
    private lateinit var mainViewModel: MainViewModel

    /**
     * 覆写 attachBaseContext 以应用自定义密度缩放
     * 基于设备原生 DPI，根据用户设置的 uiSize 应用相对缩放
     */
    override fun attachBaseContext(newBase: Context) {
        // 1. 获取用户设置的 uiSize (兼容老用户：优先读独立 Key，回退读 JSON)
        val uiSizeIndex = DensityConfigManager.getUiSizeFromPrefs(newBase)

        // 2. 获取系统原始参数
        val systemMetrics = Resources.getSystem().displayMetrics
        val systemConfig = Resources.getSystem().configuration
        val scale = DensityConfigManager.getScaleFactor(uiSizeIndex)

        // 3. 计算目标值
        val targetDensity = systemMetrics.density * scale
        val targetDpi = (systemMetrics.densityDpi * scale).toInt()
        // 关键：保留系统字体缩放偏好
        val targetScaledDensity = targetDensity * systemConfig.fontScale

        // 4. 构建新的 Configuration
        val config = Configuration(newBase.resources.configuration)
        config.densityDpi = targetDpi
        config.fontScale = systemConfig.fontScale

        // 5. 生成并应用新的 Context
        val newContext = newBase.createConfigurationContext(config)

        // 6. 手动修复 DisplayMetrics (双重保险)
        newContext.resources.displayMetrics.apply {
            density = targetDensity
            scaledDensity = targetScaledDensity
            densityDpi = targetDpi
        }

        super.attachBaseContext(newContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查启动 Intent，如果是取件码，设置当前时间戳
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

        // 初始化 MainViewModel 供 onResume 使用
        mainViewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        // 初始化动态快捷方式
        setupDynamicShortcuts()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val settings by settingsViewModel.settings.collectAsState()

            // 监听 UI 大小变化，如果变化则重启 Activity 应用新设置
            LaunchedEffect(settings.uiSize) {
                if (currentUiSize != settings.uiSize) {
                    currentUiSize = settings.uiSize
                    // 重启 Activity 使设置生效
                    recreate()
                }
            }

            CalendarAssistantTheme(darkTheme = settings.isDarkMode) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        window.statusBarColor = Color.Transparent.toArgb()
                        window.navigationBarColor = Color.Transparent.toArgb()
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !settings.isDarkMode
                        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !settings.isDarkMode
                    }
                }

                // 使用外部初始化的 mainViewModel，避免创建多个实例
                val navController = rememberNavController()

                NavHost(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable(
                        route = "home",
                        enterTransition = {
                            // 进入主页时：从左滑入（较慢）
                            slideInHorizontally(
                                initialOffsetX = { width: Int -> -width },
                                animationSpec = tween(
                                    durationMillis = 450,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        },
                        exitTransition = {
                            // 离开主页进入详情页时：向左快速滑出（被挤出屏幕）
                            slideOutHorizontally(
                                targetOffsetX = { width: Int -> -width },
                                animationSpec = tween(
                                    durationMillis = 220,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        },
                        popEnterTransition = {
                            // 返回主页时：从左滑入（较慢）
                            slideInHorizontally(
                                initialOffsetX = { width: Int -> -width },
                                animationSpec = tween(
                                    durationMillis = 450,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        },
                        popExitTransition = {
                            // 主页返回到其他页面时：不需要特殊处理
                            null
                        }
                    ) {
                        HomeScreen(
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            // 传入时间戳，而不是 Boolean
                            pickupTimestamp = pickupEventTimestamp.value,
                            onNavigateToSettings = { destination ->
                                // 处理退出登录操作
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
                        enterTransition = {
                            // 进入详情页时：从右滑入（较慢）
                            slideInHorizontally(
                                initialOffsetX = { width: Int -> width },
                                animationSpec = tween(
                                    durationMillis = 450,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        },
                        exitTransition = {
                            // 离开详情页到更深层页面时：不需要特殊处理
                            null
                        },
                        popEnterTransition = {
                            // 从更深层页面返回详情页时：不需要特殊处理
                            null
                        },
                        popExitTransition = {
                            // 返回主页时：向右快速滑出（被挤出屏幕）
                            slideOutHorizontally(
                                targetOffsetX = { width: Int -> width },
                                animationSpec = tween(
                                    durationMillis = 220,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    ) { backStackEntry ->
                        val typeName = backStackEntry.arguments?.getString("type") ?: ""

                        SettingsDetailScreen(
                            destinationStr = typeName,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateTo = { route -> navController.navigate(route) },
                            uiSize = settings.uiSize
                        )
                    }
                }
            }
        }
    }

    /**
     * 每次回到前台时刷新数据
     * 确保归档后的状态能及时更新到 UI
     */
    override fun onResume() {
        super.onResume()
        // 刷新数据：触发自动归档 + 强制 UI 重组
        if (::mainViewModel.isInitialized) {
            mainViewModel.refreshData()
        }
    }

    /**
     * 处理从通知/胶囊点击进入时的新 Intent
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // 每次收到新 Intent，如果是取件码，更新时间戳
        if (intent.getBooleanExtra("openPickupList", false)) {
            pickupEventTimestamp.value = System.currentTimeMillis()
        }
    }

    /**
     * 设置动态快捷方式
     */
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
        // 用于追踪当前应用的 UI 大小，避免不必要的重启
        private var currentUiSize: Int = -1
    }
}
