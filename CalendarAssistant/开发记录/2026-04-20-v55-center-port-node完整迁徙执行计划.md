# 2026-04-20 v55 Center-Port-Node 完整迁徙执行计划

## 1. 文档目的

- 冻结后续重构执行基线，避免方案漂移。
- 明确每个里程碑的改造范围、验收标准与回滚边界。
- 作为后续每一轮提交与回归的唯一对照文档。

> 当前状态：仅计划，不执行语义改动。

---

## 2. 总体策略

- 统一调用方向：`调用方(UI/Service/Receiver/Worker) -> Center -> Port -> Node`。
- 迁徙方式：纵向流程切片为主，横向护栏约束为辅。
- 推进原则：节奏激进，回滚保守；每个 Wave 必须删旧入口，避免长期双轨。

---

## 3. 当前代码总览（执行前基线）

### 3.1 已完成基础

- 写链路已 API 化（`core/operation` + `data/operation`）。
- 读链路已部分 API 化（`core/query` + `data/query`）。
- `App` 已提供 `scheduleOperationApi/settingsOperationApi/scheduleQueryApi/settingsQueryApi` 注入入口。

### 3.2 主要问题

- `AppRepository` 体量过大（约 1900+ 行），承担过多业务编排职责。
- 胶囊链路存在反向耦合：`AppRepository` 持有 `CapsuleStateManager`，`CapsuleStateManager` 反依赖 `AppRepository`。
- 提醒策略分散在 `NotificationScheduler`、`AlarmReceiver`、`CapsuleStateManager`，互斥决策非单点。
- 同步触发入口分散（`App.kt` 内嵌 `CalendarSyncReceiver`、`Worker`、`BootReceiver`）。
- OCR/SMS/通知监听/内容观察四路入库存在重复去重与重复转换逻辑。

---

## 4. Center/Port/Node 目标定稿

### 4.1 Center 清单

1. `ScheduleCenter`
2. `RecurringCenter`
3. `ArchiveCenter`
4. `ReminderCenter`
5. `SyncCenter`
6. `CourseCenter`
7. `ImportCenter`
8. `SettingsCenter`
9. `RuleCenter`

### 4.2 Port 清单（目标态）

- `ScheduleStatePort`
- `EventStorePort`
- `ArchiveStorePort`
- `CourseStorePort`
- `SettingsStorePort`
- `SyncMappingPort`
- `RuleStorePort`
- `ProviderEventPort`
- `ProviderRecurringPort`
- `ProviderMetaPort`
- `ProviderArchivePort`
- `CalendarQueryPort`
- `CalendarSyncPort`
- `ReminderPolicyPort`
- `ReminderChannelPort`
- `LiveSurfacePort`

### 4.3 Node（按当前代码映射）

- 存储 Node：`data/repository/*` + `data/source/*`
- Room Node：`data/db/*` + `data/db/reader/RoomEventReader.kt` + `data/db/shadow/RoomEventShadowWriter.kt`
- 同步 Node：`core/calendar/CalendarManager.kt`、`core/calendar/CalendarSyncManagerV2.kt`、`core/calendar/CalendarSyncGateway.kt`
- 提醒 Node：`service/notification/NotificationScheduler.kt`、`service/receiver/AlarmReceiver.kt`
- 胶囊渲染 Node：`core/capsule/CapsuleStateManager.kt`、`service/capsule/provider/*`、`service/capsule/miui/MiuiIslandManager.kt`
- 入库 Node：`service/receiver/SmsReceiver.kt`、`service/receiver/SmsNotificationListenerService.kt`、`core/sms/SmsContentObserver.kt`、`service/accessibility/TextAccessibilityService.kt`

---

## 5. 分阶段执行计划（Wave）

## Wave 0（0.5-1 天）护栏与基线

目标：冻结边界，防止新增代码回流旧链路。

- 建立调用方禁直连规则（UI/Service/Receiver/Worker 禁止直连 `AppRepository`/`AppDatabase`）。
- 更新并校正文档中的 Node 映射（移除失效路径，替换为现有代码路径）。
- 输出存量违规清单，绑定到后续 Wave 逐项清除。

DoD：新增代码零违规；存量问题清单化。

## Wave 1（2 天）ScheduleCenter 收口（M1）

目标：先统一动作流，确保同语义单入口。

- 引入 `ScheduleCenter` 与对应 Command/Query Port。
- 迁移入口：`MainViewModel`、`SettingsViewModel`、`EventActionReceiver`、`FloatingScheduleService`。
- 保持语义不变，仅调整调用路径。

DoD：动作链路仅经 `ScheduleCenter`；旧入口移除。

## Wave 2（2 天）ReminderCenter + CapsuleCenter（M2）

目标：提醒与胶囊单点决策，解除仓储反向耦合。

- 引入 `ReminderCenter`、`CapsuleCenter`、`CapsuleCommandApi`。
- 提醒互斥决策统一放入 `ReminderCenter`。
- `CapsuleStateManager` 改依赖 Query/Render Port，不再依赖 `AppRepository`。
- 移除 `AppRepository` 中 `capsuleStateManager` 持有。

DoD：调用方不再访问 `repository.capsuleStateManager`；互斥逻辑单点化。

## Wave 3（1.5-2 天）SyncCenter 收口（M3）

目标：同步入口统一、读写职责分离。

- 引入 `SyncCenter`。
- `SettingsOperationApi` 去除读方法，读能力收敛到 Query API。
- `CalendarSyncReceiver` 从 `App.kt` 中拆出为独立文件。
- Worker/Receiver 通过 API/Center 触发，不直接拿 Repository 决策。

DoD：同步链路无双轨；触发入口统一。

## Wave 4（2 天）ImportCenter 收口（M4）

目标：四路入库统一为单链路。

- 引入 `ImportCenter` + `IngestCommandApi`。
- 抽取单点 `EventDraftFactory`（DTO->MyEvent）。
- 抽取单点 `IngestDedupPolicy`。
- 引入持久化 `IngestFingerprintStore`（防并发重复入库）。

DoD：OCR/SMS/通知监听/ContentObserver 仅一条入库链。

## Wave 5（1 天）SettingsCenter 与实验入口清理（M5）

目标：收尾调用方层直连问题。

- `EdgeBarService` 设置读写完全走 API。
- `PreferenceSettingsPage`、`LaboratoryPage` 去除 UI 层直接数据层访问。
- 收敛设置副作用（天气、提醒重排、侧边栏联动）到 `SettingsCenter`。

DoD：调用方层无 Repository/Database 直连。

## Wave 6（1-2 天）大文件拆分与最终收口（M6）

目标：满足文件规模约束并完成架构闭环。

- 重点拆分：`AppRepository`、`CalendarSyncManagerV2`、`CalendarManager`、`CapsuleStateManager`。
- 删除过渡路径与临时开关。
- 文档与代码最终对齐。

DoD：核心业务文件 `<= 1000` 行；旧入口全部删除。

---

## 6. 统一验收标准（每个 Wave 必须满足）

- 编译通过：`./gradlew :app:compileDebugKotlin`
- 安装通过（有设备时）：`./gradlew :app:installDebug`
- 同语义仅保留一个入口（禁止新旧双轨长期并存）
- 关键冒烟通过：动作、提醒/胶囊、同步、入库、设置

---

## 7. 回滚策略

- 按 Wave 独立提交，任一 Wave 可单独回退。
- 允许短期开关止血，但必须在下一 Wave 内移除。
- 默认优先“结构收口不改语义”；语义优化另起小迭代。

---

## 8. 执行顺序（最终）

`Wave0 -> Wave1 -> Wave2 -> Wave3 -> Wave4 -> Wave5 -> Wave6`

预计周期：8-10 个工作日（单人节奏）。

---

## 9. 进度看板

- [ ] Wave 0 护栏与基线
- [ ] Wave 1 ScheduleCenter 收口
- [ ] Wave 2 Reminder/Capsule 收口
- [ ] Wave 3 SyncCenter 收口
- [ ] Wave 4 ImportCenter 收口
- [ ] Wave 5 Settings/实验入口清理
- [ ] Wave 6 大文件拆分与收尾
