package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.HomeEntryKey
import com.antgskds.calendarassistant.data.model.homeEntryLabel
import com.antgskds.calendarassistant.data.model.sanitizeHomeBottomItems
import com.antgskds.calendarassistant.data.model.sanitizeHomeStartPageKey
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBar
import com.antgskds.calendarassistant.ui.haptic.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

@Composable
fun getHomeEntryIcon(key: String): Painter {
    return when (key) {
        HomeEntryKey.SIDEBAR -> painterResource(R.drawable.floatingbar_menu)
        HomeEntryKey.TODAY -> painterResource(R.drawable.floatingbar_today)
        HomeEntryKey.NOTE -> painterResource(R.drawable.ic_stat_note)
        HomeEntryKey.ALL -> painterResource(R.drawable.floatingbar_all)
        else -> painterResource(R.drawable.floatingbar_today)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun BottomBarEditorPage(
    settingsViewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val settings by settingsViewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)

    val activeItems = sanitizeHomeBottomItems(settings.homeBottomItems, settings.noteEnabled)
    val startPage = sanitizeHomeStartPageKey(settings.homeStartPageKey, activeItems)

    LaunchedEffect(settings.homeBottomItems, settings.homeStartPageKey, settings.noteEnabled) {
        if (activeItems != settings.homeBottomItems || startPage != settings.homeStartPageKey) {
            settingsViewModel.updatePreference(
                homeBottomItems = activeItems,
                homeStartPageKey = startPage
            )
        }
    }

    fun saveConfig(newItems: List<String>, newStartPage: String? = null) {
        val sanitizedItems = sanitizeHomeBottomItems(newItems, settings.noteEnabled)
        val sanitizedStart = sanitizeHomeStartPageKey(newStartPage ?: startPage, sanitizedItems)
        settingsViewModel.updatePreference(
            homeBottomItems = sanitizedItems,
            homeStartPageKey = sanitizedStart
        )
    }

    val candidates = listOf(HomeEntryKey.TODAY, HomeEntryKey.NOTE, HomeEntryKey.ALL)
    val standbyItems = candidates.filterNot { it in activeItems }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // 0. 顶部实时预览框
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 预览标题
                Text(
                    text = "实时预览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp)
                )

                // 模拟屏幕区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 预留足够的高度（140dp）确保底栏展开时的动画不会被裁切
                        .height(140.dp)
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    var isPreviewExpanded by remember { mutableStateOf(false) }
                    var previewSelectedPage by remember(startPage) { mutableStateOf(startPage) }

                    // 把预览栏放在这个 Box 里面
                    IntegratedFloatingBar(
                        isExpanded = isPreviewExpanded,
                        onExpandedChange = { isPreviewExpanded = it },
                        isSidebarOpen = false,
                        navItems = activeItems,
                        selectedPageKey = previewSelectedPage,
                        onMenuClick = { isPreviewExpanded = false },
                        onPageClick = {
                            isPreviewExpanded = false
                            previewSelectedPage = it
                        },
                        onSearchClick = { isPreviewExpanded = false },
                        onImageClick = { isPreviewExpanded = false },
                        onEditClick = { isPreviewExpanded = false }
                    )
                }
            }
        }

        // 1. 当前底栏设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "当前底栏项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 固定项：侧边栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = getHomeEntryIcon(HomeEntryKey.SIDEBAR),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = homeEntryLabel(HomeEntryKey.SIDEBAR),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "固定",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 动态排序项
                activeItems.forEachIndexed { index, key ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = getHomeEntryIcon(key),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = homeEntryLabel(key),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptics.selection()
                                    val mutable = activeItems.toMutableList()
                                    mutable.removeAt(index)
                                    mutable.add(index - 1, key)
                                    saveConfig(mutable)
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                            }
                            IconButton(
                                onClick = {
                                    haptics.selection()
                                    val mutable = activeItems.toMutableList()
                                    mutable.removeAt(index)
                                    mutable.add(index + 1, key)
                                    saveConfig(mutable)
                                },
                                enabled = index < activeItems.lastIndex
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                            }
                            IconButton(
                                onClick = {
                                    if (activeItems.size <= 1) return@IconButton
                                    haptics.warning()
                                    val mutable = activeItems.toMutableList().apply { remove(key) }
                                    saveConfig(mutable)
                                },
                                enabled = activeItems.size > 1
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "移除",
                                    tint = if (activeItems.size > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 备选区
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "备选页面",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (standbyItems.isEmpty()) {
                    Text(
                        text = "已添加全部可用页面",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        standbyItems.forEach { key ->
                            val noteDisabled = key == HomeEntryKey.NOTE && !settings.noteEnabled

                            ElevatedAssistChip(
                                onClick = {
                                    if (!noteDisabled) {
                                        haptics.click()
                                        val mutable = activeItems.toMutableList()
                                        if (mutable.size < 3) {
                                            mutable.add(key)
                                            saveConfig(mutable)
                                        }
                                    }
                                },
                                label = {
                                    Text(
                                        text = homeEntryLabel(key),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "添加",
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = !noteDisabled,
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    if (standbyItems.contains(HomeEntryKey.NOTE) && !settings.noteEnabled) {
                        Text(
                            text = "添加“便签”需在实验室中先开启便签功能",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // 3. 默认启动页
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "默认启动页",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeItems.forEach { key ->
                        val isSelected = key == startPage
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            label = "segment_bg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "segment_text"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgColor)
                                .clickable { haptics.selection(); saveConfig(activeItems, key) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = homeEntryLabel(key),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                color = textColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Text(
                    text = "打开 App 时默认展示的页面",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    }
}
