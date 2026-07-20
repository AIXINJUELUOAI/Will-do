package com.antgskds.calendarassistant.feature.settings.onboarding.ui.connector

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleDisplayModel
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseMeta
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.schedule.presentation.rule.EventPresenter
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.floor

@Composable
fun OnboardingLiveCapsuleDemoPage(
    uiSize: Int = 2,
) {
    val stageHeight = when (uiSize) {
        1 -> 260.dp
        3 -> 380.dp
        else -> 320.dp
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "实况胶囊演示",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary
        )

        AppSettingsCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "日程与提醒",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "先单独调整顶部胶囊态：左侧使用真实事件图标，右侧展示解析后的实况通知信息，并循环切换演示事件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LiveCapsuleDemoStage(height = stageHeight)

        AppSettingsCard {
            Text(
                text = "后续其它初始化动画先不加入；等这个固定胶囊的尺寸、间距和信息密度确认后，再复用到识别完成、天气预警等场景。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun LiveCapsuleDemoStage(
    height: Dp,
) {
    val colors = MaterialTheme.colorScheme
    val capsuleHeight = 34.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainerLowest)
        ) {
            MockPhoneTopArea()

            LiveCapsuleLoop(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                capsuleHeight = capsuleHeight
            )
        }
    }
}

@Composable
private fun MockPhoneTopArea() {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
            size = Size(size.width * 0.90f, size.height * 1.5f),
            cornerRadius = CornerRadius(size.width * 0.1f, size.width * 0.1f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun LiveCapsuleLoop(
    modifier: Modifier = Modifier,
    capsuleHeight: Dp,
) {
    val context = LocalContext.current
    val items = remember(context) {
        demoEvents().map { event ->
            val renderModel = EventPresenter.present(context, event)
            val displayModel = EventPresenter.presentCapsule(context, event, isExpired = false)
            LiveCapsuleItem(
                iconRes = renderModel.iconResId ?: R.drawable.ic_notification_small,
                displayModel = displayModel
            )
        }
    }

    val progress = rememberLoopingProgress(durationMillis = 8000, label = "liveCapsule")
    val segment = progress * items.size
    val itemIndex = floor(segment).toInt().coerceIn(0, items.lastIndex)
    val local = segment - itemIndex

    val expansionProgress = when {
        local < 0.15f -> smoothStep(local / 0.15f)
        local < 0.75f -> 1f
        local < 0.90f -> 1f - smoothStep((local - 0.75f) / 0.15f)
        else -> 0f
    }

    val contentAlpha = when {
        expansionProgress > 0.3f -> smoothStep((expansionProgress - 0.3f) / 0.7f)
        else -> 0f
    }

    val item = items[itemIndex]

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Surface(
            shape = CircleShape,
            color = Color.Black,
            shadowElevation = 4.dp
        ) {
            DynamicSymmetricalCapsuleLayout(
                expansionProgress = expansionProgress,
                capsuleHeight = capsuleHeight,
                cameraGap = 22.dp
            ) {
                // index 0: Left Icon
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp) // 稍大一点点，让它在半圆里更饱满
                        .graphicsLayer { alpha = contentAlpha }
                )
                // index 1: Right Text
                Text(
                    text = item.infoText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                )
            }
        }
    }
}

/**
 * 终极版：严格遵循小米规范的“圆心对齐”物理几何测量
 */
@Composable
private fun DynamicSymmetricalCapsuleLayout(
    expansionProgress: Float,
    capsuleHeight: Dp,
    cameraGap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val heightPx = with(density) { capsuleHeight.roundToPx() }
    val cameraGapPx = with(density) { cameraGap.roundToPx() }
    val innerGapPx = with(density) { 6.dp.roundToPx() } // Icon/文字与摄像头的安全距离

    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val iconPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val textPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))

        // 1. 严格的几何锚定：左侧半圆的半径 R
        val R = heightPx / 2f

        // 2. Icon 必须放置在 (R, R) 为圆心的位置
        // 所以 Icon 的左上角 X 坐标必定是 R - (iconWidth / 2)
        val iconX = (R - (iconPlaceable.width / 2f)).toInt()
        val iconY = (R - (iconPlaceable.height / 2f)).toInt()

        // 3. 计算左侧为了包裹住 Icon 所需要的最小半宽 (距离摄像头中心)
        // Icon的右边缘坐标是 iconX + iconWidth。再加一个内边距
        val leftRequired = iconX + iconPlaceable.width + innerGapPx

        // 4. 计算右侧为了包裹住文字所需要的最小半宽
        // 为了视觉平衡，文字的右侧边距也固定使用 R
        val rightRequired = innerGapPx + textPlaceable.width + R.toInt()

        // 5. 提取最大值，确保绝对的对称性
        val targetHalfWidth = maxOf(leftRequired, rightRequired)
        val targetTotalWidth = targetHalfWidth * 2 + cameraGapPx

        // 6. 根据动画插值计算当前帧的动态宽度
        val currentWidth = heightPx + ((targetTotalWidth - heightPx) * expansionProgress).toInt()

        layout(currentWidth, heightPx) {
            if (expansionProgress > 0.05f) {
                // Icon：无论宽度如何伸缩，Icon 永远被死死钉在左半圆的圆心
                iconPlaceable.placeRelative(iconX, iconY)

                // Text：放置在右侧半圆内，保持与右侧边缘距离 R
                val textY = (heightPx - textPlaceable.height) / 2
                val textX = currentWidth - R.toInt() - textPlaceable.width
                textPlaceable.placeRelative(textX, textY)
            }
        }
    }
}

@Composable
private fun rememberLoopingProgress(
    durationMillis: Int,
    label: String,
): Float {
    val transition = rememberInfiniteTransition(label = label)
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "${label}Progress"
    )
    return progress
}

private data class LiveCapsuleItem(
    val iconRes: Int,
    val displayModel: CapsuleDisplayModel,
) {
    val infoText: String
        get() {
            val short = displayModel.shortText.cleanCapsuleLine()
            val primary = displayModel.primaryText.cleanCapsuleLine()
            return short ?: primary ?: "提醒"
        }
}

private fun String?.cleanCapsuleLine(): String? {
    val clean = this
        ?.trim()
        ?.replace("\n", " ")
        ?.replace("\r", " ")
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return if (clean.equals("null", ignoreCase = true)) null else clean
}

private fun demoEvents(): List<Event> {
    val zone = ZoneId.systemDefault()
    val date = LocalDate.now().plusDays(1)
    fun epoch(hour: Int, minute: Int): Long = date.atTime(LocalTime.of(hour, minute)).atZone(zone).toEpochSecond()

    return listOf(
        Event(
            id = 1001,
            startTS = epoch(14, 0),
            endTS = epoch(15, 0),
            title = "产品需求评审会",
            location = "一号会议室",
            description = "【日程】产品需求评审会",
            tag = EventTags.GENERAL,
            timeZone = zone.id
        ),
        Event(
            id = 1002,
            startTS = epoch(16, 30),
            endTS = epoch(17, 0),
            title = "菜鸟取件 A1002",
            location = "东门驿站",
            description = "【取件】A1002|菜鸟驿站|东门货架 3",
            tag = EventTags.PICKUP,
            timeZone = zone.id
        ),
        Event(
            id = 1003,
            startTS = epoch(18, 20),
            endTS = epoch(22, 50),
            title = "A8 检票", // 极短文本，可以清晰看出动态宽度的收缩效果
            location = "深圳北站",
            description = "【列车】G1007|A8|6车12A",
            tag = EventTags.TRAIN,
            reminder1Minutes = 30,
            timeZone = zone.id
        ),
        Event(
            id = 1004,
            startTS = epoch(9, 40),
            endTS = epoch(11, 10),
            title = "高等数学",
            location = "教学楼 301",
            description = CourseEventMapper.buildParentDescription(
                CourseMeta(
                    uid = "demo-course-1004",
                    teacher = "王教授",
                    dayOfWeek = date.dayOfWeek.value.coerceIn(1, 7),
                    startNode = 1,
                    endNode = 2,
                    startWeek = 1,
                    endWeek = 4,
                    weekType = 0
                )
            ),
            rrule = "FREQ=WEEKLY;INTERVAL=1;COUNT=4",
            tag = EventTags.COURSE,
            timeZone = zone.id
        )
    )
}

private fun smoothStep(value: Float): Float {
    val x = value.coerce01()
    return x * x * (3f - 2f * x)
}

private fun Float.coerce01(): Float = coerceIn(0f, 1f)
