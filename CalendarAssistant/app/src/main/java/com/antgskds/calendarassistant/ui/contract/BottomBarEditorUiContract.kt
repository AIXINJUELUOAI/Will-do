package com.antgskds.calendarassistant.ui.contract

data class BottomBarEditorUiState(
    val activeItems: List<String>,
    val standbyItems: List<String>,
    val startPage: String,
    val quickMemoEnabled: Boolean,
    val hapticEnabled: Boolean
)

sealed interface BottomBarEditorUiAction {
    data class SaveConfig(val items: List<String>, val startPage: String? = null) : BottomBarEditorUiAction
}
