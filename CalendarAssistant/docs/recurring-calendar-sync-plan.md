# 重复日程反向同步改造计划

更新时间：2026-03-08

## 背景

当前系统日历反向同步读取的是 `CalendarContract.Events`，这对普通单次日程有效，但对重复日程只会拿到规则定义，无法拿到实际发生的实例，因此像“每天 06:00”这类系统日历重复日程不会正确进入 App。

同时，如果直接把重复日程平铺为大量普通事件，会带来两个问题：

1. 列表页被大量重复实例刷屏
2. 自动提醒 / 胶囊 / 正向同步可能产生洪泛或错误回写

本次改造采用“参考课表父子节点”的方案处理重复日程。

## 已确认的产品决策

1. 系统日历重复日程改为读取 `CalendarContract.Instances`
2. 同步窗口固定为“过去 30 天到未来 30 天”
3. 重复日程采用“父节点 + 子节点”模型
4. 全部列表页中，重复日程只显示 1 条父节点摘要
5. 父节点显示重复 icon，用于提示这是重复系列
6. 点击父节点后，默认针对“下次实例”进入编辑
7. Dialog 底部增加说明：
   - 此日程为重复日程
   - 下一次提醒时间：`yyyy-MM-dd HH:mm`
   - 本次修改将应用到下次实例，并脱离重复系列
8. 用户编辑保存后，不直接修改原重复系列，而是：
   - 生成一个脱离系列的本地单次事件
   - 同时把原实例加入父节点排除列表，避免后续同步再次导回
9. 系统导入的重复父节点 / 子节点默认不生成 App 提醒、不进入胶囊
10. 系统导入的重复节点不参与 App -> 系统日历正向回写

## 总体设计

### 1. 数据模型

在 `MyEvent` 中增加重复系列相关字段，预计至少包括：

- `isRecurring: Boolean = false`
- `isRecurringParent: Boolean = false`
- `parentRecurringId: String? = null`
- `recurringSeriesKey: String? = null`
- `recurringInstanceKey: String? = null`
- `excludedRecurringInstances: List<String> = emptyList()`
- `nextOccurrenceStartMillis: Long? = null`

说明：

- 父节点：`isRecurring = true` 且 `isRecurringParent = true`
- 子节点：`isRecurring = true` 且 `isRecurringParent = false`
- 脱离后的单次事件：普通 `MyEvent`，`isRecurring = false`

### 2. 父子节点语义

#### 父节点（系列摘要）

职责：

- 只用于“全部列表页”聚合展示
- 保存系列级信息，如标题、地点、备注、下一次实例时间
- 保存排除列表，记录哪些实例已经被“脱离编辑”覆盖

建议 ID：

- `recurring_parent_<systemEventId>` 或等价稳定 key

#### 子节点（实际实例）

职责：

- 用于主页 / 当天列表 / 明天列表按具体日期展示
- 与系统日历 `Instances` 一一对应

建议 ID：

- `recurring_instance_<systemEventId>_<beginMillis>`

实例唯一键：

- `instanceKey = <systemEventId>_<beginMillis>`

## 同步层方案

### 1. 数据源切换

`CalendarManager` 中新增基于 `CalendarContract.Instances` 的查询方法。

要求：

- 查询窗口严格限制为过去 30 天到未来 30 天
- 只读取目标日历 `targetCalendarId`
- 读取足够字段用于：
  - 标题 / 地点 / 描述
  - 开始 / 结束时间
  - 识别是否为重复日程（如 `RRULE`）
  - 生成系列 key 和实例 key

### 2. 同步窗口

固定常量：

- `SYNC_LOOK_BACK_DAYS = 30`
- `SYNC_LOOK_AHEAD_DAYS = 30`

原因：

- 减少一次同步展开的实例数量
- 避免提醒、胶囊、列表和存储被 180 天实例压爆
- 足够覆盖近期使用场景

### 3. 反向同步处理

`CalendarSyncManager` 调整为两类路径：

#### 普通单次事件

- 继续使用现有普通事件同步逻辑

#### 重复事件实例

- 从 `Instances` 读取窗口内实例
- 先按系列分组，再生成：
  - 1 个父节点
  - N 个子节点
- 对父节点维护下一次实例时间
- 跳过已被父节点 `excludedRecurringInstances` 记录的实例

### 4. 删除与更新策略

删除时只处理“当前窗口内本应存在但已经消失”的重复子节点，不删除窗口外数据。

更新策略：

- 父节点：由系列摘要重新计算并覆盖
- 子节点：按 `recurringInstanceKey` 更新
- 若实例已被用户脱离编辑，则不再从系统端覆盖该实例对应的本地单次事件

## UI 方案

### 1. 全部列表页

`AllEventsPage` 中：

- 普通事件：正常显示
- 重复子节点：不直接显示
- 重复父节点：每个系列只显示 1 条
- 在条目中加入重复 icon

父节点展示重点：

- 标题
- 时间（以下次实例时间为主）
- 必要时可附加“重复日程”弱提示

### 2. 首页 / 当天列表 / 明天列表

这些页面只显示重复子节点，不显示父节点，避免同一系列在日历型页面中重复两次。

### 3. 编辑弹窗

从“全部列表页”的重复父节点点击进入编辑时：

- 默认取“下次实例”作为编辑基准
- 预填标题、时间、地点、备注
- 底部显示说明：
  - 此日程为重复日程
  - 下一次提醒时间：`yyyy-MM-dd HH:mm`
  - 本次修改将应用到下次实例，并脱离重复系列

保存后行为：

1. 新建一个普通单次事件
2. 将该实例 key 写入父节点的 `excludedRecurringInstances`
3. 后续同步不再重新导入该实例

## 提醒 / 胶囊 / 正向同步保护

### 1. 提醒

系统导入的重复父节点 / 子节点：

- 不调用 `NotificationScheduler.scheduleReminders`
- 不生成全局提前提醒
- 不生成“开始时”提醒

只有用户编辑脱离后生成的单次事件，才走普通提醒逻辑。

### 2. 胶囊

系统导入的重复父节点 / 子节点：

- 不参与胶囊激活
- 不参与胶囊结束闹钟

### 3. App -> 系统日历

系统导入的重复父节点 / 子节点：

- 不参与正向同步
- 避免把系统 RRULE 系列错误写回为普通单次事件

## 参考课表的映射关系

本方案参考课表当前机制：

- 课表主课程 = 重复日程父节点
- 影子课程 = 用户脱离出来的单次本地事件
- `excludedDates` = `excludedRecurringInstances`
- `parentCourseId` = `parentRecurringId`

但两者并不完全相同：

- 课表由本地规则展开
- 重复日程由系统日历 `Instances` 作为唯一真实来源

## 预期修改文件

- `app/src/main/java/com/antgskds/calendarassistant/data/model/MyEvent.kt`
- `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarManager.kt`
- `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarSyncManager.kt`
- `app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepository.kt`
- `app/src/main/java/com/antgskds/calendarassistant/ui/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/antgskds/calendarassistant/ui/page_display/AllEventsPage.kt`
- `app/src/main/java/com/antgskds/calendarassistant/ui/event_display/SwipeableEventItem.kt`
- `app/src/main/java/com/antgskds/calendarassistant/ui/dialogs/AddEventDialog.kt`

## 分阶段实施建议

### 阶段 1：数据与同步

- 给 `MyEvent` 增加重复字段
- `CalendarManager` 接入 `Instances`
- `CalendarSyncManager` 构建父子节点
- 限制窗口为过去 30 / 未来 30 天

### 阶段 2：UI 与编辑脱离

- 全部列表页只显示父节点
- 增加重复 icon
- Dialog 底部增加重复说明
- 编辑父节点时默认取下次实例
- 保存后生成脱离单次事件并写入排除列表

### 阶段 3：保护逻辑

- 跳过重复节点提醒
- 跳过重复节点胶囊
- 跳过重复节点正向同步
- 校验主页 / 列表不重复展示

## 验收要点

1. 系统日历“每天 06:00”能在 App 中正确出现
2. 全部列表页只显示该系列 1 条摘要
3. 主页 / 某天列表只显示当天那一次实例
4. 点击系列条目进入编辑时，默认基于下次实例
5. Dialog 底部能显示重复提示与下次时间
6. 保存后生成新的单次本地事件，且原实例不再重复导回
7. 重复导入实例不会生成提醒、胶囊和错误正向同步
