package com.antgskds.calendarassistant.feature.settings.shell.ui.connector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.shared.management.catalog.PageCatalog
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.appearance.ui.connector.ThemeSettingsPage
import com.antgskds.calendarassistant.feature.backup.ui.connector.BackupSettingsPage
import com.antgskds.calendarassistant.feature.home.ui.connector.BottomBarEditorPage
import com.antgskds.calendarassistant.feature.recognition.ui.connector.AiSettingsPage
import com.antgskds.calendarassistant.feature.recognition.ui.connector.RegexRuleEditorPage
import com.antgskds.calendarassistant.feature.schedule.ui.connector.ArchivesPage
import com.antgskds.calendarassistant.feature.schedule.ui.connector.CourseManagerScreen
import com.antgskds.calendarassistant.feature.schedule.ui.connector.ScheduleColorSettingsPage
import com.antgskds.calendarassistant.feature.schedule.ui.connector.ScheduleSettingsPage
import com.antgskds.calendarassistant.feature.schedule.ui.connector.TimeTableEditorScreen
import com.antgskds.calendarassistant.feature.settings.about.ui.connector.DonatePage
import com.antgskds.calendarassistant.feature.settings.developer.ui.connector.ConfigEditorPage
import com.antgskds.calendarassistant.feature.settings.developer.ui.connector.DeveloperPage
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.connector.LaboratoryPage
import com.antgskds.calendarassistant.feature.settings.onboarding.ui.connector.OnboardingGuidePage
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceSettingsPage
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiAction
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiState
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.SettingsDetailScreenContent
import com.antgskds.calendarassistant.feature.settings.about.ui.connector.AboutPage
import com.antgskds.calendarassistant.feature.update.ui.connector.AppUpdatePage
import com.antgskds.calendarassistant.feature.weather.ui.connector.WeatherDetailPage
import com.antgskds.calendarassistant.feature.weather.ui.connector.WeatherSettingsPage
import com.antgskds.calendarassistant.platform.widget.ui.connector.WidgetSettingsPage
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel

@Composable
fun SettingsDetailRoute(
    destinationStr: String,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onExitSettings: () -> Unit,
    onLogout: () -> Unit,
    uiSize: Int = 2,
) {
    val settings by settingsViewModel.settings.collectAsState()
    val uiState by mainViewModel.uiState.collectAsState()
    val appUpdateUiState by mainViewModel.appUpdateUiState.collectAsState()
    val archivedEvents by mainViewModel.archivedEvents.collectAsState()
    val initialRoute = remember(destinationStr) {
        val initialDestination = PageCatalog.resolveDestination(destinationStr)
        PageCatalog.routeFor(initialDestination)
            ?: requireNotNull(PageCatalog.routeFor(SettingsDestination.Preference))
    }
    val courseCount = remember(uiState.rawEvents, uiState.settings) {
        CourseEventMapper.extractParentCourses(uiState.rawEvents, uiState.settings).size
    }
    val archiveCount = remember(archivedEvents) {
        archivedEvents
            .filter { it.archivedAt != null }
            .distinctBy { it.id }
            .size
    }

    SettingsDetailScreenContent(
        state = SettingsDetailUiState(
            initialRoute = initialRoute,
            uiSize = uiSize,
            courseModuleEnabled = com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.enabled(settings),
            backgroundEnabled = settings.appBackgroundImagePath.isNotBlank(),
            backgroundMiuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
            backgroundCardAlphaPercent = settings.appBackgroundCardAlphaPercent,
            pageHasAppBackground = uiState.settings.appBackgroundImagePath.isNotBlank(),
            isDarkMode = settings.isDarkMode,
            hasAppUpdate = appUpdateUiState.hasUpdate,
            hapticEnabled = uiState.settings.hapticFeedbackEnabled,
            navigationPredictiveBackEnabled = settings.predictiveBackEnabled,
            confirmationPredictiveBackEnabled = uiState.settings.predictiveBackEnabled,
            courseCount = courseCount,
            archiveCount = archiveCount,
        ),
        onAction = { action ->
            when (action) {
                SettingsDetailUiAction.ExitSettings -> onExitSettings()
                SettingsDetailUiAction.Logout -> onLogout()
                is SettingsDetailUiAction.SetDarkMode -> settingsViewModel.updateDarkMode(action.isDark)
                SettingsDetailUiAction.ClearCourses -> mainViewModel.clearAllCourses()
                SettingsDetailUiAction.ClearArchives -> mainViewModel.clearAllArchives()
            }
        },
        pageContent = { route, destination, onNavigateTo, onNavigateRoute ->
            if (route == PageCatalog.weatherDetailPage.route) {
                WeatherDetailPage(uiSize = uiSize)
            } else {
                SettingsPageRouteContent(
                    destination = destination,
                    mainViewModel = mainViewModel,
                    settingsViewModel = settingsViewModel,
                    uiSize = uiSize,
                    rawEvents = uiState.rawEvents,
                    onNavigateTo = onNavigateTo,
                    onNavigateRoute = onNavigateRoute,
                )
            }
        },
    )
}

@Composable
private fun SettingsPageRouteContent(
    destination: SettingsDestination,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    uiSize: Int,
    rawEvents: List<com.antgskds.calendarassistant.feature.schedule.domain.model.Event>,
    onNavigateTo: (SettingsDestination) -> Unit,
    onNavigateRoute: (String) -> Unit,
) {
    val currentSettings by settingsViewModel.settings.collectAsState()
    if (!com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.enabled(currentSettings) &&
        destination in setOf(SettingsDestination.Schedule, SettingsDestination.SemesterConfig,
            SettingsDestination.CourseManage, SettingsDestination.TimeTableManage)) {
        androidx.compose.material3.Text("课表功能已关闭，可在开发者设置中重新开启")
        return
    }
    when (destination) {
        SettingsDestination.AI -> AiSettingsPage(
            viewModel = settingsViewModel,
            mainViewModel = mainViewModel,
            uiSize = uiSize,
        )

        SettingsDestination.Weather -> WeatherSettingsPage(
            viewModel = settingsViewModel,
            uiSize = uiSize,
            onOpenWeatherDetail = { onNavigateRoute(PageCatalog.weatherDetailPage.route) },
        )

        SettingsDestination.Schedule,
        SettingsDestination.SemesterConfig -> ScheduleSettingsPage(
            viewModel = settingsViewModel,
            onNavigateTo = onNavigateTo,
            uiSize = uiSize,
        )

        SettingsDestination.CourseManage -> CourseManagerScreen(mainViewModel, settingsViewModel, uiSize)
        SettingsDestination.TimeTableManage -> TimeTableEditorScreen(settingsViewModel, uiSize)
        SettingsDestination.Preference -> PreferenceSettingsPage(
            viewModel = settingsViewModel,
            uiSize = uiSize,
            onNavigateToBottomBarEditor = { onNavigateTo(SettingsDestination.BottomBarEditor) },
            onNavigateToWidgetSettings = { onNavigateTo(SettingsDestination.WidgetSettings) },
            onNavigateToScheduleColors = { onNavigateTo(SettingsDestination.ScheduleColors) },
            onNavigateToSemesterConfig = { onNavigateTo(SettingsDestination.SemesterConfig) },
            onNavigateToCourseManage = { onNavigateTo(SettingsDestination.CourseManage) },
            onNavigateToTimeTableManage = { onNavigateTo(SettingsDestination.TimeTableManage) },
        )

        SettingsDestination.ScheduleColors -> ScheduleColorSettingsPage(
            viewModel = settingsViewModel,
            uiSize = uiSize,
        )

        SettingsDestination.Backup -> BackupSettingsPage(settingsViewModel, mainViewModel, uiSize)
        SettingsDestination.AppUpdate -> AppUpdatePage(mainViewModel, uiSize)
        SettingsDestination.About -> AboutPage(
            uiSize = uiSize,
            onNavigateToDonate = { onNavigateTo(SettingsDestination.Donate) },
            settingsViewModel = settingsViewModel,
        )

        SettingsDestination.Donate -> DonatePage(uiSize, settingsViewModel)
        SettingsDestination.Laboratory -> LaboratoryPage(
            uiSize = uiSize,
            settingsViewModel = settingsViewModel,
            mainViewModel = mainViewModel,
            onNavigateToDeveloper = { onNavigateTo(SettingsDestination.Developer) },
        )

        SettingsDestination.Developer -> DeveloperPage(
            settingsViewModel = settingsViewModel,
            uiSize = uiSize,
            onNavigateToConfig = { onNavigateTo(SettingsDestination.ConfigEditor) },
            onNavigateToRegexRules = { onNavigateTo(SettingsDestination.RegexRuleEditor) },
            onNavigateToOnboardingGuide = { onNavigateTo(SettingsDestination.OnboardingGuide) },
        )

        SettingsDestination.ConfigEditor -> ConfigEditorPage(uiSize = uiSize)
        SettingsDestination.OnboardingGuide -> OnboardingGuidePage(
            settingsViewModel = settingsViewModel,
            mainViewModel = mainViewModel,
            uiSize = uiSize,
        )
        SettingsDestination.RegexRuleEditor -> RegexRuleEditorPage(uiSize = uiSize)
        SettingsDestination.BottomBarEditor -> BottomBarEditorPage(
            settingsViewModel = settingsViewModel,
            uiSize = uiSize,
        )

        SettingsDestination.WidgetSettings -> WidgetSettingsPage(
            settingsViewModel = settingsViewModel,
            rawEvents = rawEvents,
            uiSize = uiSize,
        )

        SettingsDestination.Theme -> ThemeSettingsPage(
            viewModel = settingsViewModel,
            mainViewModel = mainViewModel,
            uiSize = uiSize
        )
        SettingsDestination.Archives -> ArchivesPage(viewModel = mainViewModel)
        SettingsDestination.Logout -> Unit
    }
}
