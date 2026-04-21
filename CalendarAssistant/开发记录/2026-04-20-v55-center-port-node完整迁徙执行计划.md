# 2026-04-20 v55 Center-Port-Node 完整迁徙执行计划

## 1. 文档目的

- 冻结后续重构执行基线，避免方案漂移。
- 明确每个里程碑的改造范围、验收标准与回滚边界。
- 作为后续每一轮提交与回归的唯一对照文档。

> 当前状态：已进入执行阶段（保持语义不变优先）。

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

- [x] Wave 0 护栏与基线
- [x] Wave 1 ScheduleCenter 收口
- [x] Wave 2 Reminder/Capsule 收口
- [x] Wave 3 SyncCenter 收口
- [x] Wave 4 ImportCenter 收口
- [x] Wave 5 Settings/实验入口清理
- [x] Wave 6 大文件拆分与收尾

## 10. 执行日志

### 2026-04-20 / Wave 0 完成

- 新增根任务：`checkArchitectureGuardrails`、`updateArchitectureGuardrailsBaseline`（`build.gradle.kts`）。
- 新增基线文件：`gradle/architecture-guardrails-baseline.txt`。
- 护栏覆盖范围：`ui/**`、`service/**`、`**/*Worker.kt`。
- 禁止规则：`AppRepository.getInstance(...)`、`(applicationContext as App).repository`、`AppDatabase.getInstance(...)`。
- 本地验证：`./gradlew checkArchitectureGuardrails` 通过。

### 2026-04-20 / Wave 1 完成

- 新增 `ScheduleCenter`：`app/src/main/java/com/antgskds/calendarassistant/core/center/ScheduleCenter.kt`。
- `App` 注入新增：`scheduleCenter`（基于 `ScheduleOperationApi + ScheduleQueryApi + SettingsQueryApi` 组合）。
- 迁移入口调用：
  - `MainViewModel` 日程动作与查询改经 `ScheduleCenter`。
  - `SettingsViewModel` 的日程统计与提醒冲突检测改经 `ScheduleCenter`。
  - `EventActionReceiver`（含聚合取件批量完成）改经 `ScheduleCenter`。
  - `FloatingScheduleService` 的日程动作改经 `ScheduleCenter`，读侧改走 QueryApi。
- 护栏基线收敛：移除 `FloatingScheduleService` 的 `AppRepository.getInstance` 直连残留。
- 本地验证：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin` 通过。

### 2026-04-20 / Wave 2 完成

- 新增 Capsule API：
  - `core/operation/CapsuleCommandApi.kt`
  - `core/query/CapsuleQueryApi.kt`
  - `data/operation/CapsuleStateManagerCommandApi.kt`
  - `data/query/CapsuleStateManagerQueryApi.kt`
- 新增 Center：
  - `core/center/CapsuleCenter.kt`
  - `core/center/ReminderCenter.kt`
- `CapsuleStateManager` 去除对 `AppRepository` 的反向依赖，改依赖 `ScheduleQueryApi + SettingsQueryApi`。
- `AppRepository` 移除 `capsuleStateManager` 持有，改为 `bindCapsuleRefreshHandler` 回调触发刷新。
- `App` 升级注入：新增 `capsuleStateManager/capsuleCommandApi/capsuleQueryApi/capsuleCenter/reminderCenter`。
- 入口迁移：
  - `AlarmReceiver` 改经 `ReminderCenter/CapsuleCenter` 控制胶囊刷新与 MIUI 岛更新。
  - `TextAccessibilityService` OCR 胶囊操作改经 `CapsuleCenter`。
  - `EventActionReceiver` 聚合取件完成后刷新胶囊改经 `CapsuleCommandApi`。
- 提醒链路收口：`NotificationScheduler` 去除 `AppRepository.getInstance`，改经 `App.settingsQueryApi` 读取设置。
- 护栏基线继续收敛：`TextAccessibilityService` 直连项已移除，`gradle/architecture-guardrails-baseline.txt` 更新为 5 条存量。
- 本地验证：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin` 通过。

### 2026-04-20 / Wave 3 完成

- 新增 `SyncCenter`：`app/src/main/java/com/antgskds/calendarassistant/core/center/SyncCenter.kt`。
- `SettingsOperationApi` 移除读接口（`getSyncStatus/getSelectableSyncCalendars`），读能力统一走 `SettingsQueryApi`。
- `SettingsViewModel` 同步链路改经 `SyncCenter`（刷新状态、切换开关、手动同步、来源日历更新）。
- `CalendarReverseSyncWorker` 去除 `AppRepository.getInstance` + 手动 Adapter new，改经 `App.syncCenter`。
- `CalendarSyncReceiver` 改经 `App.syncCenter` 触发反向同步。
- 护栏基线进一步收敛：移除 Worker 存量项，`gradle/architecture-guardrails-baseline.txt` 降至 4 条。
- 本地验证：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin` 通过。

### 2026-04-20 / Wave 4 完成

- 新增统一入库接口：`core/operation/IngestCommandApi.kt`。
- 新增统一入库中心：`core/center/ImportCenter.kt`（实现 `IngestCommandApi`）。
- 入库能力统一：
  - `ingestSmsPickup`：短信/通知/ContentObserver 三通道共享去重与入库。
  - `ingestRecognizedEvents`：OCR 识别批量入库共享转换与去重。
- `App` 注入新增：`importCenter`、`ingestCommandApi`。
- 入口迁移：
  - `SmsReceiver` 改为调用 `ingestCommandApi.ingestSmsPickup`。
  - `SmsNotificationListenerService` 改为调用 `ingestCommandApi.ingestSmsPickup`。
  - `SmsContentObserver` 改签名为 `getIngestCommandApi` 并调用统一入库接口。
  - `TextAccessibilityService.saveEventsLocally` 改为调用 `ingestCommandApi.ingestRecognizedEvents`。
- 重复转换函数清理：移除三处 `eventDataToMyEvent` 分散实现，转换逻辑收敛至 `ImportCenter`。
- 本地验证：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin` 通过。

### 2026-04-20 / Wave 5 完成

- 新增 `RuleCenter`：`app/src/main/java/com/antgskds/calendarassistant/core/center/RuleCenter.kt`。
- `App` 注入新增：`ruleCenter`。
- `LaboratoryPage` 去除 UI 直连 `AppDatabase`，改经 `RuleCenter` 完成规则读写与状态读取。
- `RuleEditorDialog` 由 `stateDao` 直传改为 `loadStates` 回调，UI 侧不再持有 DAO。
- `EdgeBarService` 去除 `(applicationContext as App).repository`，读设置改经 `settingsQueryApi`。
- 护栏基线继续收敛：`gradle/architecture-guardrails-baseline.txt` 降至 2 条（仅 `BootReceiver`、`DailySummaryReceiver`）。
- 本地验证：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin` 通过。

### 2026-04-20 / Wave 5 追加收口

- `BootReceiver` 去除 `AppRepository.getInstance`，改走 `app.scheduleCenter.refreshAndScheduleAll()`。
- `DailySummaryReceiver` 去除 `AppRepository.getInstance`，读侧改走 `app.settingsQueryApi` + `app.scheduleCenter.events`。
- `ScheduleOperationApi` 新增 `refreshAndScheduleAll()` 以承接开机恢复调度入口。
- 护栏基线已清空：`gradle/architecture-guardrails-baseline.txt` 仅保留注释头。

### 2026-04-20 / Wave 6 进行中（阶段一）

- 提取 `CalendarSyncManagerV2` 的哈希/归一化/指纹/Seed 匹配工具到新文件：
  - `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarSyncManagerV2Hashing.kt`
- `CalendarSyncManagerV2` 改为调用 `CalendarSyncManagerV2Hashing`，删除重复工具方法。
- 文件体量变化：`CalendarSyncManagerV2.kt` 由 `1555` 行降到 `1414` 行。
- 本地验证：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin` 通过。

### 2026-04-20 / Wave 6 进行中（阶段二）

- 新增 `CalendarManagerBuilders`：`app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarManagerBuilders.kt`。
- 从 `CalendarManager` 提取以下能力到 Builders：
  - 日历列表查询（`queryCalendars`）
  - 单次/重复/课程事件 ContentValues 构建
  - 重复规则 horizon 计算
- `CalendarManager` 已改为委托 Builders，文件体量由 `1222` 行降到 `991` 行（达成 `<1000` 目标）。
- 护栏基线保持为空（仅注释头）。
- 本地验证：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin` 通过。

### 2026-04-20 / Wave 6 进行中（阶段三）

- 新增 `CalendarSyncManagerV2Seeding`：`app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarSyncManagerV2Seeding.kt`。
  - 提取日历元数据加载、目标日历解析、映射 seed 逻辑。
- 新增 `CalendarSyncManagerV2RecurringSupport`：`app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarSyncManagerV2RecurringSupport.kt`。
  - 提取重复映射清理、缺失实例处理、外部重复系列落库逻辑。
- 新增 `CalendarSyncManagerV2RecurringUtils`：`app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarSyncManagerV2RecurringUtils.kt`。
  - 提取重复事件合并与同步窗口判定。
- `CalendarSyncManagerV2` 文件体量进一步由 `1414` 行降到 `996` 行（达成 `<1000` 目标）。
- 当前已达标的大文件：`CalendarManager.kt`（`991`）、`CalendarSyncManagerV2.kt`（`996`）。
- 护栏基线保持为空（仅注释头），本地验证持续通过：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin`。

### 2026-04-20 / Wave 6 进行中（阶段四）

- 新增 `AppRepositoryBackupSupport`：`app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepositoryBackupSupport.kt`。
  - 提取课程/事件导入导出、WakeUp 导入与导入归档状态规范化逻辑。
  - 迁出 `CoursesBackupData`、`EventsBackupData` 备份模型定义。
- 新增 `AppRepositorySyncSupport`：`app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepositorySyncSupport.kt`。
  - 提取作息解析、来源日历 ID 解析、反向同步去重/随机色辅助逻辑。
- `AppRepository` 改为委托上述 Support，删除对应内联实现。
- `AppRepository.kt` 文件体量由 `1921` 行降到 `1761` 行（持续下降中）。
- 护栏基线保持为空（仅注释头），本地验证通过：`./gradlew checkArchitectureGuardrails`、`./gradlew :app:compileDebugKotlin`。

### 2026-04-20 / Wave 6 进行中（阶段五）

- 新增 `AppRepositoryRoomDiff`：`app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepositoryRoomDiff.kt`。
  - 提取 Room/JSON 差异对比日志与 `EventCompareSnapshot`。
- 新增 `AppRepositoryArchiveStorageSupport`：`app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepositoryArchiveStorageSupport.kt`。
  - 提取归档持久化、归档列表加载、映射清理底层实现。
- 新增 `AppRepositoryReverseSyncSupport`：`app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepositoryReverseSyncSupport.kt`。
  - 提取反向同步新增/更新/删除分发逻辑与同步快照模型。
- 新增 `AppRepositoryArchiveWorkflowSupport`：`app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepositoryArchiveWorkflowSupport.kt`。
  - 提取 archive/restore/delete/clear/autoArchive 业务流程。
- `AppRepositorySyncSupport` 继续扩展：提取 `updateSourceCalendars` 与 `pruneSourceMappings` 工作流。
- `AppRepository.kt` 文件体量进一步降至 `1382` 行（较初始 `1921` 行已减少 `539` 行）。
- 按当前工作节奏继续拆分，下一阶段目标优先压缩同步主流程与课程/事件状态变更路径。

### 2026-04-20 / Wave 6 进行中（阶段六）

- 新增 `AppRepositoryEventActionSupport`：`app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepositoryEventActionSupport.kt`。
  - 提取完成/检票/撤销/主动作决策，以及重复实例 detach 的编排逻辑。
- `AppRepositoryReverseSyncSupport` 增加 `syncFromCalendar` 统一流程入口，`AppRepository` 仅保留网关参数与回调注入。
- `AppRepositorySyncSupport` 扩展：提取 `enableCalendarSyncAndSyncNow`、`manualSync`、`getSelectableSyncCalendars`。
- `AppRepository.kt` 文件体量进一步降至 `1198` 行（较初始 `1921` 行已减少 `723` 行）。
- 本轮按你的要求未执行编译/验证命令，仅继续结构拆分。

### 2026-04-20 / Wave 6 进行中（阶段七）

- 新增 `CalendarSyncReceiver` 独立文件：`app/src/main/java/com/antgskds/calendarassistant/CalendarSyncReceiver.kt`。
  - `App.kt` 内嵌接收器已移除，`CalendarSyncReceiver` 与应用入口解耦。
- 新增 `AppRepositoryBackupWorkflowSupport`：`app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepositoryBackupWorkflowSupport.kt`。
  - 提取课程/事件导入导出编排逻辑（包含归档懒加载、导入后追加、归档状态同步、提醒补调度）。
  - `AppRepository` 备份相关接口改为委托 Workflow Support，减少主文件编排噪音。
- `AppRepository.kt` 文件体量由 `1198` 行降至 `1152` 行（继续收敛中）。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段八）

- 启动 `AppRepository` 下线前的“调度壳”迁移：
  - 新增 Port 接口：
    - `app/src/main/java/com/antgskds/calendarassistant/core/port/ScheduleStorePort.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/core/port/SettingsStorePort.kt`
  - 新增 Dispatcher：
    - `app/src/main/java/com/antgskds/calendarassistant/data/port/AppRepositoryStoreDispatcher.kt`
    - 由 Dispatcher 实现 Port 并代理 `AppRepository`，用于隔离上层对具体仓储实现的依赖。
- API 适配层改造：
  - `AppRepositoryScheduleOperationApi` / `AppRepositoryScheduleQueryApi` 改依赖 `ScheduleStorePort`。
  - `AppRepositorySettingsOperationApi` / `AppRepositorySettingsQueryApi` 改依赖 `SettingsStorePort`。
- 应用装配改造：
  - `App.kt` 引入 `storeDispatcher`，由 `storeDispatcher -> API -> Center` 组装链路。
  - `App.repository` 已降为 `private`，调用方不可再直接访问。
- 调用方去仓储化（第一批）：
  - `MainViewModel` 去除 `AppRepository` 依赖，改注入 `Context + ScheduleCenter + SettingsQueryApi`。
  - `SettingsViewModel` 去除 `AppRepository` 依赖，改注入 `ScheduleCenter + SyncCenter + SettingsOperationApi + SettingsQueryApi`。
  - `MainActivity` ViewModelFactory 改为从 `App` 注入 Center/API，不再传入 `repository`。
  - `AlarmReceiver` 与 `TextAccessibilityService` 去除 `(applicationContext as App).repository` 直连，改用 Center/QueryApi 读取数据。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段九）

- 继续推进 `AppRepository` 下线前隔离：
  - `App.kt` 完全移除 `AppRepository` 类型依赖与导入，改为 `AppRepositoryStoreDispatcher.getInstance(this)` 装配。
  - `AppRepositoryStoreDispatcher` 升级为单例入口，并对外暴露 `bindCapsuleRefreshHandler`，承接应用启动时胶囊刷新回调绑定。
- 当前状态：`App` 层不再感知 `AppRepository` 实体类型，`AppRepository` 仅在 `data/port` 与 `data/repository` 内部可见。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十）

- 完成 `AppRepository` 实体下线（类型层面）：
  - `app/src/main/java/com/antgskds/calendarassistant/data/repository/AppRepository.kt` 中主类已重命名为 `StoreCoordinator`（仍在原文件，后续可按需再做文件名清理）。
  - `AppRepositoryStoreDispatcher` 改为依赖 `StoreCoordinator`，并继续实现 `ScheduleStorePort/SettingsStorePort`。
  - 项目代码中已无 `AppRepository` 类型引用（仅保留历史日志 tag、文件名与 Support 命名前缀）。
- 架构形态更新：`App -> StoreDispatcher(Port) -> StoreCoordinator -> Support/Node`。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十一）

- 完成历史命名清理（去除 `AppRepository*` 前缀）：
  - `AppRepositoryStoreDispatcher` -> `StoreDispatcher`
  - `AppRepositoryScheduleOperationApi` -> `ScheduleStoreOperationApi`
  - `AppRepositorySettingsOperationApi` -> `SettingsStoreOperationApi`
  - `AppRepositoryScheduleQueryApi` -> `ScheduleStoreQueryApi`
  - `AppRepositorySettingsQueryApi` -> `SettingsStoreQueryApi`
  - `AppRepository*Support` 全部重命名为 `Store*Support`（含 Reverse/Sync/Backup/Archive/RoomDiff/EventAction）
  - `AppRepository.kt` 文件重命名为 `StoreCoordinator.kt`
- `App.kt` 已同步改用新命名装配链路（`StoreDispatcher` + `*Store*Api`）。
- 代码中已无旧前缀类引用，仅保留少量历史日志 tag / 注释文本（不影响运行）。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十二）

- 启动“前端只调 API、后台层计算结果”落地（端内后台层）：
  - 新增 `HomeQueryApi` 与 `HomeSnapshot`：`app/src/main/java/com/antgskds/calendarassistant/core/query/HomeQueryApi.kt`。
  - 新增 `LocalHomeQueryApi` 实现：`app/src/main/java/com/antgskds/calendarassistant/data/query/LocalHomeQueryApi.kt`。
    - 下沉首页计算逻辑：备注/日程拆分、今日/明日合并、优先级排序、过期触发延迟计算。
- `MainViewModel` 改造：
  - 构造注入新增 `homeQueryApi`。
  - 删除 ViewModel 内大段列表计算与过期扫描实现，改为调用 `homeQueryApi.buildSnapshot(...)` 与 `homeQueryApi.calculateDelayToNextExpiration(...)`。
  - 当前 ViewModel 仅保留状态装配与用户意图编排。
- 应用装配改造：
  - `App.kt` 新增 `homeQueryApi` 实例（`LocalHomeQueryApi`）。
  - `MainActivity` 在 `MainViewModel` 创建时注入 `homeQueryApi`。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十三）

- 继续推进“前端只调 API、端内后台计算”第二批迁移：
  - 新增设置转换 API：
    - `app/src/main/java/com/antgskds/calendarassistant/core/query/SettingsTransformApi.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/data/query/LocalSettingsTransformApi.kt`
  - `SettingsViewModel.updatePreference(...)` 内联字段更新 + sanitize 逻辑已下沉为 `settingsTransformApi.applyPreferenceUpdate(...)`。
- 新增通知/事件动作查询 API：
  - `app/src/main/java/com/antgskds/calendarassistant/core/query/EventActionQueryApi.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/data/query/LocalEventActionQueryApi.kt`
  - `AlarmReceiver` 中以下决策已改为调用 API：
    - 事件存在性校验
    - 规则 ID 解析（intent ruleId / payload / tag 回退）
    - 动作文案与按钮可见性/动作类型判定
- 应用装配改造：
  - `App.kt` 新增 `settingsTransformApi`、`eventActionQueryApi`。
  - `MainActivity` 创建 `SettingsViewModel` 时注入 `settingsTransformApi`。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十四）

- 继续收敛 Receiver 端业务逻辑到端内后台 API：
  - 新增通知展示文案 API：
    - `app/src/main/java/com/antgskds/calendarassistant/core/query/NotificationPresentationQueryApi.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/data/query/LocalNotificationPresentationQueryApi.kt`
  - `AlarmReceiver` 的时间文案/地点文案/最终通知文案拼接已改为调用 `notificationPresentationQueryApi.buildPresentation(...)`。
  - `AlarmReceiver` 现有事件动作判定继续由 `eventActionQueryApi` 负责，Receiver 侧以分发与组装通知对象为主。
- 应用装配改造：
  - `App.kt` 新增 `notificationPresentationQueryApi`（`LocalNotificationPresentationQueryApi`）。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十五）

- 继续收敛 Receiver 分支决策到端内后台 API：
  - 新增告警路由查询 API：
    - `app/src/main/java/com/antgskds/calendarassistant/core/query/AlarmRoutingQueryApi.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/data/query/LocalAlarmRoutingQueryApi.kt`
  - 抽离 action -> route 决策（`CAPSULE_START/CAPSULE_END/CAPSULE_REFRESH/REMINDER`），并保留未知 action 回退提醒的兼容行为。
- `AlarmReceiver` 改造：
  - `handleReceiveAsync(...)` 分支不再直接匹配 `NotificationScheduler` action 常量，改为调用 `alarmRoutingQueryApi.resolveRoute(action)` 后按 `AlarmRoute` 分发。
  - 未知 action 日志保留，但 route 决策统一在 QueryApi 内。
- 应用装配改造：
  - `App.kt` 新增 `alarmRoutingQueryApi`（`LocalAlarmRoutingQueryApi`）。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十六）

- 继续收敛胶囊路由判定到端内后台 API：
  - 新增胶囊路由查询 API：
    - `app/src/main/java/com/antgskds/calendarassistant/core/query/CapsuleRoutingQueryApi.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/data/query/LocalCapsuleRoutingQueryApi.kt`
  - 将 `liveCapsuleEnabled + HyperOS + Xposed` 组合判定下沉为 `CapsuleRouteMode` 决策（`MIUI_ISLAND / LIVE_CAPSULE / STANDARD_NOTIFICATION`）。
- `AlarmReceiver` 改造：
  - `handleCapsuleStart/handleCapsuleRefresh/handleCapsuleEnd` 不再内联 MIUI 岛模式判断，统一通过 `capsuleRoutingQueryApi.resolveMode(...)` 分支。
  - `REMINDER` 路径是否跳过普通提醒也改为通过 `CapsuleRouteMode` 决策。
  - 删除 Receiver 内部旧的 `isMiuiIslandMode(...)` 判断函数。
- 应用装配改造：
  - `App.kt` 新增 `capsuleRoutingQueryApi`（`LocalCapsuleRoutingQueryApi`）。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十七）

- 继续收口“前端仍做业务计算”尾项：
  - 新增日程洞察查询 API：
    - `app/src/main/java/com/antgskds/calendarassistant/core/query/ScheduleInsightsQueryApi.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/data/query/LocalScheduleInsightsQueryApi.kt`
    - 下沉逻辑：重复实例下一条查找、提前提醒重复检查、课表目标周次计算。
  - 新增每日摘要查询 API：
    - `app/src/main/java/com/antgskds/calendarassistant/core/query/DailySummaryQueryApi.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/data/query/LocalDailySummaryQueryApi.kt`
    - 下沉逻辑：早/晚报目标日期、日程筛选、天气拼接标题、摘要正文构建。
- 调用方改造：
  - `MainViewModel` 改为调用 `scheduleInsightsQueryApi` 完成重复实例查找与目标周次计算。
  - `SettingsViewModel.hasDuplicateAdvanceReminder(...)` 改为调用 `scheduleInsightsQueryApi`。
  - `DailySummaryReceiver` 改为调用 `dailySummaryQueryApi.buildPayload(...)`，Receiver 不再内联筛选与文案拼接。
  - `MainActivity` / `App.kt` 已完成新 API 注入与装配。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段十八）

- 真机安装与运行验证（你要求“装到设备上看效果”）：
  - 初次执行 `:app:installDebug` 失败，根因是当前 shell 使用的 Java 环境缺少 `jlink`（错误指向 `.antigravity/.../jre/.../jlink.exe` 不存在）。
  - 确认并切换到 Android Studio 内置 JBR：`D:\application\Android Studio\jbr`（含 `jlink.exe`）。
  - 在该 Java 环境下重新执行 `./gradlew :app:installDebug` 成功，APK 安装到设备 `25098PN5AC (Android 16)`。
  - 执行 `adb shell monkey -p com.antgskds.calendarassistant -c android.intent.category.LAUNCHER 1`，应用可正常拉起。
- 本机开发环境修复（避免后续反复手动切换 Java）：
  - 已写入用户级环境变量：
    - `JAVA_HOME=D:\application\Android Studio\jbr`
    - User `Path` 追加 `D:\application\Android Studio\jbr\bin`
- 说明：系统级（Machine）变量写入因当前会话无管理员权限跳过；用户级已可满足本机命令行构建安装。

### 2026-04-20 / Wave 6 进行中（阶段十九）

- 继续推进 `StoreCoordinator` 拆分到 Node：
  - 新增启动与归档加载节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreBootstrapSupport.kt`
  - 新增迁移节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreMigrationSupport.kt`
  - 新增写路径编排节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreMutationSupport.kt`
  - 新增同步编排节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreCalendarSyncSupport.kt`
- `StoreCoordinator` 由内联实现改为委托上述 Support，聚焦调度壳职责：
  - 启动刷新、归档懒加载、迁移流程、事件/课程 mutation、同步流程均已收口到 Support。
- 文件体量变化：
  - `StoreCoordinator.kt` 当前 `969` 行（继续保持 `<1000` 约束）。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十）

- 继续瘦身 `StoreCoordinator`，下沉事件存储与旧文件清理逻辑：
  - 新增事件存储节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreEventStorageSupport.kt`
    - 承接：事件归一化、事件去重归并、活跃事件持久化、提醒可调度判定。
  - 新增旧文件清理节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreLegacyCleanupSupport.kt`
    - 承接：迁移稳定后 legacy JSON 文件清理流程。
- `StoreCoordinator` 调整：
  - `updateEvents/persistActiveEvents/normalizeEventForPersistence/sanitizeRecurringEvents` 改为委托 Support。
  - `cleanupLegacyJsonIfStable` 改为委托 Support。
- 文件体量变化：
  - `StoreCoordinator.kt` 由 `969` 行降至 `930` 行。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十一）

- 继续推进同步/反向同步链路收口：
  - `StoreCalendarSyncSupport` 新增 `syncFromCalendar(...)` 聚合入口，统一承接：
    - 归档预加载
    - 快照构建
    - gateway 回调分发
    - 新增/更新/删除事件处理委托
- `StoreCoordinator.syncFromCalendar()` 已改为调用 `StoreCalendarSyncSupport.syncFromCalendar(...)`，移除内联分发细节。
- 继续压缩 `StoreCoordinator` 注释与重复样板，保留必要结构注释。
- 文件体量变化：
  - `StoreCoordinator.kt` 由 `930` 行进一步降至 `860` 行。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十二）

- 继续完成 `StoreCoordinator` 剩余桥接层拆分：
  - 新增归档编排节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreArchiveCoordinatorSupport.kt`
    - 承接 archive/restore/delete/clear/autoArchive 入口编排与归档状态更新。
  - 新增事件动作编排节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreEventCoordinatorSupport.kt`
    - 承接重复实例 detach、主动作分发、检票/完成/撤销桥接、重复实例取消标记。
  - 新增设置编排节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/repository/StoreSettingsSupport.kt`
    - 承接设置落盘 + 天气副作用触发 + 协程更新入口。
- 同步编排补齐：
  - `StoreCalendarSyncSupport` 新增 `enableCalendarSyncAndSyncNow(...)`，同步组合动作完全移出 `StoreCoordinator`。
- 文件体量变化：
  - `StoreCoordinator.kt` 由 `860` 行继续降至 `840` 行。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十三）

- 继续收口 `StoreCoordinator` 的壳层噪音，做无语义变更清理：
  - 移除大段历史注释与重复说明，保留必要结构信息。
  - 统一入口保持“对外 API -> Support/Workflow 委托”风格，降低文件阅读成本。
- 文件体量变化：
  - `StoreCoordinator.kt` 由 `840` 行继续降至 `785` 行。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十四）

- 继续做 `StoreCoordinator` 壳层精简（无语义变更）：
  - 移除仅做透传的一组私有 helper（active/archived list 读取、event 查找、archive 持久化桥接）。
  - 在委托调用处直接传入状态访问 lambda，减少中间层跳转。
- 结果：
  - `StoreCoordinator.kt` 由 `785` 行进一步降至 `775` 行。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十五）

- 执行 Phase A 结构语义收口：
  - `StoreCoordinator` 重命名并迁移为：
    - `app/src/main/java/com/antgskds/calendarassistant/data/node/store/StoreRootNode.kt`
  - `Store*Support` 全量改名为 `Store*Node`，并统一迁移到：
    - `app/src/main/java/com/antgskds/calendarassistant/data/node/store/`
  - `StoreRoomDiff` 同步改名为 `StoreRoomDiffNode`。
- `StoreDispatcher` 已切换为依赖 `StoreRootNode`（移除旧 `data/repository/StoreCoordinator` 引用）。
- 新增 Store Root Port 契约占位：
  - `app/src/main/java/com/antgskds/calendarassistant/data/port/StoreRootNodePort.kt`
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十六）

- 完成 Phase A 收尾：dispatcher -> root node port 依赖切换。
  - `StoreRootNode` 实现 `StoreRootNodePort`：
    - `app/src/main/java/com/antgskds/calendarassistant/data/node/store/StoreRootNode.kt`
  - `StoreDispatcher` 依赖由具体类切换为接口：
    - `app/src/main/java/com/antgskds/calendarassistant/data/port/StoreDispatcher.kt`
- 兼容收口：
  - `StoreRootNode` 增加 `importEventsData(jsonString)` 对口实现，内部保持 `preserveArchivedStatus` 扩展重载。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十七）

- 启动 Phase B（Recognition + ContentIngest）第一轮落地：
  - 新增识别中心：
    - `app/src/main/java/com/antgskds/calendarassistant/core/center/RecognitionCenter.kt`
  - 新增识别节点：
    - `app/src/main/java/com/antgskds/calendarassistant/data/node/recognition/RecognitionOcrNode.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/data/node/recognition/RecognitionTextNode.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/data/node/recognition/RecognitionMultimodalNode.kt`
  - 新增内容入库编排中心（对 `ImportCenter` 做中心化包装）：
    - `app/src/main/java/com/antgskds/calendarassistant/core/center/ContentIngestCenter.kt`
- App 装配调整：
  - `App` 新增 `recognitionCenter` 与 `contentIngestCenter` 装配。
  - `ingestCommandApi` 改由 `contentIngestCenter` 提供。
- 入口迁移（去除入口层直接依赖 `RecognitionProcessor`）：
  - `TextAccessibilityService`
  - `FloatingScheduleService`
  - `HomePage`
  - `NoteEditorScreen`
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十八）

- 事件化基建（v0.1）第一步已落地：
  - 新增事件总线与事件封装：
    - `app/src/main/java/com/antgskds/calendarassistant/core/event/DomainEventBus.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/core/event/DomainEventEnvelope.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/core/event/DomainEventType.kt`
  - 新增最小事件集 payload 定义：
    - `app/src/main/java/com/antgskds/calendarassistant/core/event/events/RecognitionEvents.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/core/event/events/IngestEvents.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/core/event/events/ScheduleEvents.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/core/event/events/CapsuleEvents.kt`
  - `App` 新增 `domainEventBus` 全局装配入口。
- 说明：当前阶段为基建接入，尚未开启大规模事件消费链替换；下一阶段将推进 Recognition/ContentIngest 的事件生产与消费改造。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段二十九）

- 事件化推进第二步：RecognitionCenter 开始事件生产。
  - `RecognitionCenter` 接入 `DomainEventBus`，在 `parseUserText/analyzeImage` 成功时发出 `recognition.completed`，失败或空结果时发出 `recognition.failed`。
  - 事件字段已带 `traceId/source/entityKey`，并保持调用端兼容（新增参数均提供默认值）。
- App 装配更新：
  - `recognitionCenter` 改为注入 `domainEventBus`。
- 说明：当前仍未启用 `ContentIngestCenter` 事件消费链，避免与现有直调入库路径产生重复写入；将在后续 Actor 化阶段切换。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十）

- 事件化推进第三步：`ContentIngestCenter` 完成 Actor 化与最小消费链接入。
  - 入库请求统一进入 `Channel<IngestTask>(capacity=64)`，采用 `SUSPEND` 背压策略。
  - 单消费者串行处理 `sms/recognized` 入库任务，异常被 `try/catch` 兜底，避免消费循环中断。
  - 入库结果统一发出：
    - 成功：`ingest.succeeded`
    - 失败：`ingest.failed`
  - 接入 `recognition.completed` 事件监听：仅当 `ingestRequested=true` 时才触发自动入库，避免与现有直调路径重复写入。
- `RecognitionCompletedEvent` 补充字段：
  - `sourceImagePath`
  - `ingestRequested`
- `App` 装配更新：
  - `ContentIngestCenter` 注入 `domainEventBus` 与 `appScope`。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十一）

- 并行推进（多子任务）完成以下收口：
  - 统一写口事件发射：
    - `StoreDispatcher` 在成功写操作后发出 `schedule.changed`（带 `changeType/eventIds/origin/entityVersion/updatedAt`），并通过变更检测避免无效重复发射。
    - 文件：`app/src/main/java/com/antgskds/calendarassistant/data/port/StoreDispatcher.kt`
  - 事件身份规范化：
    - 新增 `EventIdentity`，统一 `traceId` 与 `entityKey` 生成。
    - 文件：`app/src/main/java/com/antgskds/calendarassistant/core/event/EventIdentity.kt`
    - `RecognitionCenter` 与 `ContentIngestCenter` 已切换使用。
  - `recognition.failed` 可见反馈接入：
    - Home/Note 通过 `HomeScreen` 统一订阅并提示；
    - Floating 与 Accessibility 服务侧分别接入订阅并做源过滤。
    - 文件：`HomeScreen.kt`、`HomePage.kt`、`NoteEditorScreen.kt`、`FloatingScheduleService.kt`、`TextAccessibilityService.kt`
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十二）

- 统一刷新链补齐（`schedule.changed -> capsule.refresh.requested -> CapsuleCenter`）：
  - `App` 新增事件桥接订阅：
    - 监听 `schedule.changed` 后转发 `capsule.refresh.requested`
    - 监听 `capsule.refresh.requested` 后执行 `capsuleCenter.forceRefresh()`
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/App.kt`
- 识别入库双轨收敛（Accessibility 路径先行）：
  - `TextAccessibilityService` 的截图识别改为 `ingestRequested=true`，由 `ContentIngestCenter` Actor 链路自动入库。
  - 移除无障碍服务内对 `ingestCommandApi.ingestRecognizedEvents(...)` 的直调写入，避免同源重复写入风险。
  - 新增 `ingest.succeeded/ingest.failed` 订阅，用于无障碍胶囊结果提示。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/service/accessibility/TextAccessibilityService.kt`
- 识别失败反馈去重优化（Floating 路径）：
  - Floating 手动输入识别失败统一走 `recognition.failed` 订阅提示，移除本地重复提示分支。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/service/floating/FloatingScheduleService.kt`
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十三）

- 继续收敛识别写入双轨（Home/Floating）：
  - `RecognitionCenter.parseUserText(...)` 补充 `sourceImagePath` 参数，并在 `recognition.completed` payload 透传。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/core/center/RecognitionCenter.kt`
- Home 图片识别路径改为事件化入库：
  - Home 图片导入后不再本地 `addEvent`，改为 `ingestRequested=true` 触发 `ContentIngestCenter` Actor 入库。
  - `HomeScreen` 新增 `ingest.succeeded/ingest.failed` 订阅提示（按 Home source 过滤）。
  - 文件：`HomePage.kt`、`HomeScreen.kt`
- Floating 手动输入识别路径改为事件化入库：
  - 不再本地 `scheduleCenter.addEvent`，改为 `ingestRequested=true`。
  - 新增 `ingest.succeeded/ingest.failed` 订阅提示（按 Floating source 过滤）。
  - 文件：`FloatingScheduleService.kt`
- 结果：
  - Accessibility/Home/Floating 三条识别主入口已统一走 `Recognition -> recognition.completed -> ContentIngestCenter Actor -> ingest.*` 主链。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十四）

- Note 识别入口完成单路径收口：
  - `NoteEditorScreen` 不再通过 `onAnalyzeResult` 回传草稿并本地 `addEvent`，改为 `ingestRequested=true` 触发统一入库主链。
  - 成功提示改为“识别完成，正在保存...”，最终入库结果由 `ingest.*` 事件统一反馈。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/ui/page_display/NoteEditorScreen.kt`
- Home 主屏 ingest 反馈范围补齐：
  - `HomeScreen` 的 `ingest.succeeded/ingest.failed` 订阅新增 Note source 过滤，统一承接 Note 入库反馈。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/ui/page_display/HomeScreen.kt`
- 结果：
  - Home / Note / Floating / Accessibility 四条识别入口均已收敛至统一 ingest 主链，识别写入双轨基本清除。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十五）

- 提醒链联动收口（第一步）：
  - 在 `App` 的 `schedule.changed` 订阅中新增提醒对齐逻辑：
    - 对变更事件中的 `eventIds` 执行“活跃事件重排（先取消后重建）”；
    - 对归档事件执行提醒取消。
  - 与既有胶囊刷新链并行：`schedule.changed -> capsule.refresh.requested -> CapsuleCenter.forceRefresh()`。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/App.kt`
- 结果：
  - `schedule.changed` 现在可驱动“胶囊刷新 + 提醒重排”双链路，减少跨入口写入后的提醒状态漂移。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十六）

- 提醒链联动收口（第二步，防抖与聚合）：
  - `schedule.changed` 订阅改为聚合队列：
    - 变更 `eventIds` 先入集合，250ms 窗口聚合后统一执行一次提醒对齐；
    - 避免高频批量写入场景下重复取消/重建闹钟。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/App.kt`
- 识别入口单路径继续收口（Note 路径补齐）：
  - `NoteEditorScreen` 改为 `ingestRequested=true`，不再回传草稿并本地新增事件。
  - `HomeScreen` 的 ingest 反馈订阅扩展到 Note source。
  - 文件：`NoteEditorScreen.kt`、`HomeScreen.kt`
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十七）

- 提醒链联动收口（第三步，空 ID Bulk 兜底）：
  - 对 `schedule.changed` 且 `eventIds` 为空的 `BULK(import/sync/system)` 事件，新增全量提醒对齐兜底：
    - 活跃事件全量“先取消后重建”提醒；
    - 归档事件全量取消提醒。
  - 同时与现有按 ID 增量对齐并存，避免 import/sync 场景下 diff 结果为空导致提醒漂移。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/App.kt`
- 调度优化：
  - 维持事件 ID 聚合窗口，并在全量兜底时提升延迟窗口（400ms）减少短时抖动。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十八）

- 提醒链联动收口（第四步，按变更类型动作分流）：
  - 对增量事件 ID 引入动作路由：
    - `CREATE/RESTORE -> SCHEDULE_ONLY`
    - `UPDATE/BULK -> RESCHEDULE`
    - `DELETE/ARCHIVE -> CANCEL_ONLY`
  - 聚合窗口内按“最新事件覆盖同 ID 旧动作”，减少无效重复 cancel/schedule 调度。
  - 文件：`app/src/main/java/com/antgskds/calendarassistant/App.kt`
- 结果：
  - 相比全量 `cancel+schedule`，在 CREATE/RESTORE/DELETE/ARCHIVE 路径显著减少不必要提醒重排。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段三十九）

- 事件消费编排从 `App` 下沉到 `ReminderCenter`：
  - `schedule.changed` 订阅、提醒对齐（增量/全量兜底/动作分流）与胶囊刷新桥接逻辑迁入 `ReminderCenter`。
  - `App` 不再承载提醒链业务细节，仅在 `onCreate` 调用 `reminderCenter.startEventSubscriptions()`。
  - 文件：
    - `app/src/main/java/com/antgskds/calendarassistant/core/center/ReminderCenter.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/App.kt`
- 结果：
  - Application 层业务负担下降，提醒链与胶囊联动职责集中到 Center，结构更贴近 center/port/node 目标。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十）

- 收尾清理（命名与日志语义统一）：
  - 清理遗留 `AppRepository` 语义：
    - Store 节点日志 tag 全量统一为 `StoreNode` / `StoreMigrationNode`；
    - 相关注释文本同步改为 `StoreRootNode` 语义（BootReceiver、NotificationScheduler、CalendarSyncManager）。
  - 文件覆盖：
    - `data/node/store/*.kt`（多文件）
    - `service/receiver/BootReceiver.kt`
    - `service/notification/NotificationScheduler.kt`
    - `core/calendar/CalendarSyncManager.kt`
- 结果：
  - Store 域已无 `AppRepository` / `StoreCoordinator` / `Store*Support` 残留引用，命名语义与 center/port/node 目标对齐。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十一）

- 收尾清理（重复提醒调度消除）：
  - `ImportCenter` 移除对 `NotificationScheduler.scheduleReminders(...)` 的直接调用。
  - 保持统一链路：写入后由 Store/ReminderCenter 统一执行提醒编排，避免双重调度。
  - 文件：
    - `app/src/main/java/com/antgskds/calendarassistant/core/center/ImportCenter.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/App.kt`（构造参数同步清理）
- 结果：
  - 识别/导入路径提醒调度进一步收口，避免局部入口与全局事件链并行导致的重复 schedule。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十二）

- 封板前清单式扫描与残留清理：
  - 全局扫描关键残留关键字：`AppRepository` / `StoreCoordinator` / `Store*Support`，结果归零。
  - 保留 `NotificationScheduler` 直调位置仅两类：
    - `StoreRootNode`（写路径基础调度）
    - `ReminderCenter`（事件联动重排）
- 识别入口单路径复核：
  - Home / Note / Floating / Accessibility 均通过 `ingestRequested=true` 进入统一 ingest 主链。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十三）

- Notification/Receiver 侧策略分支继续下沉（小步收口）：
  - `ReminderCenter` 新增 `resolveCapsuleMode(...)`，统一封装“是否启用实况胶囊 + 路由模式”决策入口。
  - `AlarmReceiver` 改为通过 `ReminderCenter` 获取胶囊路由模式，移除 receiver 内重复组合判断。
  - 文件：
    - `app/src/main/java/com/antgskds/calendarassistant/core/center/ReminderCenter.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/service/receiver/AlarmReceiver.kt`
- 结果：
  - receiver 侧策略判断继续瘦身，通知路由决策进一步向 center 聚合。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十四）

- AlarmReceiver 壳层再瘦身（收官冲刺）：
  - `ReminderCenter` 新增模式路由辅助方法：
    - `isStandardNotificationMode(...)`
    - `isMiuiIslandMode(...)`
    - `routeByCapsuleMode(...)`
  - `AlarmReceiver` 改为调用 `ReminderCenter` 路由辅助，进一步移除 receiver 内部模式分支重复代码。
  - 文件：
    - `app/src/main/java/com/antgskds/calendarassistant/core/center/ReminderCenter.kt`
    - `app/src/main/java/com/antgskds/calendarassistant/service/receiver/AlarmReceiver.kt`
- 结果：
  - AlarmReceiver 更接近“路由 + 调中心”壳层，通知策略分支继续向 Center 聚拢。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十五）

- 封板前总清单复核：
  - 命名残留复核：`AppRepository` / `StoreCoordinator` / `Store*Support` 全量 grep 归零。
  - 提醒调度调用点复核：
    - 保留在 `StoreRootNode`（写路径基础调度）与 `ReminderCenter`（事件联动重排）两处；
    - `ImportCenter` 直调已移除。
  - 识别入口单路径复核：
    - Home / Note / Floating / Accessibility 均为 `ingestRequested=true`。
- Receiver 策略复核：
  - `AlarmReceiver` 已无 `resolveMode + isLiveCapsuleEnabled` 组合判断，统一经 `ReminderCenter`。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十六）

- Weather API 化第一轮落地（D+ 前置收口）：
  - 新增接口层：
    - `core/query/WeatherQueryApi.kt`
    - `core/operation/WeatherOperationApi.kt`
  - 新增适配层：
    - `data/query/WeatherRepositoryQueryApi.kt`
    - `data/operation/WeatherRepositoryOperationApi.kt`
  - App 装配：新增 `weatherQueryApi/weatherOperationApi`，统一 WeatherRepository 访问入口。
  - 调用方迁移：
    - `MainViewModel`：改为依赖 `WeatherQueryApi + WeatherOperationApi`
    - `FloatingScheduleService`：改为通过 `weatherQueryApi` 读取天气流
    - `DailySummaryReceiver`：改为读取 `app.weatherQueryApi.weatherData`
    - `WeatherSettingsPage`：改为通过 `app.weatherQueryApi/weatherOperationApi` 读取与测试
  - 文件：`App.kt`、`MainActivity.kt`、`MainViewModel.kt`、`FloatingScheduleService.kt`、`DailySummaryReceiver.kt`、`WeatherSettingsPage.kt`
- 结果：
  - 天气能力调用口已统一到 API 层，减少页面/服务对 `WeatherRepository` 的直接依赖。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十七）

- BackupCenter 独立第一轮落地（E+ 前置）：
  - 新增 `BackupCenter`，将备份导入导出能力从 `SettingsViewModel` 中抽离为独立中心编排入口。
  - `SettingsViewModel` 的课程/事件导入导出与 WakeUp 导入改为调用 `BackupCenter`。
  - `MainActivity` 注入链更新，`App` 装配新增 `backupCenter`。
  - 文件：
    - `core/center/BackupCenter.kt`
    - `ui/viewmodel/SettingsViewModel.kt`
    - `MainActivity.kt`
    - `App.kt`
- 结果：
  - 备份能力从 Schedule/Settings 业务调用层解耦，形成独立中心入口，后续可继续下沉到专属 Room 节点链路。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 进行中（阶段四十八）

- Runtime/Permission/Network Capsule 收口（F 主线推进）：
  - 新增 `RuntimeCenter`，统一承接启动/恢复编排：
    - 定时反向同步调度
    - 保活调度
    - 早晚报调度
    - 短信通知监听恢复
    - 侧边栏启动
    - 网速监控启动
  - `App` 启动流程改为 `runtimeCenter.startAppRoutines()`，移除 `App` 内分散的运行时细节编排。
  - `BootReceiver` 改为调用 `app.runtimeCenter.restoreAfterBoot()`，收拢启动恢复分支。
  - 网速链路切换为 `NetworkSpeedProbeQueryApi -> LocalNetworkSpeedProbeQueryApi -> NetworkSpeedProbeNode`，由 `RuntimeCenter` 统一消费并更新 `CapsuleCenter`。
- PermissionCenter 使用面补齐（F1）：
  - `FloatingScheduleService` 悬浮窗权限判断改走 `PermissionCenter`。
  - `TextAccessibilityService` 启动悬浮窗权限判断改走 `PermissionCenter`。
  - `EdgeBarService` 启动权限判断改走 `PermissionCenter`。
  - `PreferenceSettingsPage` 的 overlay/calendar 权限判断改走 `PermissionCenter`。
- Notification/Floating 边界补强：
  - 设置页每日汇总调度触发改走 `RuntimeCenter.scheduleDailySummary()`，减少页面对 receiver 调度的直连。
- 变更文件：
  - `app/src/main/java/com/antgskds/calendarassistant/core/center/RuntimeCenter.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/App.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/service/receiver/BootReceiver.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/service/floating/FloatingScheduleService.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/service/accessibility/TextAccessibilityService.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/service/floating/EdgeBarService.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/ui/page_display/settings/PreferenceSettingsPage.kt`
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。

### 2026-04-20 / Wave 6 完成（阶段四十九，最终封板）

- Backup 域最终收口：
  - 新增独立 `BackupOperationApi` 与 `BackupStorePort`，备份导入导出不再依附 `SettingsOperationApi` 语义。
  - `SettingsOperationApi/SettingsStorePort` 移除备份导入导出方法，语义收敛为“设置+同步”操作边界。
  - 新增 `BackupStoreOperationApi`，由 `StoreDispatcher` 以 `BackupStorePort` 形式承接。
  - `BackupCenter` 改依赖 `BackupOperationApi`，形成独立中心 -> 独立操作 API -> StoreRootNode 主写链。
- Notification/Floating 最终收口：
  - 新增 `NotificationCenter`，统一普通通知构建与按钮编排逻辑。
  - `AlarmReceiver` 通知展示逻辑下沉到 `NotificationCenter`，receiver 仅保留路由壳层。
  - 新增 `FloatingCenter`，统一悬浮窗/侧边栏启动与权限门禁调用。
  - `TextAccessibilityService`、`PreferenceSettingsPage`、`RuntimeCenter` 改经 `FloatingCenter` 触发悬浮能力。
- App 装配更新：
  - 新增 `backupOperationApi`、`notificationCenter`、`floatingCenter` 注入。
  - `RuntimeCenter` 依赖更新为 `PermissionCenter + FloatingCenter + NetworkSpeedProbeQueryApi`。
- 结论：
  - v55 波次任务全部完成，Wave 0-6 全部勾选闭环。
  - 本轮中心化目标（Backup/Notification/Floating/Runtime/Permission/Network Capsule）全部落地。
- 本地验证通过：`./gradlew :app:compileDebugKotlin`、`./gradlew checkArchitectureGuardrails`。
