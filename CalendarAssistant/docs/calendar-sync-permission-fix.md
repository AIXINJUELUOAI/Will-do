# 日历同步权限问题分析与解决方案

## 问题描述

在 ColorOS (OPPO/realme) 系统上，日历同步开关无法打开，提示"无法获取日历ID"。

## 根本原因

**Android 13+ 日历权限机制导致的**，不是 ColorOS 的问题。

Android 13 将日历权限拆分为三种模式：

| 权限模式 | READ_CALENDAR | WRITE_CALENDAR | 查询日历 | 写入事件 |
|---------|---------------|----------------|---------|---------|
| 允许 | ✅ | ✅ | ✅ | ✅ |
| 仅允许创建 | ❌ | ✅ | ❌ (返回空) | ✅ (需已知calendarId) |
| 不允许 | ❌ | ❌ | ❌ | ❌ |

### 关键点

1. 用户在系统权限弹窗中选择了"仅允许创建"
2. App 只授予了 `WRITE_CALENDAR` 权限，没有 `READ_CALENDAR` 权限
3. 代码中使用 `query Calendars` 查询日历列表，因为没有读权限，返回0条数据
4. 导致后续逻辑判断"没有日历"，开关失效

## 解决方案

### 方案A：引导用户升级权限（推荐）

在检测到用户只有写入权限（"仅允许创建"模式）时，提示用户升级为"允许"权限。

#### 修改内容

1. **CalendarPermissionHelper.kt** - 新增权限检测方法：
   - `hasReadPermission()` - 检查是否有读权限
   - `hasWritePermission()` - 检查是否有写权限
   - `hasOnlyWritePermission()` - 检查是否是"仅创建"模式

2. **PreferenceSettingsPage.kt** - 增加权限判断逻辑：
   - 如果是完全权限（读写）→ 直接开启
   - 如果是"仅创建"模式 → 弹出对话框引导用户升级
   - 如果没有权限 → 请求权限

### 方案B：兼容"仅创建"模式（备选）

如果不依赖 query，直接尝试用常见默认日历ID（如1）写入，通过写入结果判断是否成功。

## 修改文件列表

1. `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarPermissionHelper.kt`
   - 新增 `hasReadPermission()`、`hasWritePermission()`、`hasOnlyWritePermission()` 方法

2. `app/src/main/java/com/antgskds/calendarassistant/ui/page_display/settings/PreferenceSettingsPage.kt`
   - 新增 `showUpgradePermissionDialog` 状态
   - 新增升级权限对话框
   - 修改开关点击逻辑，区分三种权限状态

3. `app/src/main/java/com/antgskds/calendarassistant/ui/viewmodel/SettingsViewModel.kt`
   - 修复错误处理，返回 Result 以便 UI 层判断成功/失败

4. `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarManager.kt`
   - 增加调试日志（开发阶段）
   - 尝试获取只读日历作为备用

## 测试步骤

1. 在 Android 13+ 设备上安装 App
2. 首次打开时授予日历权限，选择"仅允许创建"
3. 进入偏好设置，点击"日历同步"开关
4. 应该弹出对话框提示升级权限
5. 点击"去设置"，将权限改为"允许"
6. 重新点击开关，应该可以正常开启

## 相关日志（调试用）

```
权限状态: READ=0 (0=GRANTED), WRITE=0 (0=GRANTED)
查询到 0 个日历 (最小访问级别: 500)
未找到可写日历，尝试获取只读日历
```

## 分支信息

- 分支名：`fix/calendar-sync-issue`
- 修复日期：2026-03-04
