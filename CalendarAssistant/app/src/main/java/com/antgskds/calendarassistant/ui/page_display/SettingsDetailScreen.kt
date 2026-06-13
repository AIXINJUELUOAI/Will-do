package com.antgskds.calendarassistant.ui.page_display

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.components.SettingsSidebar
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.layout.PushSlideLayout
import com.antgskds.calendarassistant.ui.navigation.navBackwardEnterTransition
import com.antgskds.calendarassistant.ui.navigation.navBackwardExitTransition
import com.antgskds.calendarassistant.ui.navigation.navForwardEnterTransition
import com.antgskds.calendarassistant.ui.navigation.navForwardExitTransition
import com.antgskds.calendarassistant.ui.page_display.settings.AboutPage
import com.antgskds.calendarassistant.ui.page_display.settings.AppUpdatePage
import com.antgskds.calendarassistant.ui.page_display.settings.AiSettingsPage
import com.antgskds.calendarassistant.ui.page_display.settings.ArchivesPage
import com.antgskds.calendarassistant.ui.page_display.settings.BackupSettingsPage
import com.antgskds.calendarassistant.ui.page_display.settings.BottomBarEditorPage
import com.antgskds.calendarassistant.ui.page_display.settings.CourseManagerScreen
import com.antgskds.calendarassistant.ui.page_display.settings.DonatePage
import com.antgskds.calendarassistant.ui.page_display.settings.LaboratoryPage
import com.antgskds.calendarassistant.ui.page_display.settings.PreferenceSettingsPage
import com.antgskds.calendarassistant.ui.page_display.settings.ScheduleSettingsPage
import com.antgskds.calendarassistant.ui.page_display.settings.ThemeSettingsPage
import com.antgskds.calendarassistant.ui.page_display.settings.TimeTableEditorScreen
import com.antgskds.calendarassistant.ui.page_display.settings.WeatherDetailPage
import com.antgskds.calendarassistant.ui.page_display.settings.WeatherSettingsPage
import com.antgskds.calendarassistant.ui.page_display.settings.WidgetSettingsPage
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

private object SettingsRoutes {
    const val Ai = "settings_ai"
    const val Weather = "settings_weather"
    const val WeatherDetail = "settings_weather_detail"
    const val Schedule = "settings_schedule"
    const val CourseManage = "settings_course_manage"
    const val TimeTableManage = "settings_timetable_manage"
    const val SemesterConfig = "settings_semester_config"
    const val Preference = "settings_preference"
    const val Archives = "settings_archives"
    const val Backup = "settings_backup"
    const val AppUpdate = "settings_app_update"
    const val Theme = "settings_theme"
    const val About = "settings_about"
    const val Donate = "settings_donate"
    const val Laboratory = "settings_laboratory"
    const val BottomBarEditor = "settings_bottom_bar_editor"
    const val WidgetSettings = "settings_widget_settings"
}

private fun parseSettingsDestination(value: String): SettingsDestination {
    return when (value) {
        "course_manager" -> SettingsDestination.CourseManage
        "timetable_editor" -> SettingsDestination.TimeTableManage
        else -> SettingsDestination.entries.firstOrNull { it.name == value } ?: SettingsDestination.Preference
    }
}

private fun SettingsDestination.toSettingsRoute(): String? {
    return when (this) {
        SettingsDestination.AI -> SettingsRoutes.Ai
        SettingsDestination.Weather -> SettingsRoutes.Weather
        SettingsDestination.Schedule -> SettingsRoutes.Schedule
        SettingsDestination.CourseManage -> SettingsRoutes.CourseManage
        SettingsDestination.TimeTableManage -> SettingsRoutes.TimeTableManage
        SettingsDestination.SemesterConfig -> SettingsRoutes.SemesterConfig
        SettingsDestination.Preference -> SettingsRoutes.Preference
        SettingsDestination.Archives -> SettingsRoutes.Archives
        SettingsDestination.Backup -> SettingsRoutes.Backup
        SettingsDestination.AppUpdate -> SettingsRoutes.AppUpdate
        SettingsDestination.Theme -> SettingsRoutes.Theme
        SettingsDestination.About -> SettingsRoutes.About
        SettingsDestination.Donate -> SettingsRoutes.Donate
        SettingsDestination.Laboratory -> SettingsRoutes.Laboratory
        SettingsDestination.BottomBarEditor -> SettingsRoutes.BottomBarEditor
        SettingsDestination.WidgetSettings -> SettingsRoutes.WidgetSettings
        SettingsDestination.Logout -> null
    }
}

private fun routeToSettingsDestination(route: String): SettingsDestination {
    return when (route.substringBefore('?')) {
        SettingsRoutes.Ai -> SettingsDestination.AI
        SettingsRoutes.Weather -> SettingsDestination.Weather
        SettingsRoutes.WeatherDetail -> SettingsDestination.Weather
        SettingsRoutes.Schedule -> SettingsDestination.Schedule
        SettingsRoutes.CourseManage -> SettingsDestination.CourseManage
        SettingsRoutes.TimeTableManage -> SettingsDestination.TimeTableManage
        SettingsRoutes.SemesterConfig -> SettingsDestination.SemesterConfig
        SettingsRoutes.Preference -> SettingsDestination.Preference
        SettingsRoutes.Archives -> SettingsDestination.Archives
        SettingsRoutes.Backup -> SettingsDestination.Backup
        SettingsRoutes.AppUpdate -> SettingsDestination.AppUpdate
        SettingsRoutes.Theme -> SettingsDestination.Theme
        SettingsRoutes.About -> SettingsDestination.About
        SettingsRoutes.Donate -> SettingsDestination.Donate
        SettingsRoutes.Laboratory -> SettingsDestination.Laboratory
        SettingsRoutes.BottomBarEditor -> SettingsDestination.BottomBarEditor
        SettingsRoutes.WidgetSettings -> SettingsDestination.WidgetSettings
        else -> SettingsDestination.Preference
    }
}

private fun settingsTitle(destination: SettingsDestination): String {
    return when (destination) {
        SettingsDestination.AI -> "模型配置"
        SettingsDestination.Weather -> "天气"
        SettingsDestination.Schedule -> "课表设置"
        SettingsDestination.CourseManage -> "课程管理"
        SettingsDestination.TimeTableManage -> "作息表管理"
        SettingsDestination.SemesterConfig -> "学期配置"
        SettingsDestination.Preference -> "偏好设置"
        SettingsDestination.Archives -> "归档"
        SettingsDestination.Backup -> "数据备份"
        SettingsDestination.AppUpdate -> "软件更新"
        SettingsDestination.Theme -> "主题设置"
        SettingsDestination.About -> "关于应用"
        SettingsDestination.Donate -> "捐赠开发者"
        SettingsDestination.Laboratory -> "实验室"
        SettingsDestination.BottomBarEditor -> "底栏编辑"
        SettingsDestination.WidgetSettings -> "桌面小组件"
        SettingsDestination.Logout -> "退出应用"
    }
}

private fun NavGraphBuilder.settingsPageComposable(
    route: String,
    content: @Composable () -> Unit
) {
    composable(
        route = route,
        enterTransition = { navForwardEnterTransition() },
        exitTransition = { navForwardExitTransition() },
        popEnterTransition = { navBackwardEnterTransition() },
        popExitTransition = { navBackwardExitTransition() }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageContent(
    destination: SettingsDestination,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    uiSize: Int,
    onBack: () -> Unit,
    onNavigateTo: (SettingsDestination) -> Unit,
    onNavigateRoute: (String) -> Unit = {},
    titleOverride: String? = null,
    contentOverride: (@Composable () -> Unit)? = null
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val haptics = rememberAppHaptics(uiState.settings.hapticFeedbackEnabled)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val courseCount = remember(destination, uiState.rawEvents, uiState.settings) {
        if (destination == SettingsDestination.CourseManage) {
            CourseEventMapper.extractParentCourses(uiState.rawEvents, uiState.settings).size
        } else {
            0
        }
    }
    var showClearCoursesConfirm by rememberSaveable(destination) { mutableStateOf(false) }

    when (destination) {
        SettingsDestination.Archives -> ArchivesPage(
            viewModel = mainViewModel,
            onBack = onBack
        )

        else -> Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(titleOverride ?: settingsTitle(destination)) },
                        navigationIcon = {
                            IconButton(onClick = { haptics.click(); onBack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    modifier = Modifier.size(
                                        when (uiSize) {
                                            1 -> 24.dp
                                            2 -> 28.dp
                                            else -> 32.dp
                                        }
                                    )
                                )
                            }
                        },
                        actions = {
                            if (destination == SettingsDestination.CourseManage && courseCount > 0) {
                                IconButton(onClick = { showClearCoursesConfirm = true }) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "清空课程",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    if (contentOverride != null) {
                        contentOverride()
                    } else when (destination) {
                        SettingsDestination.AI -> AiSettingsPage(
                            viewModel = settingsViewModel,
                            mainViewModel = mainViewModel,
                            uiSize = uiSize
                        )
                        SettingsDestination.Weather -> WeatherSettingsPage(
                            viewModel = settingsViewModel,
                            uiSize = uiSize,
                            onOpenWeatherDetail = { onNavigateRoute(SettingsRoutes.WeatherDetail) }
                        )
                        SettingsDestination.Schedule,
                        SettingsDestination.SemesterConfig -> ScheduleSettingsPage(
                            viewModel = settingsViewModel,
                            onNavigateTo = onNavigateTo,
                            uiSize = uiSize
                        )
                        SettingsDestination.CourseManage -> CourseManagerScreen(mainViewModel, uiSize)
                        SettingsDestination.TimeTableManage -> TimeTableEditorScreen(settingsViewModel, uiSize)
                        SettingsDestination.Preference -> PreferenceSettingsPage(
                            viewModel = settingsViewModel,
                            uiSize = uiSize,
                            onNavigateToBottomBarEditor = { onNavigateTo(SettingsDestination.BottomBarEditor) },
                            onNavigateToWidgetSettings = { onNavigateTo(SettingsDestination.WidgetSettings) },
                            onNavigateToSemesterConfig = { onNavigateTo(SettingsDestination.SemesterConfig) },
                            onNavigateToCourseManage = { onNavigateTo(SettingsDestination.CourseManage) },
                            onNavigateToTimeTableManage = { onNavigateTo(SettingsDestination.TimeTableManage) }
                        )
                        SettingsDestination.Backup -> BackupSettingsPage(settingsViewModel, mainViewModel, uiSize)
                        SettingsDestination.AppUpdate -> AppUpdatePage(mainViewModel, uiSize)
                        SettingsDestination.About -> AboutPage(
                            uiSize = uiSize,
                            onNavigateToDonate = { onNavigateTo(SettingsDestination.Donate) },
                            settingsViewModel = settingsViewModel
                        )
                        SettingsDestination.Donate -> DonatePage(uiSize, settingsViewModel)
                        SettingsDestination.Laboratory -> LaboratoryPage(
                            uiSize = uiSize,
                            settingsViewModel = settingsViewModel,
                            mainViewModel = mainViewModel
                        )
                        SettingsDestination.BottomBarEditor -> BottomBarEditorPage(
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize
                        )
                        SettingsDestination.WidgetSettings -> WidgetSettingsPage(
                            settingsViewModel = settingsViewModel,
                            rawEvents = uiState.rawEvents,
                            uiSize = uiSize
                        )
                        SettingsDestination.Theme -> ThemeSettingsPage(settingsViewModel, uiSize)
                        SettingsDestination.Archives,
                        SettingsDestination.Logout -> Unit
                    }
                }
            }

            PredictiveFloatingActionCard(
                visible = showClearCoursesConfirm,
                title = "确认清空",
                content = "此操作将删除当前 $courseCount 门课程。\n删除后将无法恢复。",
                confirmText = "删除",
                dismissText = "取消",
                isDestructive = true,
                isLoading = false,
                predictiveBackEnabled = uiState.settings.predictiveBackEnabled,
                onConfirm = {
                    showClearCoursesConfirm = false
                    mainViewModel.clearAllCourses()
                },
                onDismiss = { showClearCoursesConfirm = false },
                modifier = Modifier
                    .padding(bottom = bottomInset)
            )
        }
    }
}

@Composable
fun SettingsDetailScreen(
    destinationStr: String,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onExitSettings: () -> Unit,
    onLogout: () -> Unit,
    uiSize: Int = 2
) {
    val settings by settingsViewModel.settings.collectAsState()
    val appUpdateUiState by mainViewModel.appUpdateUiState.collectAsState()
    val settingsNavController = rememberNavController()
    val initialDestination = remember(destinationStr) { parseSettingsDestination(destinationStr) }
    val initialRoute = remember(destinationStr) {
        initialDestination.toSettingsRoute() ?: SettingsRoutes.Preference
    }
    val backStackEntry by settingsNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: initialRoute

    var isSidebarOpen by remember { mutableStateOf(false) }

    fun navigateToDestination(target: SettingsDestination) {
        if (target == SettingsDestination.Logout) {
            isSidebarOpen = false
            onLogout()
            return
        }

        val targetRoute = target.toSettingsRoute() ?: return
        if (targetRoute == currentRoute) {
            isSidebarOpen = false
            return
        }

        isSidebarOpen = false
        settingsNavController.navigate(targetRoute) {
            launchSingleTop = true
        }
    }

    fun navigateToRoute(targetRoute: String) {
        if (targetRoute == currentRoute) return
        settingsNavController.navigate(targetRoute) {
            launchSingleTop = true
        }
    }

    fun handleBackNavigation() {
        when {
            isSidebarOpen -> isSidebarOpen = false
            settingsNavController.previousBackStackEntry != null -> settingsNavController.popBackStack()
            else -> onExitSettings()
        }
    }

    PushSlideLayout(
        isOpen = isSidebarOpen,
        onOpenChange = { isSidebarOpen = it },
        enableGesture = true,
        sidebar = {
            SettingsSidebar(
                isDarkMode = settings.isDarkMode,
                hasAppUpdate = appUpdateUiState.hasUpdate,
                onThemeToggle = { isDark -> settingsViewModel.updateDarkMode(isDark) },
                onNavigate = { destination -> navigateToDestination(destination) }
            )
        },
        bottomBar = {},
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                NavHost(
                    navController = settingsNavController,
                    startDestination = initialRoute,
                    modifier = Modifier.fillMaxSize()
                ) {
                    settingsPageComposable(SettingsRoutes.Ai) {
                        SettingsPageContent(
                            destination = SettingsDestination.AI,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) },
                            onNavigateRoute = { route -> navigateToRoute(route) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.WeatherDetail) {
                        SettingsPageContent(
                            destination = SettingsDestination.Weather,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) },
                            titleOverride = "天气详情",
                            contentOverride = { WeatherDetailPage(uiSize = uiSize) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.Weather) {
                        SettingsPageContent(
                            destination = SettingsDestination.Weather,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) },
                            onNavigateRoute = { route -> navigateToRoute(route) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.Schedule) {
                        SettingsPageContent(
                            destination = SettingsDestination.Schedule,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.CourseManage) {
                        SettingsPageContent(
                            destination = SettingsDestination.CourseManage,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.TimeTableManage) {
                        SettingsPageContent(
                            destination = SettingsDestination.TimeTableManage,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.SemesterConfig) {
                        SettingsPageContent(
                            destination = SettingsDestination.SemesterConfig,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.Preference) {
                        SettingsPageContent(
                            destination = SettingsDestination.Preference,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.Archives) {
                        SettingsPageContent(
                            destination = SettingsDestination.Archives,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.Backup) {
                        SettingsPageContent(
                            destination = SettingsDestination.Backup,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.AppUpdate) {
                        SettingsPageContent(
                            destination = SettingsDestination.AppUpdate,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.Theme) {
                        SettingsPageContent(
                            destination = SettingsDestination.Theme,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.About) {
                        SettingsPageContent(
                            destination = SettingsDestination.About,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.Donate) {
                        SettingsPageContent(
                            destination = SettingsDestination.Donate,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.Laboratory) {
                        SettingsPageContent(
                            destination = SettingsDestination.Laboratory,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.BottomBarEditor) {
                        SettingsPageContent(
                            destination = SettingsDestination.BottomBarEditor,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                    settingsPageComposable(SettingsRoutes.WidgetSettings) {
                        SettingsPageContent(
                            destination = SettingsDestination.WidgetSettings,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            uiSize = uiSize,
                            onBack = { handleBackNavigation() },
                            onNavigateTo = { target -> navigateToDestination(target) }
                        )
                    }
                }

            }
        }
    )

    BackHandler(enabled = isSidebarOpen || !settings.predictiveBackEnabled) {
        handleBackNavigation()
    }
}
