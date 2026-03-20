package com.antgskds.calendarassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

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
    val navItemWidth = 76.dp
    val navItemSpacing = 4.dp
    val navPaddingHorizontal = 6.dp

    val navExpandedWidth = navItemWidth * 3f + navItemSpacing * 2f + navPaddingHorizontal * 2f
    val navCollapsedWidth = navHeight
    val fabCollapsedWidth = fabSize

    val navWidth by animateDpAsState(
        targetValue = if (isExpanded) navCollapsedWidth else navExpandedWidth,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navWidth"
    )
    val iconAreaWidth by animateDpAsState(
        targetValue = if (isExpanded) navExpandedWidth - navCollapsedWidth else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iconAreaWidth"
    )
    val actionWidth = fabCollapsedWidth + iconAreaWidth

    val isMenuSelected = isSidebarOpen
    val isTabHighlightEnabled = !isSidebarOpen
    val currentTabIcon = if (selectedTab == 0) Icons.Default.Home else Icons.Default.List
    val currentTabClick = if (selectedTab == 0) onHomeClick else onListClick

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Card(
                shape = navShape,
                colors = CardDefaults.cardColors(containerColor = navBg),
                elevation = CardDefaults.cardElevation(defaultElevation = navElevation),
                modifier = Modifier
                    .height(navHeight)
                    .width(navWidth)
            ) {
                if (isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = navPaddingHorizontal, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        HydrogenNavIcon(
                            icon = if (isSidebarOpen) Icons.Default.Menu else currentTabIcon,
                            isSelected = true,
                            indicatorColor = navIndicator,
                            contentColor = navContent,
                            onClick = {
                                onExpandedChange(false)
                                if (isSidebarOpen) {
                                    onMenuClick()
                                } else {
                                    currentTabClick()
                                }
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = navPaddingHorizontal, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(navItemSpacing)
                    ) {
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            Card(
                shape = fabShape,
                colors = CardDefaults.cardColors(containerColor = fabBg),
                elevation = CardDefaults.cardElevation(defaultElevation = fabElevation),
                modifier = Modifier
                    .height(fabSize)
                    .width(actionWidth)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(iconAreaWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ActionIconButton(Icons.Default.Search, "搜索", fabIcon, onSearchClick)
                                ActionIconButton(Icons.Default.Image, "图片", fabIcon, onImageClick)
                                ActionIconButton(Icons.Default.Edit, "新建", fabIcon, onEditClick)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(fabCollapsedWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { onExpandedChange(!isExpanded) }) {
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
    }
}

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
            .width(76.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
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
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
