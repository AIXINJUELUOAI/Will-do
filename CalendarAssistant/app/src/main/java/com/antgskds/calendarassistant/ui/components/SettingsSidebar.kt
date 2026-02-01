package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Preference,        // 偏好设置
    Archives,          // 日程归档
    Backup,            // 数据备份

    // 操作类（不导航，直接执行）
    ThemeToggle,       // 深色/浅色模式切换
    Logout,            // 退出登录
    About              // 关于软件
}

@Composable
fun SettingsSidebar(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false,
    onThemeToggle: (Boolean) -> Unit = {},
    onNavigate: (SettingsDestination) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 第一块：顶部操作卡片（退出、主题切换、关于）
        SidebarTopActionsCard(
            isDarkMode = isDarkMode,
            onThemeToggle = onThemeToggle,
            onAbout = { onNavigate(SettingsDestination.About) },
            onLogout = { onNavigate(SettingsDestination.Logout) }
        )

        // 第二块：课表管理卡片
        SidebarScheduleCard(onNavigate)

        // 第三块：其他设置卡片
        SidebarOtherSettingsCard(onNavigate)

        // 第四块：数据管理卡片（日程归档、数据备份）
        SidebarDataManagementCard(onNavigate)

        Spacer(modifier = Modifier.weight(1f))
    }
}

// 第一块：顶部操作卡片
@Composable
private fun SidebarTopActionsCard(
    isDarkMode: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 深色/浅色模式切换
            SidebarActionItem(
                icon = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                title = if (isDarkMode) "浅色模式" else "深色模式",
                subtitle = "切换主题外观",
                onClick = { onThemeToggle(!isDarkMode) },
                showChevron = false
            )
            // 关于软件
            SidebarActionItem(
                icon = Icons.Default.Info,
                title = "关于软件",
                subtitle = "版本信息与帮助",
                onClick = onAbout
            )
            // 退出应用
            SidebarActionItem(
                icon = Icons.Default.ExitToApp,
                title = "退出应用",
                subtitle = "安全退出应用",
                onClick = onLogout,
                showChevron = false
            )
        }
    }
}

// 通用操作项组件
@Composable
private fun SidebarActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// 第二块：课表管理卡片
@Composable
private fun SidebarScheduleCard(onNavigate: (SettingsDestination) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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
private fun SidebarOtherSettingsCard(onNavigate: (SettingsDestination) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 模型配置
            SidebarActionItem(
                icon = Icons.Default.Android,
                title = "模型配置",
                subtitle = "API Key 与模型",
                onClick = { onNavigate(SettingsDestination.AI) }
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

// 第四块：数据管理卡片
@Composable
private fun SidebarDataManagementCard(onNavigate: (SettingsDestination) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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
