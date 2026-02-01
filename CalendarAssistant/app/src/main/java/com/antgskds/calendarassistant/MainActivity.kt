package com.antgskds.calendarassistant

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.page_display.HomeScreen
import com.antgskds.calendarassistant.ui.page_display.SettingsDetailScreen
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantTheme
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

// 固定 UI 密度（常量）
private const val FIXED_DENSITY_DPI = 380  // 默认密度

// UI 大小对应的 fontScale
private fun getFontScaleForUiSize(uiSize: Int): Float {
    return when (uiSize) {
        1 -> 1.0f   // 小
        2 -> 1.15f  // 中（默认）
        3 -> 1.32f  // 大
        else -> 1.15f
    }
}

class MainActivity : ComponentActivity() {
    // 【修改 1】将 Boolean 改为 Long，默认值为 0L
    private var pickupEventTimestamp = mutableStateOf(0L)

    // ViewModel 实例，供 onResume 使用
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 【修改 2】检查启动 Intent，如果是取件码，设置当前时间戳
        if (intent.getBooleanExtra("openPickupList", false)) {
            pickupEventTimestamp.value = System.currentTimeMillis()
        }

        // 强制使用固定的 DPI，确保在不同设备上 UI 尺寸一致
        // 首次创建使用默认 UI 大小（中），后续会根据用户设置调整
        if (currentUiSize == -1) {
            currentUiSize = 2  // 默认中等大小
        }
        forceFixedDensity(currentUiSize)

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

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val settings by settingsViewModel.settings.collectAsState()

            // 监听 UI 大小变化，如果变化则重启 Activity 应用新设置
            LaunchedEffect(settings.uiSize) {
                if (currentUiSize != settings.uiSize) {
                    currentUiSize = settings.uiSize
                    // 重新应用字体缩放
                    forceFixedDensity(settings.uiSize)
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
                            // 【修改 3】传入时间戳，而不是 Boolean
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
        // 【修改 4】每次收到新 Intent，如果是取件码，更新时间戳（这保证了值一定会变）
        if (intent.getBooleanExtra("openPickupList", false)) {
            pickupEventTimestamp.value = System.currentTimeMillis()
        }
    }

    /**
     * 强制使用固定的 DPI 和字体缩放
     * @param uiSize UI 大小：1=小, 2=中, 3=大
     */
    private fun forceFixedDensity(uiSize: Int = 2) {
        val config = Configuration(resources.configuration)
        config.densityDpi = FIXED_DENSITY_DPI
        config.fontScale = getFontScaleForUiSize(uiSize)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    companion object {
        // 用于追踪当前应用的 UI 大小，避免不必要的重启
        private var currentUiSize: Int = -1
    }
}
