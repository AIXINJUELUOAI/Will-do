package com.antgskds.calendarassistant.ui.contract

data class AboutUiState(
    val versionName: String,
    val hasDonated: Boolean,
    val developerOptionsUnlocked: Boolean,
    val hapticFeedbackEnabled: Boolean,
    val daemonStatus: String
)

sealed interface AboutUiAction {
    data object OpenGithub : AboutUiAction
    data object OpenBlog : AboutUiAction
    data object OpenDonate : AboutUiAction
    data object UnlockDeveloperOptions : AboutUiAction
}
