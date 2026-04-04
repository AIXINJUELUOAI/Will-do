# Tag 筛选 Chip 条 — 开发计划

## 一、功能目标

为列表页（今日/全部）添加顶部 tag 筛选条，用户点击 chip 可按事件类型过滤。

- 默认展示 4 个 chip：日程、列车、取件、取餐
- 点击筛选，再点取消（回到"全部"）
- 用户可在设置页自定义显示哪些 chip（8 种可选）
- "全部" chip 始终存在，不属于自定义范围

---

## 二、Tag 分类体系

8 种 tag 分两类：

| 类别 | 标签 |
|------|------|
| 日程类 | 日程(general)、列车(train)、打车(taxi)、航班(flight) |
| 取件类 | 取件(pickup)、取餐(food)、取票(ticket)、寄件(sender) |

---

## 三、改动文件清单

### 1. `data/model/MySettings.kt`
新增字段：
```kotlin
val visibleTagChips: List<String> = listOf("general", "train", "pickup", "food")
```
序列化自动兼容旧数据（默认值机制）。

### 2. `data/model/MyEvent.kt`
EventTags 对象新增：
- `displayName(tag: String): String` — tag key → 中文显示名
- `ALL_TAGS: List<String>` — 全部 8 个 tag

### 3. 新建 `ui/components/TagFilterChipBar.kt`
可复用筛选条 Composable：
- 参数：`visibleTags: List<String>`, `selectedTag: String?`, `onTagSelected: (String?) -> Unit`
- 横向滚动 Row + Material3 FilterChip
- 第一个是"全部"，后续为用户配置的 tag
- 点击已选中 chip → 取消选中

### 4. `ui/page_display/AllEventsPage.kt`
- 新增参数 `selectedTag`, `onTagSelected`
- Column 顶部添加 TagFilterChipBar
- `filteredEvents` 的 `derivedStateOf` 增加 tag 过滤逻辑

### 5. `ui/page_display/HomePage.kt`
- 新增 `todaySelectedTag`, `allSelectedTag` 状态
- Tab 0：todayEvents/tomorrowEvents 加 tag 过滤，LazyColumn 中加 TagFilterChipBar
- Tab 1：透传 allSelectedTag 给 AllEventsPage
- BackHandler 退出搜索时清除 tag 选择

### 6. `ui/viewmodel/SettingsViewModel.kt`
- `updatePreference` 新增 `visibleTagChips: List<String>?` 参数

### 7. `ui/page_display/settings/PreferenceSettingsPage.kt`
- 添加可展开的"筛选标签"配置项
- 展示 8 个 tag 开关，至少保留 1 个

---

## 四、数据流

```
用户点击 chip → onTagSelected(tag)
  → 页面状态 selectedTag 更新
  → derivedStateOf 重新过滤事件列表
  → UI 刷新

设置页修改 → updatePreference(visibleTagChips = ...)
  → MySettings 持久化
  → 下次加载时读取新配置
```

---

## 五、验证要点

- [ ] 今日 tab / 全部 tab 都能看到 chip 条
- [ ] 点击 chip 正确过滤事件，再点取消
- [ ] 设置页可增删 chip，重启后保留
- [ ] 搜索 + tag 筛选可同时生效
- [ ] 默认 4 个 chip 与设置一致
