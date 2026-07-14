package com.antgskds.calendarassistant.feature.settings.about.ui.connector

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.antgskds.calendarassistant.BuildConfig
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiAction
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiState
import com.antgskds.calendarassistant.feature.settings.about.ui.render.AboutScreen
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

@Composable
fun AboutPage(
    uiSize: Int = 2,
    onNavigateToDonate: () -> Unit = {},
    settingsViewModel: SettingsViewModel? = null
) {
    val context = LocalContext.current
    val settings by settingsViewModel?.settings?.collectAsState()
        ?: return AboutScreen(
            state = defaultAboutUiState(),
            onAction = { action ->
                handleAboutAction(action, context::startActivity, onNavigateToDonate, null)
            }
        )

    val state = AboutUiState(
        versionName = BuildConfig.VERSION_NAME,
        hasDonated = settings.hasDonated,
        developerOptionsUnlocked = settings.developerOptionsUnlocked,
        hapticFeedbackEnabled = settings.hapticFeedbackEnabled,
        daemonStatus = daemonStatus()
    )
    AboutScreen(
        state = state,
        onAction = { action ->
            handleAboutAction(
                action = action,
                openIntent = context::startActivity,
                onNavigateToDonate = onNavigateToDonate,
                onUnlockDeveloperOptions = settingsViewModel::unlockDeveloperOptions
            )
        }
    )
}

private fun defaultAboutUiState(): AboutUiState = AboutUiState(
    versionName = BuildConfig.VERSION_NAME,
    hasDonated = false,
    developerOptionsUnlocked = false,
    hapticFeedbackEnabled = true,
    daemonStatus = daemonStatus()
)

private fun daemonStatus(): String = when (PrivilegeManager.privilegeType) {
    PrivilegeManager.PrivilegeType.SHIZUKU -> "Daemon: Shizuku Active"
    PrivilegeManager.PrivilegeType.ROOT -> "Daemon: Root Active"
    PrivilegeManager.PrivilegeType.NONE -> "Daemon: None"
}

private fun handleAboutAction(
    action: AboutUiAction,
    openIntent: (Intent) -> Unit,
    onNavigateToDonate: () -> Unit,
    onUnlockDeveloperOptions: (() -> Unit)?
) {
    when (action) {
        AboutUiAction.OpenGithub -> openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        AboutUiAction.OpenBlog -> openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(BLOG_URL)))
        AboutUiAction.OpenDonate -> onNavigateToDonate()
        AboutUiAction.UnlockDeveloperOptions -> onUnlockDeveloperOptions?.invoke()
    }
}

private const val GITHUB_URL = "https://github.com/AIXINJUELUOAI/Will-do"
private const val BLOG_URL = "https://aixinjueluoonline.top/"
