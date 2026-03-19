package com.antgskds.calendarassistant.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 统一高度设定为 68dp (增强视觉存在感)
val IntegratedFloatingBarHeight = 68.dp
val IntegratedFloatingBarBottomSpacing = 24.dp

// --- Hydrogen 核心配色 (极低饱和度) ---
val HydrogenBg = Color(0xFFF1F1EA)      // 奶油白底色
val HydrogenIndicator = Color(0xFFE2E2D5) // 稍微深一点的奶油灰
val HydrogenContent = Color(0xFF44473E)   // 深橄榄绿/灰 (用于图标和文字)
val HydrogenFab = Color(0xFF4B5541)       // 深色按钮，形成视觉对比
val HydrogenFabIcon = Color(0xFFF1F1EA)

@Composable
fun IntegratedFloatingBar(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isSidebarOpen: Boolean = false,
    selectedTab: Int,
    onMenuClick: () -> Unit,
    onHomeClick: () -> Unit,
    onListClick: () -> Unit,
    onSearchClick: () -> Unit,
    onImageClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fabRotation"
    )

    val mdBlend = 1.0f
    val navBg = lerp(HydrogenBg, MaterialTheme.colorScheme.surfaceContainerLow, mdBlend)
    val navIndicator = lerp(HydrogenIndicator, MaterialTheme.colorScheme.secondaryContainer, mdBlend)
    val navContent = lerp(HydrogenContent, MaterialTheme.colorScheme.onSurfaceVariant, mdBlend)
    val fabBg = lerp(HydrogenFab, MaterialTheme.colorScheme.primary, mdBlend)
    val fabIcon = lerp(HydrogenFabIcon, MaterialTheme.colorScheme.onPrimary, mdBlend)
    val navShape = CircleShape
    val fabShape = RoundedCornerShape(22.dp)
    val navElevation = 6.dp
    val fabElevation = 6.dp
    val navHeight = IntegratedFloatingBarHeight + 4.dp
    val fabSize = IntegratedFloatingBarHeight + 4.dp

    val isMenuSelected = isSidebarOpen
    val isTabHighlightEnabled = !isSidebarOpen

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // --- 1. 垂直菜单 (Hydrogen 风格药丸) ---
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = IntegratedFloatingBarHeight + 16.dp, end = 4.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuActionPill(Icons.Default.Search, "搜索日程", navBg, navContent, onSearchClick)
                MenuActionPill(Icons.Default.Image, "导入图片", navBg, navContent, onImageClick)
                MenuActionPill(Icons.Default.Edit, "新建日程", navBg, navContent, onEditClick)
            }
        }

        // --- 2. 底栏主体 (严格参考 Hydrogen 比例) ---
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 左侧：Hydrogen 岛
            Card(
                shape = navShape,
                colors = CardDefaults.cardColors(containerColor = navBg),
                elevation = CardDefaults.cardElevation(defaultElevation = navElevation),
                modifier = Modifier
                    .height(navHeight)
                    .weight(1f, fill = false)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 菜单图标
                    HydrogenNavIcon(
                        icon = Icons.Default.Menu,
                        isSelected = isMenuSelected,
                        indicatorColor = navIndicator,
                        contentColor = navContent,
                        onClick = onMenuClick
                    )
                    
                    HydrogenNavIcon(
                        icon = Icons.Default.Home,
                        isSelected = isTabHighlightEnabled && selectedTab == 0,
                        indicatorColor = navIndicator,
                        contentColor = navContent,
                        onClick = onHomeClick
                    )
                    
                    HydrogenNavIcon(
                        icon = Icons.Default.List,
                        isSelected = isTabHighlightEnabled && selectedTab == 1,
                        indicatorColor = navIndicator,
                        contentColor = navContent,
                        onClick = onListClick
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 右侧：FAB (尺寸加大)
            Card(
                onClick = { onExpandedChange(!isExpanded) },
                shape = fabShape,
                colors = CardDefaults.cardColors(containerColor = fabBg),
                elevation = CardDefaults.cardElevation(defaultElevation = fabElevation),
                modifier = Modifier.size(fabSize)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Toggle",
                        tint = fabIcon,
                        modifier = Modifier
                            .size(34.dp)
                            .rotate(rotation)
                    )
                }
            }
        }
    }
}

/**
 * 关键组件：Hydrogen 风格的导航项
 * 指示器不再是扁平药丸，而是填充容器高度的圆角块
 */
@Composable
private fun HydrogenNavIcon(
    icon: ImageVector,
    isSelected: Boolean,
    indicatorColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(76.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // Hydrogen 的指示器：高度几乎撑满，圆角稍小
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                    .background(indicatorColor, CircleShape)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun MenuActionPill(
    icon: ImageVector,
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = backgroundColor,
        modifier = Modifier.clickable { onClick() },
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = contentColor)
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = contentColor
            )
        }
    }
}
