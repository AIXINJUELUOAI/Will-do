package com.antgskds.calendarassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
// 【关键修复】引用新位置的 NotificationScheduler
import com.antgskds.calendarassistant.service.notification.NotificationScheduler

// --- 1. Expandable Group Component ---

@Composable
fun ExpandableSettingsGroup(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    showDivider: Boolean = true,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onExpandedChange(!expanded) }
                .padding(vertical = 18.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "arrow")
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = Color.Gray
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
                content()
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

// --- 2. Wheel Pickers ---

@Composable
fun WheelPicker(items: List<String>, initialIndex: Int, modifier: Modifier = Modifier, onSelectionChanged: (Int) -> Unit) {
    val listState = rememberPagerState(initialPage = initialIndex) { items.size }
    LaunchedEffect(listState.currentPage) { onSelectionChanged(listState.currentPage) }
    Box(modifier.height(175.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal=4.dp).height(35.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(12.dp)))
        VerticalPager(state = listState, contentPadding = PaddingValues(vertical = 70.dp), modifier = Modifier.fillMaxSize()) { page ->
            val pageOffset = (listState.currentPage - page) + listState.currentPageOffsetFraction
            val alpha = (1f - (Math.abs(pageOffset) * 0.6f)).coerceAtLeast(0.2f)
            Box(Modifier.height(35.dp), contentAlignment = Alignment.Center) {
                Text(text = items[page], fontSize = 18.sp, fontWeight = if (Math.abs(pageOffset) < 0.5) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth().alpha(alpha))
            }
        }
    }
}

@Composable
fun WheelDatePickerDialog(initialDate: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = { WheelDatePicker(initialDate, { selectedDate = it }) },
        confirmButton = { TextButton(onClick = { onConfirm(selectedDate) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun WheelDatePicker(initialDate: LocalDate, onDateChanged: (LocalDate) -> Unit) {
    val years = (2020..2035).toList(); val months = (1..12).toList()
    var sY by remember { mutableIntStateOf(initialDate.year) }
    var sM by remember { mutableIntStateOf(initialDate.monthValue) }
    var sD by remember { mutableIntStateOf(initialDate.dayOfMonth) }
    val daysInMonth = remember(sY, sM) { YearMonth.of(sY, sM).lengthOfMonth() }
    LaunchedEffect(daysInMonth) { if (sD > daysInMonth) sD = daysInMonth; onDateChanged(LocalDate.of(sY, sM, sD)) }
    LaunchedEffect(sY, sM, sD) { if (sD <= daysInMonth) onDateChanged(LocalDate.of(sY, sM, sD)) }
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WheelPicker(years.map{"${it}年"}, years.indexOf(sY).coerceAtLeast(0), Modifier.weight(1.3f)) { sY = years[it] }
        WheelPicker(months.map{String.format("%02d月",it)}, months.indexOf(sM).coerceAtLeast(0), Modifier.weight(1f)) { sM = months[it] }
        WheelPicker((1..daysInMonth).map{String.format("%02d日",it)}, (sD-1).coerceIn(0,daysInMonth-1), Modifier.weight(1f)) { sD = it+1 }
    }
}

@Composable
fun WheelTimePickerDialog(initialTime: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val parts = initialTime.split(":")
    val h = parts.getOrElse(0) { "09" }.toIntOrNull() ?: 9
    val m = parts.getOrElse(1) { "00" }.toIntOrNull() ?: 0
    var sH by remember { mutableIntStateOf(h) }
    var sM by remember { mutableIntStateOf(m) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = { WheelTimePicker(h, m, { hh, mm -> sH = hh; sM = mm }) },
        confirmButton = { TextButton(onClick = { onConfirm(String.format("%02d:%02d", sH, sM)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun WheelTimePicker(initialHour: Int, initialMinute: Int, onTimeChanged: (Int, Int) -> Unit) {
    val hours = (0..23).toList(); val minutes = (0..59).toList()
    var cH by remember { mutableIntStateOf(initialHour) }; var cM by remember { mutableIntStateOf(initialMinute) }
    LaunchedEffect(cH, cM) { onTimeChanged(cH, cM) }
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        WheelPicker(hours.map{String.format("%02d",it)}, initialHour, Modifier.weight(1f)) { cH = hours[it] }
        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
        WheelPicker(minutes.map{String.format("%02d",it)}, initialMinute, Modifier.weight(1f)) { cM = minutes[it] }
    }
}

@Composable
fun WheelReminderPickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    // 【关键修复】现在能正确引用了
    val options = NotificationScheduler.REMINDER_OPTIONS
    val defaultIndex = options.indexOfFirst { it.first == initialMinutes }.takeIf { it != -1 }
        ?: options.indexOfFirst { it.first == 30 }.takeIf { it != -1 } ?: 4

    var selectedIndex by remember { mutableIntStateOf(defaultIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加提醒", style = MaterialTheme.typography.titleMedium) },
        text = {
            WheelPicker(
                items = options.map { it.second },
                initialIndex = defaultIndex,
                onSelectionChanged = { selectedIndex = it }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(options[selectedIndex].first)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}