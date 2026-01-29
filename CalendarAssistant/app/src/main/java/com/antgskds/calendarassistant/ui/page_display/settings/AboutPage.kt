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
fun AboutPage() {

    // 使用 verticalScroll 让页面可以滚动，防止内容溢出
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // 关键：添加滚动状态
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // 【关键修改】：设置垂直排列方式为居中
        verticalArrangement = Arrangement.Center
    ) {
        // --- 1. 头部信息 ---
        // 既然居中了，顶部的 Spacer 可以稍微减小或者保留，看你想要视觉中心偏上还是绝对居中
        // 这里保留原样，作为内容块内部的留白
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Will do",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Version 1.1.2",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "作者: AIXINJUELUO_AI",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(48.dp))

        // --- 2. 致谢部分 (优化版：名字高亮) ---
        Text(
            text = "特别致谢 / Special Thanks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 使用下方定义的辅助组件来显示高亮名字
        ContributorLine(
            name = "加大号的猫",
            contribution = "关于原生安卓和三星的实况通知代码"
        )
        Spacer(modifier = Modifier.height(8.dp)) // 行间距
        ContributorLine(
            name = "阿巴阿巴6789",
            contribution = "关于Flyme的实况通知代码"
        )

        Spacer(modifier = Modifier.height(48.dp))

        // --- 3. BUG 列表部分 ---
        //BugListSection(bugList = knownBugs)

        // 底部留白
        Spacer(modifier = Modifier.height(24.dp))

        // --- 修改：增加导航栏适配，防止小白条遮挡最后的内容 ---
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * 辅助组件：用于显示 "名字(高亮) + 贡献内容"
 */
@Composable
fun ContributorLine(name: String, contribution: String) {
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
            // 3. 换行 (如果内容太长想换行可以加 \n，不换行则去掉)
            append("\n")

            // 4. 贡献内容样式
            withStyle(
                style = SpanStyle(fontSize = 13.sp) // 稍微改小一点点字体区分层次
            ) {
                append(contribution)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center, // 整体居中
        lineHeight = 20.sp // 增加行高，防止换行时挤在一起
    )
}