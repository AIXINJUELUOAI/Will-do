package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey

fun editionHomeEntries(entries: List<String>): List<String> =
    (entries + HomeEntryKey.SETTINGS).distinct()

data class HyperHomeContentInsets(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

val LocalHyperHomeContentInsets = staticCompositionLocalOf { HyperHomeContentInsets() }
