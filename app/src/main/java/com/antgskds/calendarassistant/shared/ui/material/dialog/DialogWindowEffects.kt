package com.antgskds.calendarassistant.shared.ui.material.dialog

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
fun DialogEdgeToEdgeEffect(isDarkTheme: Boolean) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val activityWindow = (LocalView.current.context as? Activity)?.window

    DisposableEffect(dialogWindow) {
        val previousStatusBarColor = dialogWindow.statusBarColor
        val previousNavigationBarColor = dialogWindow.navigationBarColor
        val previousBackground = dialogWindow.decorView.background

        onDispose {
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, true)
            dialogWindow.statusBarColor = previousStatusBarColor
            dialogWindow.navigationBarColor = previousNavigationBarColor
            dialogWindow.setBackgroundDrawable(previousBackground)
        }
    }

    SideEffect {
        WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        dialogWindow.statusBarColor = Color.TRANSPARENT
        dialogWindow.navigationBarColor = Color.TRANSPARENT
        dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val dialogController = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
        val activityController = activityWindow?.let { WindowCompat.getInsetsController(it, it.decorView) }
        dialogController.isAppearanceLightStatusBars = activityController?.isAppearanceLightStatusBars ?: !isDarkTheme
        dialogController.isAppearanceLightNavigationBars = activityController?.isAppearanceLightNavigationBars ?: !isDarkTheme
    }
}

@Composable
fun DisableDialogWindowDimEffect() {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return

    DisposableEffect(dialogWindow) {
        val previousDimAmount = dialogWindow.attributes.dimAmount
        val hadDimFlag = dialogWindow.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0

        onDispose {
            if (hadDimFlag) {
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            val attributes = dialogWindow.attributes
            attributes.dimAmount = previousDimAmount
            dialogWindow.attributes = attributes
        }
    }

    SideEffect {
        dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val attributes = dialogWindow.attributes
        attributes.dimAmount = 0f
        dialogWindow.attributes = attributes
    }
}
