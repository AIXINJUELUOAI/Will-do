package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutPage(uiSize: Int = 2) {

    // --- 字体样式优化 ---
    val cardTitleStyle = MaterialTheme.typography.headlineMedium
    val contentBodyStyle = MaterialTheme.typography.bodyMedium

    // 作者/版本号等次要信息：灰色 + Transparent
    val metaInfoStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    // 板块标题：加粗 + 主色
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    // 使用 verticalScroll 让页面可以滚动，防止内容溢出
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- 1. 头部信息 ---
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Will do",
            style = cardTitleStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Version 1.1.3 Beta",
            style = metaInfoStyle
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "作者: AIXINJUELUO_AI",
            style = MaterialTheme.typography.labelLarge, // 保持原样或微调
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(48.dp))

        // --- 2. 致谢部分 ---
        Text(
            text = "特别致谢 / Special Thanks",
            style = sectionTitleStyle
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 使用下方定义的辅助组件来显示高亮名字
        ContributorLine(
            name = "加大号的猫",
            contribution = "关于原生安卓和三星的实况通知代码",
            textStyle = contentBodyStyle
        )
        Spacer(modifier = Modifier.height(8.dp))
        ContributorLine(
            name = "阿巴阿巴6789",
            contribution = "关于Flyme的实况通知代码",
            textStyle = contentBodyStyle
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 底部留白
        Spacer(modifier = Modifier.height(24.dp))

        // 导航栏适配
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * 辅助组件：用于显示 "名字(高亮) + 贡献内容"
 */
@Composable
fun ContributorLine(name: String, contribution: String, textStyle: androidx.compose.ui.text.TextStyle) {
    Text(
        text = buildAnnotatedString {
            // 1. 名字样式：加粗 + 主题色
            withStyle(
                style = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                append(name)
            }
            // 2. 连接词样式
            withStyle(
                style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                append(" 提供的")
            }
            // 3. 换行
            append("\n")

            // 4. 贡献内容样式：灰色，稍微小一点
            withStyle(
                style = SpanStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                append(contribution)
            }
        },
        style = textStyle,
        textAlign = TextAlign.Center, // 整体居中
        lineHeight = 20.sp
    )
}