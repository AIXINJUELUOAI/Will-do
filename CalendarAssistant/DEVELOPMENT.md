# CalendarAssistant 开发日志

## 2026-01-31 开发记录

### 1. AI配置页Material 3风格重构
- **文件**: `ui/page_display/settings/AiSettingsPage.kt`
- **改动**:
  - 全面采用Material 3设计规范
  - 新增服务提供商快速选择：DeepSeek、OpenAI、Gemini、自定义
  - 服务提供商自动填充对应的API地址和模型列表
  - 新增模型名称下拉选择器（根据提供商自动更新可选模型）
  - 优化样式层次：
    - `sectionTitleStyle`: Primary色 + ExtraBold字重
    - `cardTitleStyle`: OnSurface色 + Medium字重
    - `cardValueStyle`: OnSurfaceVariant色 + Normal字重
    - `cardSubtitleStyle`: 半透明灰色
  - 支持DeepSeek、OpenAI、Gemini主流AI服务商
  - 自定义模式支持任意API地址

---

### 2. 作息表编辑器样式优化
- **文件**: `ui/page_display/settings/TimeTableEditorScreen.kt`
- **改动**:
  - 全面采用Material 3设计规范
  - 字体样式优化：
    - `sectionHeaderStyle`: Primary色 + Bold（板块标题）
    - `cardTimeStyle`: OnSurface色 + Medium（时间段）
    - `cardDurationStyle`: OnSurfaceVariant色（时长）
  - 卡片阴影移除（`defaultElevation = 0.dp`）
  - 午休/晚饭分割线视觉优化
  - 课间休息时间可点击调整（5-40分钟）
  - 上午/下午/晚上开始时间可点击修改

---

### 3. 多日日程显示问题修复
- **提交**: `72bb756 修复多日日程显示问题并优化排序逻辑`
- **改动**:
  - 修复跨越多天的日程在日程列表中的显示问题
  - 优化事件排序逻辑，确保按开始时间正确排序

---

### 4. 实况通知显示问题修复
- **提交**: `9c4e158 修复实况通知显示问题和深浅模式切换时 topbar 不同步`
- **改动**:
  - 修复Flyme实况通知展开后内容空白的问题
  - 修复深色/浅色模式切换时TopBar样式不同步的问题

---

### 5. 事件列表排序优化
- **提交**: `56fcd22 优化事件列表排序逻辑并更换胶囊图标`
- **改动**:
  - 优化事件列表的排序算法
  - 更新实况胶囊图标资源

---

## 2026-01-30 开发记录

### 1. 新增课程功能优化
- **文件**: `ui/dialogs/CourseManagementDialog.kt`
- **改动**:
  - 删除了固定的20色颜色库（原第73-79行）
  - 新增课程时随机使用 `Color.kt` 中 `EventColors` 的颜色
  - 移除了颜色选择UI（"颜色标签"标题和颜色选择圆形按钮区域）
  - 清理了不再使用的导入：`CircleShape`、`@OptIn(ExperimentalLayoutApi::class)`

---

### 2. 作息表默认时长修复
- **文件**: `ui/page_display/settings/TimeTableEditorScreen.kt`
- **改动**:
  - 注释掉了从已保存数据读取课程时长的代码（第90行）
  - 确保默认使用45分钟作为课程时长

---

### 3. 作息表页面FAB按钮统一样式
- **文件**: `ui/page_display/settings/TimeTableEditorScreen.kt`
- **改动**:
  - 添加了 `CircleShape` 导入
  - 添加了 `uiSize` 参数支持动态大小调整
  - 将两个FAB（节数设置、保存）改为横向排列
  - 统一颜色为 `primary` / `onPrimary`
  - 样式与主页FAB一致：圆形 + 动态大小（56dp/64dp/72dp）

---

### 4. AI配置页FAB按钮统一样式
- **文件**: `ui/page_display/settings/AiSettingsPage.kt`
- **改动**:
  - 添加了 `CircleShape` 和 `Icons.Default.Check` 导入
  - 添加了动态大小支持（基于 `uiSize`）
  - 将保存按钮改为右下角圆形FAB，带对勾图标
  - 移除了表单底部的保存按钮
  - 增加了底部padding防止内容被FAB遮挡（120dp）

---

### 5. AI配置页UI重构
- **文件**: `ui/page_display/settings/AiSettingsPage.kt`
- **改动**:
  - **删除了顶部的"模型配置"说明卡片**
  - **状态提升**: 将 `modelUrl`、`modelName`、`modelKey` 状态从 `AiConfigForm` 提升到 `AiSettingsPage`
  - **新UI设计**:
    - 使用一个 Card 包裹所有配置项
    - 去掉边框，改用 `MyDivider` 分割线
    - 左标题右信息的布局
    - 右对齐的展开列表选项
  - **新增组件**:
    - `ExpandableSelectionItem`: 可展开的选择项（点击右侧信息展开列表，卡片高度随列表伸缩）
    - `TextInputItem`: 无边框文本输入项（使用 `BasicTextField`）
    - `MyDivider`: 自定义分割线（左侧留白）
  - **动画效果**: 使用 `AnimatedVisibility` 实现展开/收起动画
  - **智能展开**: 当 API Key 为空时，服务提供商和模型名称默认展开

---

### 6. 设置页面字体大小动态调控
为所有设置页面添加了 `uiSize` 参数支持，用户可在"偏好设置"中调整界面大小（小/中/大）。

#### 修改的文件:

**SettingsDetailScreen.kt**
- 将 `uiSize` 传递给所有设置页面

**AiSettingsPage.kt**
- 添加 `uiSize` 参数
- 计算 `bodyLargeStyle`：小=bodyMedium, 中=bodyLarge, 大=headlineSmall
- 传递给 `ExpandableSelectionItem` 和 `TextInputItem`

**TimeTableEditorScreen.kt**
- 添加 `uiSize` 参数
- 计算 `titleMediumStyle`、`bodyMediumStyle`、`titleTextStyle`
- 应用到所有文字和 SectionHeader

**AboutPage.kt**
- 添加 `uiSize` 参数
- 计算多种字体样式
- 传递给 `ContributorLine`

**PreferenceSettingsPage.kt**
- 添加 `uiSize` 参数
- 计算字体样式
- 传递给 `SwitchSettingItem` 和 `SliderSettingItem`

**ScheduleSettingsPage.kt**
- 添加 `uiSize` 参数
- 计算字体样式
- 传递给 `SettingItem`

**BackupSettingsPage.kt**
- 添加 `uiSize` 参数
- 计算字体样式
- 传递给 `BackupCard` 和 `ImportOptionRadio`

---

### 7. 去掉卡片阴影
- **文件**: `ui/page_display/settings/AiSettingsPage.kt`
- **改动**: 将 AI 配置卡片 `defaultElevation` 从 `2.dp` 改为 `0.dp`

---

## 待办事项
- [ ] 继续优化其他页面的字体大小调控
- [ ] 测试所有修改的功能

---

## 技术要点

### 状态提升 (State Hoisting)
- 将表单状态从子组件提升到父组件，便于 FAB 直接访问
- 使用回调函数更新状态

### 动态样式计算
```kotlin
val bodyLargeStyle = when (uiSize) {
    1 -> MaterialTheme.typography.bodyMedium
    2 -> MaterialTheme.typography.bodyLarge
    else -> MaterialTheme.typography.headlineSmall
}
```

### 展开/收起动画
```kotlin
AnimatedVisibility(
    visible = isExpanded,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut()
)
```
