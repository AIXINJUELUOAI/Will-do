package com.antgskds.calendarassistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.page_display.settings.AppBackgroundGlassSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// 定义设置导航的目标
enum class SettingsDestination {
    // 课表相关（新细分）
    CourseManage,      // 课表管理
    TimeTableManage,   // 作息表管理
    SemesterConfig,    // 学期配置

    // 课表相关（旧版，保持兼容）
    Schedule,          // 综合课表设置（已废弃，建议使用上述细分选项）

    // 其他设置
    AI,                // 模型配置
    Weather,           // 天气设置
    Preference,        // 偏好设置
    ScheduleColors,    // 日程颜色（从偏好设置入口进入）
    Archives,          // 日程归档
    Backup,            // 数据备份
    AppUpdate,         // 软件更新

    // 操作类（不导航，直接执行）
    Theme,             // 主题设置
    Logout,            // 退出登录
    About,             // 关于软件
    Donate,            // 捐赠开发者

    // 实验室
    Laboratory,        // 实验室功能
    Developer,         // 开发者页（从实验室入口进入）
    ConfigEditor,      // 配置编辑页（从开发者页入口进入）
    RegexRuleEditor,   // 正则规则编辑页（从开发者页入口进入）
    BottomBarEditor,   // 底栏编辑（从偏好设置入口进入）
    WidgetSettings     // 桌面小组件（从偏好设置入口进入）
}

@Composable
fun SettingsSidebar(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false,
    glassMode: Boolean = false,
    hasAppUpdate: Boolean = false,
    onThemeToggle: (Boolean) -> Unit = {},
    onNavigate: (SettingsDestination) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var rubberBandOffset by remember { mutableFloatStateOf(0f) }
    var reboundJob by remember { mutableStateOf<Job?>(null) }
    val maxRubberBandOffset = with(density) { 56.dp.toPx() }

    fun stopRebound() {
        reboundJob?.cancel()
        reboundJob = null
    }

    fun applyRubberBand(delta: Float, dampen: Boolean = true): Float {
        if (delta == 0f) return 0f
        stopRebound()
        val previous = rubberBandOffset
        val isReturning = (previous > 0f && delta < 0f) || (previous < 0f && delta > 0f)
        val distanceRatio = (abs(previous) / maxRubberBandOffset).coerceIn(0f, 0.85f)
        val resistance = when {
            isReturning -> 1f
            dampen -> 0.42f * (1f - distanceRatio)
            else -> 1f
        }
        val next = (previous + delta * resistance).coerceIn(-maxRubberBandOffset, maxRubberBandOffset)
        rubberBandOffset = next
        return next - previous
    }

    fun springBack() {
        val startOffset = rubberBandOffset
        if (startOffset == 0f) return
        reboundJob?.cancel()
        reboundJob = scope.launch {
            val animatable = Animatable(startOffset)
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) {
                rubberBandOffset = value
            }
            rubberBandOffset = 0f
        }
    }

    val rubberBandConnection = remember(maxRubberBandOffset) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || rubberBandOffset == 0f) return Offset.Zero
                val delta = available.y
                val isReturning = (rubberBandOffset > 0f && delta < 0f) ||
                    (rubberBandOffset < 0f && delta > 0f)
                if (!isReturning) return Offset.Zero
                val consumedY = applyRubberBand(delta, dampen = false)
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                val consumedY = applyRubberBand(available.y)
                return Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                springBack()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                springBack()
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(if (glassMode) Color.Transparent else MaterialTheme.colorScheme.background)
            .nestedScroll(rubberBandConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .offset { IntOffset(0, rubberBandOffset.roundToInt()) }
                .padding(horizontal = 16.dp)
                .statusBarsPadding()
                .padding(top = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 第一块：顶部操作卡片（退出、主题切换、关于）
            SidebarTopActionsCard(
                isDarkMode = isDarkMode,
                glassMode = glassMode,
                hasAppUpdate = hasAppUpdate,
                onThemeNavigate = { onNavigate(SettingsDestination.Theme) },
                onAbout = { onNavigate(SettingsDestination.About) },
                onAppUpdate = { onNavigate(SettingsDestination.AppUpdate) }
            )

            // 第二块：课表管理卡片
            SidebarScheduleCard(glassMode, onNavigate)

            // 第三块：其他设置卡片
            SidebarOtherSettingsCard(glassMode, onNavigate)

            // 第四块：实验室卡片
            SidebarLaboratoryCard(glassMode, onNavigate)

            // 第五块：数据管理卡片（日程归档、数据备份）
            SidebarDataManagementCard(glassMode, onNavigate)

            // 为浮动底栏预留空间，避免底部板块被遮挡
            Spacer(modifier = Modifier.height(IntegratedFloatingBarVisualHeight + 16.dp))
        }
    }
}

// 第一块：顶部操作卡片
@Composable
private fun SidebarTopActionsCard(
    isDarkMode: Boolean,
    glassMode: Boolean,
    hasAppUpdate: Boolean,
    onThemeNavigate: () -> Unit,
    onAbout: () -> Unit,
    onAppUpdate: () -> Unit
) {
    SidebarGlassCard(glassMode = glassMode) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 主题设置
            SidebarActionItem(
                icon = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                title = "主题设置",
                subtitle = "深色模式与主题颜色",
                onClick = onThemeNavigate
            )
            // 关于软件
            SidebarActionItem(
                icon = Icons.Default.Info,
                title = "关于软件",
                subtitle = "版本信息与帮助",
                onClick = onAbout
            )
            // 软件更新
            SidebarActionItem(
                icon = Icons.Default.SystemUpdate,
                title = "软件更新",
                subtitle = "版本日志与下载",
                onClick = onAppUpdate,
                showBadge = hasAppUpdate
            )
        }
    }
}

@Composable
private fun SidebarGlassCard(
    glassMode: Boolean,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    if (glassMode) {
        AppBackgroundGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape
        ) {
            content()
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

// 通用操作项组件
@Composable
private fun SidebarActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    showBadge: Boolean = false
) {
    val haptics = rememberAppHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null) {
                haptics.click()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (showBadge) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                )
            }
        }
        if (showChevron) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// 第二块：课表管理卡片
@Composable
private fun SidebarScheduleCard(glassMode: Boolean, onNavigate: (SettingsDestination) -> Unit) {
    SidebarGlassCard(glassMode = glassMode) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 课表管理
            SidebarActionItem(
                icon = Icons.Default.TableChart,
                title = "课表管理",
                subtitle = "管理课程信息",
                onClick = { onNavigate(SettingsDestination.CourseManage) }
            )
            // 作息表管理
            SidebarActionItem(
                icon = Icons.Default.Schedule,
                title = "作息表管理",
                subtitle = "设置上课时间",
                onClick = { onNavigate(SettingsDestination.TimeTableManage) }
            )
            // 学期配置
            SidebarActionItem(
                icon = Icons.Default.DateRange,
                title = "学期配置",
                subtitle = "设置学期时间",
                onClick = { onNavigate(SettingsDestination.SemesterConfig) }
            )
        }
    }
}

// 第三块：其他设置卡片
@Composable
private fun SidebarOtherSettingsCard(glassMode: Boolean, onNavigate: (SettingsDestination) -> Unit) {
    SidebarGlassCard(glassMode = glassMode) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 模型配置
            SidebarActionItem(
                icon = Icons.Default.Android,
                title = "模型配置",
                subtitle = "API Key 与模型",
                onClick = { onNavigate(SettingsDestination.AI) }
            )
            SidebarActionItem(
                icon = Icons.Default.WbSunny,
                title = "天气",
                subtitle = "天气 API 与展示设置",
                onClick = { onNavigate(SettingsDestination.Weather) }
            )
            // 偏好设置
            SidebarActionItem(
                icon = Icons.Default.Tune,
                title = "偏好设置",
                subtitle = "通知、显示选项",
                onClick = { onNavigate(SettingsDestination.Preference) }
            )
        }
    }
}

// 第四块：实验室卡片
@Composable
private fun SidebarLaboratoryCard(glassMode: Boolean, onNavigate: (SettingsDestination) -> Unit) {
    SidebarGlassCard(glassMode = glassMode) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 实验室
            SidebarActionItem(
                icon = Icons.Default.Science,
                title = "实验室",
                subtitle = "实验性功能",
                onClick = { onNavigate(SettingsDestination.Laboratory) }
            )
        }
    }
}

// 第五块：数据管理卡片
@Composable
private fun SidebarDataManagementCard(glassMode: Boolean, onNavigate: (SettingsDestination) -> Unit) {
    SidebarGlassCard(glassMode = glassMode) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 日程归档
            SidebarActionItem(
                icon = Icons.Default.Archive,
                title = "日程归档",
                subtitle = "查看历史日程",
                onClick = { onNavigate(SettingsDestination.Archives) }
            )
            // 数据备份
            SidebarActionItem(
                icon = Icons.Default.Save,
                title = "数据备份",
                subtitle = "导入导出",
                onClick = { onNavigate(SettingsDestination.Backup) }
            )
        }
    }
}
