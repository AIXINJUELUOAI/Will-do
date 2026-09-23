package com.antgskds.calendarassistant.feature.settings.about.ui.render.material

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiAction
import com.antgskds.calendarassistant.feature.settings.about.ui.contract.AboutUiState
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics

@Composable
fun MaterialAboutScreen(
    state: AboutUiState,
    onAction: (AboutUiAction) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics(state.hapticFeedbackEnabled)

    var titleTapCount by remember {
        mutableIntStateOf(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        /*
         * 主内容作为完整视觉块居中
         */
        Spacer(
            modifier = Modifier.weight(1f)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // -------------------------
            // 1. 品牌信息
            // -------------------------

            Text(
                text = "Will do",
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable {
                    haptics.click()

                    if (state.developerOptionsUnlocked) {
                        Toast.makeText(
                            context,
                            "开发者选项已解锁",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        titleTapCount++

                        val remaining =
                            DEVELOPER_UNLOCK_TAP_COUNT - titleTapCount

                        when {
                            remaining <= 0 -> {
                                titleTapCount = 0

                                onAction(
                                    AboutUiAction.UnlockDeveloperOptions
                                )

                                Toast.makeText(
                                    context,
                                    "开发者选项已解锁，请前往实验室查看",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            titleTapCount >= 2 -> {
                                Toast.makeText(
                                    context,
                                    "再点击 $remaining 次解锁开发者选项",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = "v${state.versionName}",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // -------------------------
            // 2. Slogan
            // -------------------------

            Spacer(
                modifier = Modifier.height(26.dp)
            )

            Text(
                text = "Remember less. Do more.",
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = "让每件事都有着落",
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // -------------------------
            // 3. 作者信息
            // -------------------------

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            Text(
                text = "Designed by AIXINJUELUO_AI",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.72f
                ),
                textAlign = TextAlign.Center
            )

            if (state.hasDonated) {
                Spacer(
                    modifier = Modifier.height(7.dp)
                )

                Text(
                    text = "感谢您的支持",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }

        /*
         * Footer 独立于主体
         */
        Spacer(
            modifier = Modifier.weight(1f)
        )

        AboutFooter(
            onGithub = {
                haptics.click()
                onAction(AboutUiAction.OpenGithub)
            },
            onBlog = {
                haptics.click()
                onAction(AboutUiAction.OpenBlog)
            },
            onDonate = {
                haptics.click()
                onAction(AboutUiAction.OpenDonate)
            }
        )

        Spacer(
            modifier = Modifier.height(36.dp)
        )
    }
}

@Composable
private fun AboutFooter(
    onGithub: () -> Unit,
    onBlog: () -> Unit,
    onDonate: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        FooterLink(
            text = "GitHub",
            onClick = onGithub
        )

        FooterLink(
            text = "博客",
            onClick = onBlog
        )

        FooterLink(
            text = "支持开发",
            onClick = onDonate
        )
    }
}

@Composable
private fun FooterLink(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = 0.62f
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(
                horizontal = 6.dp,
                vertical = 8.dp
            )
    )
}

private const val DEVELOPER_UNLOCK_TAP_COUNT = 5