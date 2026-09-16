package com.antgskds.calendarassistant

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import com.antgskds.calendarassistant.shared.ui.material.component.AppDropdownMenu
import com.antgskds.calendarassistant.shared.ui.material.component.AppGlassSettings
import com.antgskds.calendarassistant.shared.ui.material.component.AppGlassSettingsProvider
import com.antgskds.calendarassistant.shared.ui.material.component.AppMenuItem
import com.antgskds.calendarassistant.shared.ui.material.component.appWindowBackdrop
import com.antgskds.calendarassistant.shared.ui.material.component.rememberAppWindowBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** 重现首页的父场景 + 局部场景采样关系，菜单必须在独立窗口中绘制。 */
class AppDropdownMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassMenuSupportsLongPressSelectionAndBack() = exerciseMenu(glass = true)
    @Test fun plainMenuSupportsLongPressSelectionAndBack() = exerciseMenu(glass = false)

    @OptIn(ExperimentalFoundationApi::class)
    private fun exerciseMenu(glass: Boolean) {
        var clicks = 0
        var selections = 0
        var menuVisible = false
        compose.setContent {
            MaterialTheme {
                val root = rememberAppWindowBackdrop()
                val scene = rememberAppWindowBackdrop(parent = root)
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().appWindowBackdrop(root).background(Color.LightGray)) {
                    Box(Modifier.fillMaxSize().appWindowBackdrop(scene)) {
                        AppGlassSettingsProvider(AppGlassSettings(
                            enabled = glass, backdrop = root, overlayBackdrop = scene,
                        )) {
                            Box(Modifier.padding(32.dp)) {
                                Text("切换视图", Modifier.combinedClickable(
                                    onClick = { clicks++ },
                                    onLongClick = { expanded = true; menuVisible = true },
                                ))
                                AppDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false; menuVisible = false },
                                    items = listOf(
                                        AppMenuItem("今日视图", onClick = {}, selected = true),
                                        AppMenuItem("周视图", onClick = { selections++ }),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("切换视图").performClick()
        compose.runOnIdle { assertEquals(1, clicks); assertFalse(menuVisible) }

        compose.onNodeWithText("切换视图").performTouchInput { longClick() }
        compose.onNodeWithText("周视图").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(1, selections)
            assertFalse(menuVisible)
        }
        compose.waitForIdle()
        compose.onNodeWithText("切换视图").performTouchInput { longClick() }
        compose.onNodeWithText("今日视图").assertIsDisplayed()
        pressBack()
        compose.runOnIdle { assertFalse(menuVisible); assertEquals(1, selections) }
    }
}
