package com.antgskds.calendarassistant.feature.settings.about.ui.render.material

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiAction
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiState
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics

@Composable
fun MaterialAboutScreen(state: AboutUiState, onAction: (AboutUiAction) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics(state.hapticFeedbackEnabled)
    var titleTapCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Will do",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                haptics.click()
                if (state.developerOptionsUnlocked) {
                    Toast.makeText(context, "开发者选项已解锁", Toast.LENGTH_SHORT).show()
                } else {
                    titleTapCount++
                    val remaining = DEVELOPER_UNLOCK_TAP_COUNT - titleTapCount
                    when {
                        remaining <= 0 -> {
                            titleTapCount = 0
                            onAction(AboutUiAction.UnlockDeveloperOptions)
                            Toast.makeText(context, "开发者选项已解锁，请前往实验室查看", Toast.LENGTH_SHORT).show()
                        }
                        titleTapCount >= 2 -> Toast.makeText(context, "再点击 $remaining 次解锁开发者选项", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        Text("Version ${state.versionName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("作者: AIXINJUELUO_AI", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(48.dp))
        Text(
            "特别致谢 / Special Thanks",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        ContributorLine("加大号的猫", "关于原生安卓和三星的实况通知代码")
        ContributorLine("阿巴阿巴6789", "关于 Flyme 的实况通知代码")
        ContributorLine("zz1812", "关于小米的超级岛代码")
        ContributorLine("shareven", "短信取件码解析正则表达式")
        if (state.hasDonated) {
            Spacer(Modifier.height(12.dp))
            Text("感谢您的捐赠", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(56.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
        ) {
            AboutIcon(R.drawable.ic_github, "GitHub") { haptics.click(); onAction(AboutUiAction.OpenGithub) }
            AboutIcon(R.drawable.ic_file, "个人博客") { haptics.click(); onAction(AboutUiAction.OpenBlog) }
            AboutIcon(R.drawable.ic_coffee, "捐赠") { haptics.click(); onAction(AboutUiAction.OpenDonate) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ContributorLine(name: String, contribution: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append(name) }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" 提供的\n") }
            withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize)) { append(contribution) }
        },
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun AboutIcon(iconRes: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(painterResource(iconRes), description, modifier = Modifier.size(32.dp))
    }
}

private const val DEVELOPER_UNLOCK_TAP_COUNT = 5
