package com.antgskds.calendarassistant.feature.settings.about.ui.render

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiAction
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiState
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(state: AboutUiState, onAction: (AboutUiAction) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics(state.hapticFeedbackEnabled)
    var titleTapCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Will do",
            modifier = Modifier
                .clickable {
                    haptics.click()
                    if (state.developerOptionsUnlocked) {
                        Toast.makeText(context, "开发者选项已解锁", Toast.LENGTH_SHORT).show()
                    } else {
                        titleTapCount++
                        val remaining = 5 - titleTapCount
                        if (remaining <= 0) {
                            titleTapCount = 0
                            onAction(AboutUiAction.UnlockDeveloperOptions)
                            Toast.makeText(context, "开发者选项已解锁，请前往实验室查看", Toast.LENGTH_SHORT).show()
                        } else if (titleTapCount >= 2) {
                            Toast.makeText(context, "再点击 $remaining 次解锁开发者选项", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .padding(8.dp),
            fontSize = MiuixTheme.textStyles.title1.fontSize,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Version ${state.versionName}",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text("作者：AIXINJUELUO_AI", fontWeight = FontWeight.Medium)

        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "特别致谢 / Special Thanks",
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                )
                ContributorLine("加大号的猫", "关于原生安卓和三星的实况通知代码")
                ContributorLine("阿巴阿巴6789", "关于 Flyme 的实况通知代码")
                ContributorLine("zz1812", "关于小米的超级岛代码")
                ContributorLine("shareven", "短信取件码解析正则表达式")
                if (state.hasDonated) {
                    Text("感谢您的捐赠", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AboutIcon(R.drawable.ic_github, "GitHub") { onAction(AboutUiAction.OpenGithub) }
            AboutIcon(R.drawable.ic_file, "个人博客") { onAction(AboutUiAction.OpenBlog) }
            AboutIcon(R.drawable.ic_coffee, "捐赠") { onAction(AboutUiAction.OpenDonate) }
        }
        Text(
            text = "本软件已完整开源并遵守 GPLv3 协议",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        )
        Text(
            text = state.daemonStatus,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
        )
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun ContributorLine(name: String, contribution: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(
            contribution,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
        )
    }
}

@Composable
private fun AboutIcon(iconRes: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(painterResource(iconRes), description, modifier = Modifier.size(28.dp))
    }
}
