package com.antgskds.calendarassistant.ui.page_display.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

@Composable
fun AboutPage(
    uiSize: Int = 2,
    onNavigateToDonate: () -> Unit = {},
    settingsViewModel: SettingsViewModel? = null
) {

    val context = LocalContext.current
    var appTitleTapCount by remember { mutableIntStateOf(0) }

    // 获取捐赠状态，如果 settingsViewModel 为 null 则默认为 false
    val settings = settingsViewModel?.settings?.collectAsState()?.value
    val haptics = rememberAppHaptics(settings?.hapticFeedbackEnabled ?: true)
    val hasDonated = settings?.hasDonated ?: false
    val developerUnlocked = settings?.developerOptionsUnlocked == true

    // --- 链接配置 ---
    // 您的 GitHub 仓库
    val githubUrl = "https://github.com/AIXINJUELUOAI/Will-do"
    val blogUrl = "https://aixinjueluoonline.top/"

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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable {
                haptics.click()
                if (developerUnlocked) {
                    Toast.makeText(context, "开发者选项已解锁", Toast.LENGTH_SHORT).show()
                    return@clickable
                }
                appTitleTapCount += 1
                val remaining = DEVELOPER_UNLOCK_TAP_COUNT - appTitleTapCount
                if (remaining <= 0) {
                    appTitleTapCount = 0
                    settingsViewModel?.unlockDeveloperOptions()
                    Toast.makeText(context, "开发者选项已解锁，请前往实验室查看", Toast.LENGTH_SHORT).show()
                } else if (appTitleTapCount >= 2) {
                    Toast.makeText(context, "再点击 $remaining 次解锁开发者选项", Toast.LENGTH_SHORT).show()
                }
            }
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
        Spacer(modifier = Modifier.height(8.dp))
        ContributorLine(
            name = "shareven",
            contribution = "短信取件码解析正则表达式"
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
                    haptics.click()
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

            // 2. 个人博客按钮
            IconButton(
                onClick = {
                    haptics.click()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(blogUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_file),
                    contentDescription = "个人博客",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            // 3. 捐赠按钮
            IconButton(
                onClick = { haptics.click(); onNavigateToDonate() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_coffee),
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

        // 导航栏避让
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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

private const val DEVELOPER_UNLOCK_TAP_COUNT = 5
