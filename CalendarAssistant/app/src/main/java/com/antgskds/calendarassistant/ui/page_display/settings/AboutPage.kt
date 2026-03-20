package com.antgskds.calendarassistant.ui.page_display.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.BuildConfig
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

@Composable
fun AboutPage(
    uiSize: Int = 2,
    onNavigateToDonate: () -> Unit = {},
    settingsViewModel: SettingsViewModel? = null
) {

    val context = LocalContext.current

    // 获取捐赠状态，如果 settingsViewModel 为 null 则默认为 false
    val hasDonated = settingsViewModel?.settings?.collectAsState()?.value?.hasDonated ?: false

    // --- 链接配置 ---
    // 您的 GitHub 仓库
    val githubUrl = "https://github.com/AIXINJUELUOAI/Will-do"
    // 稳定的 GPLv3 协议链接 (Open Source Initiative)
    val gplUrl = "https://opensource.org/licenses/GPL-3.0"

    // --- 样式定义 ---
    val cardTitleStyle = MaterialTheme.typography.headlineMedium
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    val metaInfoStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ================= 上半部分 =================
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Will do",
            style = cardTitleStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = metaInfoStyle
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "作者: AIXINJUELUO_AI",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(48.dp))

        // ================= 致谢部分 =================
        Text(
            text = "特别致谢 / Special Thanks",
            style = sectionTitleStyle
        )
        Spacer(modifier = Modifier.height(16.dp))

        ContributorLine(
            name = "加大号的猫",
            contribution = "关于原生安卓和三星的实况通知代码"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ContributorLine(
            name = "阿巴阿巴6789",
            contribution = "关于Flyme的实况通知代码"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ContributorLine(
            name = "zz1812",
            contribution = "关于小米的超级岛代码"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 仅在用户已捐赠时显示
        if (hasDonated) {
            // “感谢您的捐赠” 字体样式已和”特别致谢”统一
            Text(
                text = "感谢您的捐赠",
                style = sectionTitleStyle,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        // ================= 底部图标按钮 =================
        // 使用 Arrangement.spacedBy 来确保三个按钮中间的间距绝对一致 (这里设置为 32.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. GitHub 按钮
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub Repository",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            // 2. GPL 协议按钮
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gplUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_gpl),
                    contentDescription = "GPLv3 License",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            // 3. 捐赠按钮 (已替换为汽水瓶图片资源)
            IconButton(
                onClick = onNavigateToDonate,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    // 请确保在 res/drawable/ 目录下放入了名为 ic_cola 的汽水瓶图标
                    painter = painterResource(id = R.drawable.ic_cola),
                    contentDescription = "捐赠开发者",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 简短声明
        Text(
            text = "本软件已完整开源并遵循 GPLv3 协议",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        val daemonStatus = when (PrivilegeManager.privilegeType) {
            PrivilegeManager.PrivilegeType.SHIZUKU -> "Daemon: Shizuku Active"
            PrivilegeManager.PrivilegeType.ROOT -> "Daemon: Root Active"
            PrivilegeManager.PrivilegeType.NONE -> "Daemon: None"
        }
        Text(
            text = daemonStatus,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 辅助组件：致谢行
 */
@Composable
fun ContributorLine(name: String, contribution: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                style = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                append(name)
            }
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(" 提供的\n")
            }
            withStyle(
                style = SpanStyle(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                append(contribution)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
    )
}