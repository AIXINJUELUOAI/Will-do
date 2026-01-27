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
    // 1. 定义 BUG 数据列表
    val knownBugs = listOf(
        "深色模式浅色模式切换时TopBar不同步",
        "FLYME实况通知不生效",
        "部分页面小白条未适配",
        "以及一些暂未发现的BUG"
    )

    // 使用 verticalScroll 让页面可以滚动，防止内容溢出
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // 关键：添加滚动状态
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. 头部信息 ---
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Will do",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Version 1.1.1 Beta",
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
        BugListSection(bugList = knownBugs)

        // 底部留白
        Spacer(modifier = Modifier.height(24.dp))
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

@Composable
fun BugListSection(bugList: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally // 外层标题居中
    ) {
        Text(
            text = "已知问题 / Known Issues",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                // 让 Card 内部的所有行都水平居中
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bugList.isEmpty()) {
                    Text(
                        text = "暂无已知 BUG，这也太棒了！🎉",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    bugList.forEachIndexed { index, bug ->
                        Row(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth(),
                            // 让“序号”和“文字”这一组内容在行内居中
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Top
                        ) {
                            // 序号
                            /*Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp),
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.width(4.dp))*/
                            // 内容
                            Text(
                                text = bug,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        // 分割线 (最后一行不显示)
                        if (index < bugList.size - 1) {
                            Divider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}