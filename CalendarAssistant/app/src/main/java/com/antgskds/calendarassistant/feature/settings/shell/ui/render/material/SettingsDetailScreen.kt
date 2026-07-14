package com.antgskds.calendarassistant.feature.settings.shell.ui.render.material

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.antgskds.calendarassistant.shared.management.catalog.PageCatalog
import com.antgskds.calendarassistant.shared.ui.material.component.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.component.SettingsSidebar
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiAction
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiState
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.layout.PushSlideLayout
import com.antgskds.calendarassistant.app.ui.navigation.navBackwardEnterTransition
import com.antgskds.calendarassistant.app.ui.navigation.navBackwardExitTransition
import com.antgskds.calendarassistant.app.ui.navigation.navForwardEnterTransition
import com.antgskds.calendarassistant.app.ui.navigation.navForwardExitTransition
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme

private fun NavGraphBuilder.settingsPageComposable(
    route: String,
    content: @Composable () -> Unit,
) {
    composable(
        route = route,
        enterTransition = { navForwardEnterTransition() },
        exitTransition = { navForwardExitTransition() },
        popEnterTransition = { navBackwardEnterTransition() },
        popExitTransition = { navBackwardExitTransition() },
    ) {
        content()
    }
}

@Composable
fun MaterialSettingsDetailScreen(
    state: SettingsDetailUiState,
    onAction: (SettingsDetailUiAction) -> Unit,
    pageContent: @Composable (
        route: String,
        destination: SettingsDestination,
        onNavigateTo: (SettingsDestination) -> Unit,
        onNavigateRoute: (String) -> Unit,
    ) -> Unit,
) {
    val settingsNavController = rememberNavController()
    val backStackEntry by settingsNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: state.initialRoute
    var isSidebarOpen by remember { mutableStateOf(false) }

    fun navigateToDestination(target: SettingsDestination) {
        if (target == SettingsDestination.Logout) {
            isSidebarOpen = false
            onAction(SettingsDetailUiAction.Logout)
            return
        }

        val targetRoute = PageCatalog.routeFor(target) ?: return
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
            else -> onAction(SettingsDetailUiAction.ExitSettings)
        }
    }

    AppBackgroundStyleTheme(
        enabled = state.backgroundEnabled,
        miuiBlurEnabled = state.backgroundMiuiBlurEnabled,
        cardAlphaPercent = state.backgroundCardAlphaPercent,
    ) {
        PushSlideLayout(
            isOpen = isSidebarOpen,
            onOpenChange = { isSidebarOpen = it },
            enableGesture = true,
            contentContainerColor = if (state.backgroundEnabled) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            },
            sidebar = {
                SettingsSidebar(
                    isDarkMode = state.isDarkMode,
                    glassMode = state.backgroundEnabled,
                    hasAppUpdate = state.hasAppUpdate,
                    onThemeToggle = { isDark ->
                        onAction(SettingsDetailUiAction.SetDarkMode(isDark))
                    },
                    onNavigate = ::navigateToDestination,
                )
            },
            bottomBar = {},
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                ) {
                    NavHost(
                        navController = settingsNavController,
                        startDestination = state.initialRoute,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        PageCatalog.navigablePages.forEach { page ->
                            val route = requireNotNull(page.route)
                            settingsPageComposable(route) {
                                MaterialSettingsPage(
                                    route = route,
                                    destination = page.destination,
                                    title = page.title,
                                    state = state,
                                    onBack = ::handleBackNavigation,
                                    onAction = onAction,
                                ) {
                                    pageContent(
                                        route,
                                        page.destination,
                                        ::navigateToDestination,
                                        ::navigateToRoute,
                                    )
                                }
                            }
                        }

                        PageCatalog.weatherDetailPage.let { page ->
                            settingsPageComposable(page.route) {
                                MaterialSettingsPage(
                                    route = page.route,
                                    destination = page.parentDestination,
                                    title = page.title,
                                    state = state,
                                    onBack = ::handleBackNavigation,
                                    onAction = onAction,
                                ) {
                                    pageContent(
                                        page.route,
                                        page.parentDestination,
                                        ::navigateToDestination,
                                        ::navigateToRoute,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    BackHandler(enabled = isSidebarOpen || !state.navigationPredictiveBackEnabled) {
        handleBackNavigation()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialSettingsPage(
    route: String,
    destination: SettingsDestination,
    title: String,
    state: SettingsDetailUiState,
    onBack: () -> Unit,
    onAction: (SettingsDetailUiAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = rememberAppHaptics(state.hapticEnabled)
    val pageContainerColor = if (state.pageHasAppBackground) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showClearCoursesConfirm by rememberSaveable(route) { mutableStateOf(false) }
    var showClearArchivesConfirm by rememberSaveable(route) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = pageContainerColor,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = pageContainerColor,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptics.click()
                                onBack()
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                modifier = Modifier.size(
                                    when (state.uiSize) {
                                        1 -> 24.dp
                                        2 -> 28.dp
                                        else -> 32.dp
                                    },
                                ),
                            )
                        }
                    },
                    actions = {
                        if (destination == SettingsDestination.CourseManage && state.courseCount > 0) {
                            IconButton(onClick = { showClearCoursesConfirm = true }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "清空课程",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        if (destination == SettingsDestination.Archives && state.archiveCount > 0) {
                            IconButton(
                                onClick = {
                                    haptics.click()
                                    showClearArchivesConfirm = true
                                },
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "清空归档",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                content()
            }
        }

        PredictiveFloatingActionCard(
            visible = showClearCoursesConfirm,
            title = "确认清空",
            content = "此操作将删除当前 ${state.courseCount} 门课程。\n删除后将无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            isLoading = false,
            predictiveBackEnabled = state.confirmationPredictiveBackEnabled,
            onConfirm = {
                showClearCoursesConfirm = false
                onAction(SettingsDetailUiAction.ClearCourses)
            },
            onDismiss = { showClearCoursesConfirm = false },
            modifier = Modifier.padding(bottom = bottomInset),
        )

        PredictiveFloatingActionCard(
            visible = showClearArchivesConfirm,
            title = "确认清空",
            content = "此操作将永久删除 ${state.archiveCount} 条归档事件。\n删除后将无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            isLoading = false,
            predictiveBackEnabled = state.confirmationPredictiveBackEnabled,
            onConfirm = {
                showClearArchivesConfirm = false
                onAction(SettingsDetailUiAction.ClearArchives)
            },
            onDismiss = { showClearArchivesConfirm = false },
            modifier = Modifier.padding(bottom = bottomInset),
        )
    }
}
