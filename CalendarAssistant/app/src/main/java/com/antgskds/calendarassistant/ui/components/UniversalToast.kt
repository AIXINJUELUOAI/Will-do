package com.antgskds.calendarassistant.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用 Toast 类型
 */
enum class ToastType {
    SUCCESS,   // 成功（绿色图标）
    ERROR,     // 错误（红色图标）
    INFO       // 信息（蓝色图标）
}

/**
 * 通用 Compose Toast 组件
 * 用于 SnackbarHost 中显示
 */
@Composable
fun UniversalToast(
    message: String,
    type: ToastType = ToastType.INFO
) {
    val (icon, tint) = when (type) {
        ToastType.SUCCESS -> Icons.Rounded.CheckCircle to Color(0xFF4CAF50)
        ToastType.ERROR -> Icons.Rounded.Error to Color(0xFFEF5350)
        ToastType.INFO -> Icons.Rounded.Info to Color(0xFF42A5F5)
    }

    Surface(
        color = Color(0xFF323232),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .wrapContentWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            androidx.compose.material3.Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )
        }
    }
}

/**
 * 原生 Android Toast 工具函数
 * 用于非 Compose 环境（如 BroadcastReceiver、Service 等）
 * 注意：原生 Toast 无法显示深色 Compose 样式，会使用系统默认样式
 */
object UniversalToastUtil {

    /**
     * 显示原生 Toast（浅色系统样式）
     * 建议在 Compose 环境中使用 SnackbarHost + UniversalToast
     */
    fun show(
        context: android.content.Context,
        message: String,
        duration: Int = Toast.LENGTH_SHORT,
        type: ToastType = ToastType.INFO
    ) {
        Toast.makeText(context, message, duration).show()
    }

    /** 显示成功 Toast */
    fun showSuccess(context: android.content.Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        show(context, message, duration, ToastType.SUCCESS)
    }

    /** 显示错误 Toast */
    fun showError(context: android.content.Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        show(context, message, duration, ToastType.ERROR)
    }

    /** 显示信息 Toast */
    fun showInfo(context: android.content.Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        show(context, message, duration, ToastType.INFO)
    }
}
