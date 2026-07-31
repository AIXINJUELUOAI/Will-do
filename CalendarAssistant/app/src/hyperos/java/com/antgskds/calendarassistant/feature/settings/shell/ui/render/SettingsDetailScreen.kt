package com.antgskds.calendarassistant.feature.settings.shell.ui.render

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.app.ui.navigation.navBackwardEnterTransition
import com.antgskds.calendarassistant.app.ui.navigation.navBackwardExitTransition
import com.antgskds.calendarassistant.app.ui.navigation.navForwardEnterTransition
import com.antgskds.calendarassistant.app.ui.navigation.navForwardExitTransition
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiAction
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiState
import com.antgskds.calendarassistant.shared.management.catalog.PageCatalog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun NavGraphBuilder.hyperSettingsPage(
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
fun SettingsDetailScreenContent(
    state: SettingsDetailUiState,
    onAction: (SettingsDetailUiAction) -> Unit,
    pageContent: @Composable (
        route: String,
        destination: SettingsDestination,
        onNavigateTo: (SettingsDestination) -> Unit,
        onNavigateRoute: (String) -> Unit,
    ) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: state.initialRoute

    fun navigateTo(destination: SettingsDestination) {
        if (destination == SettingsDestination.Logout) {
            onAction(SettingsDetailUiAction.Logout)
            return
        }
        val route = PageCatalog.routeFor(destination) ?: return
        if (route != currentRoute) navController.navigate(route) { launchSingleTop = true }
    }

    fun navigateRoute(route: String) {
        if (route != currentRoute) navController.navigate(route) { launchSingleTop = true }
    }

    fun navigateBack() {
        if (!navController.popBackStack()) onAction(SettingsDetailUiAction.ExitSettings)
    }

    AppBackgroundStyleTheme(
        enabled = state.backgroundEnabled,
        miuiBlurEnabled = false,
        cardAlphaPercent = 100,
    ) {
        NavHost(
            navController = navController,
            startDestination = state.initialRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            PageCatalog.navigablePages.forEach { page ->
                val route = requireNotNull(page.route)
                hyperSettingsPage(route) {
                    HyperSettingsDetailPage(
                        route = route,
                        destination = page.destination,
                        title = page.title,
                        state = state,
                        onBack = ::navigateBack,
                        onAction = onAction,
                    ) {
                        pageContent(route, page.destination, ::navigateTo, ::navigateRoute)
                    }
                }
            }
            PageCatalog.weatherDetailPage.let { page ->
                hyperSettingsPage(page.route) {
                    HyperSettingsDetailPage(
                        route = page.route,
                        destination = page.parentDestination,
                        title = page.title,
                        state = state,
                        onBack = ::navigateBack,
                        onAction = onAction,
                    ) {
                        pageContent(page.route, page.parentDestination, ::navigateTo, ::navigateRoute)
                    }
                }
            }
        }
    }

    BackHandler(enabled = !state.navigationPredictiveBackEnabled) {
        navigateBack()
    }
}

@Composable
private fun HyperSettingsDetailPage(
    route: String,
    destination: SettingsDestination,
    title: String,
    state: SettingsDetailUiState,
    onBack: () -> Unit,
    onAction: (SettingsDetailUiAction) -> Unit,
    content: @Composable () -> Unit,
) {
    var clearCourses by rememberSaveable(route) { mutableStateOf(false) }
    var clearArchives by rememberSaveable(route) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                SmallTopAppBar(
                    title = title,
                    color = MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Normal.Back, "返回")
                        }
                    },
                    actions = {
                        if (destination == SettingsDestination.CourseManage && state.courseCount > 0) {
                            IconButton(onClick = { clearCourses = true }) {
                                Icon(MiuixIcons.Normal.Delete, "清空课程")
                            }
                        }
                        if (destination == SettingsDestination.Archives && state.archiveCount > 0) {
                            IconButton(onClick = { clearArchives = true }) {
                                Icon(MiuixIcons.Normal.Delete, "清空归档")
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                content()
            }
        }

        HyperConfirmationDialog(
            show = clearCourses,
            title = "确认清空",
            content = "此操作将删除当前 ${state.courseCount} 门课程。\n删除后将无法恢复。",
            onConfirm = {
                clearCourses = false
                onAction(SettingsDetailUiAction.ClearCourses)
            },
            onDismiss = { clearCourses = false },
        )

        HyperConfirmationDialog(
            show = clearArchives,
            title = "确认清空",
            content = "此操作将永久删除 ${state.archiveCount} 条归档日程。\n删除后将无法恢复。",
            onConfirm = {
                clearArchives = false
                onAction(SettingsDetailUiAction.ClearArchives)
            },
            onDismiss = { clearArchives = false },
        )
    }
}

@Composable
private fun HyperConfirmationDialog(
    show: Boolean,
    title: String,
    content: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = content,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                    contentColor = MiuixTheme.colorScheme.onError,
                ),
            ) {
                Text("删除")
            }
        }
    }
}
