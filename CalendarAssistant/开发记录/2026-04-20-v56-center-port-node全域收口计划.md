# 2026-04-20 v56 Center-Port-Node 全域收口计划

## 1. 文档目的

- 冻结本轮全域重构策略，避免后续执行中方案漂移。
- 明确本轮优先级：先完成既有未收口项，再推进新功能域重构。
- 作为后续实施、验收、回滚的统一对照文档。

> 当前状态：已完成 Store 大体拆分，进入全域架构语义收口阶段。

---

## 2. 会话决策锁定

本轮已确认以下关键决策：

1. `StoreCoordinator` 最终命名为 `StoreRootNode`。
2. 不新建 `TimelineCenter`，升级现有 `ScheduleCenter` 承接 APP 内业务节点化。
3. 统一命名策略：`Store*Support` 全部改为 `Store*Node`。
4. 天气能力独立为 Weather 域（Query/Operation API + Node），由主页/悬浮窗通过 API 调用。
5. 网速胶囊归属 `CapsuleCenter`，仅保留一个采集与计算 Node，并通过 API 向 `CapsuleCenter` 传输结果。
6. 权限能力采用轻量 `PermissionCenter` + 各权限 Node 的模式。
7. 平台事件能力采用独立中心收口（启动、保活、周期任务、系统广播编排）。
8. 备份导入导出从 `ScheduleCenter` 独立为 `BackupCenter`，主写路径直接落 Room。
9. 内容源编排独立为 `ContentIngestCenter`，统一入口后主写路径直接落 Room。

---

## 3. 总体执行原则

- 调用方向统一为：`Caller(UI/Service/Receiver/Worker) -> Center -> Port -> Node`。
- Node 按稳定能力边界拆分，不按页面数量拆分。
- 实施策略采用「先并行接线、后删旧路径」，降低中途断链风险。
- 每阶段独立验收，失败可局部回滚，不影响前序阶段。

---

## 4. 分阶段执行计划

## Phase A（优先）先完成既有未收口项

目标：把已做过的 Store 重构从“形似 Node”收口为“语义和结构都达标”。

### A1. Store Root 收口

- `StoreCoordinator` 重命名为 `StoreRootNode`。
- 文件迁移到 `data/node/store/`。
- `StoreDispatcher` 改为依赖 `StoreRootNodePort`（接口）而不是具体实现。

### A2. Store 子节点命名收口

- `Store*Support` 统一改名为 `Store*Node`。
- 同步改 object/class 名称、引用路径、日志标签、文档术语。

### A3. 边界收口与护栏对齐

- 清理 Store 域中 `Coordinator/Repository` 历史语义残留（仅保留真实 repository）。
- 更新架构护栏，确保 caller 不直连 store node。

### A-DoD

- `StoreRootNode` 命名、目录、依赖方向均符合 center-port-node 约束。
- `:app:compileDebugKotlin` 通过。
- `checkArchitectureGuardrails` 通过。

---

## Phase B Recognition 域收口

目标：统一多入口识别编排，形成 `RecognitionCenter + 多 Node`。

### B1. 新增 RecognitionCenter

- 统一编排 OCR、纯文本模型、多模态、SMS 识别与入库路由。

### B2. Node 划分

- `RecognitionOcrNode`
- `RecognitionTextNode`
- `RecognitionMultimodalNode`
- `RecognitionSmsParseNode`
- `RecognitionIngestNode`

### B2'. ContentIngestCenter 收口（与识别域并行）

- 新增 `ContentIngestCenter` 作为内容源统一编排中心，覆盖 OCR/纯文本/多模态/SMS/手动输入。
- `ContentIngestCenter` 负责跨来源去重、幂等、冲突策略与入库路由。
- 主写路径直接落 Room（通过对应 Port/Node 实现），JSON 仅作兼容性影子策略。

建议 Node：

- `IngestDedupNode`
- `IngestConvertNode`
- `IngestPersistRoomNode`

### B3. 入口改薄

以下入口只保留输入解包与中心调用：

- `TextAccessibilityService`
- `FloatingScheduleService`
- `SmsContentObserver`
- `SmsReceiver`
- `SmsNotificationListenerService`

### B-DoD

- 识别流程不再散落在多个入口类中。
- 相同输入类型走统一编排链路。
- 编译和护栏检查通过。

---

## Phase C Notification 域收口

目标：落地 `NotificationCenter + 2 Node`（必要时加调度 Node）。

### C1. 统一分流中心

- 新增 `NotificationCenter`，成为普通通知与胶囊通知的唯一决策点。

### C2. Node 划分

- `StandardNotificationNode`
- `CapsuleNotificationNode`
- `AlarmPlanNode`（推荐，用于承接调度策略）

### C3. Receiver/Scheduler 改薄

- `AlarmReceiver` 降级为 dumb receiver。
- `NotificationScheduler` 剥离策略分支，仅保留调度能力。

### C-DoD

- 分流决策单点化。
- receiver 中不再保留复杂业务分支。
- 编译和护栏检查通过。

---

## Phase D Floating 域收口

目标：将悬浮窗业务逻辑下沉，Service 只保留 Android 壳层职责。

### D1. 新增 FloatingCenter

- 统一管理浮窗生命周期与功能路由。

### D2. Node 划分

- `FloatingOverlayNode`
- `EdgeBarNode`
- `FloatingIngestNode`

### D-DoD

- `FloatingScheduleService`、`EdgeBarService` 仅保留生命周期/权限/窗口边界处理。
- 业务逻辑迁入 node。

---

## Phase D+ Weather 与网速胶囊收口

目标：将天气与网速胶囊从调用层中剥离为稳定能力接口。

### D+1. Weather 域

- 新增 `WeatherQueryApi`、`WeatherOperationApi`。
- 新增 `WeatherCenter`（可选，若编排较轻可由 API adapter 承接）。
- 新增 Node：`WeatherFetchNode`、`WeatherCacheNode`、`WeatherPolicyNode`。
- `MainViewModel` 与 `FloatingScheduleService` 禁止直接依赖 `WeatherRepository`。

### D+2. 网速胶囊

- 保持归属 `CapsuleCenter`，不额外新建中心。
- 新增单一 Node：`NetworkSpeedProbeNode`（负责采集与计算）。
- 通过 API/Port 将采样结果传给 `CapsuleCenter`，由胶囊中心统一更新展示状态。

### D+-DoD

- 天气与网速胶囊均通过 API 调用，不再由 App 启动代码直接编排。
- `App.kt` 中网速监控逻辑迁出至 center/node 链路。

---

## Phase E 升级 ScheduleCenter 承接 APP 内节点

目标：不新建中心，升级原 `ScheduleCenter` 统一 APP 内业务能力。

### E1. 节点划分（按能力，不按页面）

- `TimelineQueryNode`（主页/全部/归档查询聚合）
- `NoteCommandNode`
- `CourseCommandNode`
- `ArchiveCommandNode`

### E2. ViewModel 改薄

- `MainViewModel`、`SettingsViewModel` 仅做状态聚合与触发调用。

### E-DoD

- ViewModel 不再承载复杂业务编排。
- ScheduleCenter 负责统一编排，Node 承载实现。

---

## Phase E+ Backup 域独立

目标：将备份导入导出从 `ScheduleCenter` 独立，形成独立中心与 Room 主写链路。

### E+1. 新增 BackupCenter

- 新增 `BackupQueryApi`、`BackupOperationApi`。
- 新增 `BackupCenter` 承接备份导入导出流程编排。

### E+2. Node 划分

- `BackupExportNode`
- `BackupImportNode`
- `BackupPersistRoomNode`

### E+-DoD

- `ScheduleCenter` 不再直接承载备份导入导出编排。
- 备份恢复主写路径直接落 Room。

---

## Phase F 全局收尾

- 统一命名与目录语义（去历史术语残留）。
- 统一日志 tag 规范（去 `AppRepository` 等历史 tag）。
- 补齐架构护栏与文档映射。

### F1. 权限域收口

- 新增轻量 `PermissionCenter`（策略集中）。
- 新增权限 Node：`CalendarPermissionNode`、`SmsPermissionNode`、`OverlayPermissionNode`、`NotificationListenerPermissionNode`。
- 各业务中心通过 Permission Port 查询与请求，不直接处理平台分支。

### F2. 平台事件域收口

- 新增 `RuntimeOrchestrationCenter`（启动/保活/周期任务/系统广播编排）。
- `BootReceiver`、`KeepAlive`、周期调度入口仅做转发调用。

### F-DoD

- 全域满足 center-port-node 结构约束。
- 文档与代码结构一致。

---

## 5. 实施顺序（固定）

1. Phase A（未收口优先）
2. Phase B（Recognition + ContentIngest）
3. Phase C（Notification）
4. Phase D（Floating）
5. Phase D+（Weather + Network Capsule）
6. Phase E（ScheduleCenter 升级）
7. Phase E+（BackupCenter 独立）
8. Phase F（Permission + Runtime + 全局收尾）

---

## 5.1 执行策略补充（已锁定）

- 采用“结构收口优先 + 事件化伴随迁移”的混合推进，不做完全串行。
- 具体节奏：
  1. 先完成 Phase A 结构语义收口；
  2. 提前插入事件基建（EventBus/Envelope/最小事件集）；
  3. 在 Recognition/ContentIngest/Notification/Floating 迁移过程中同步事件化；
  4. 最后统一护栏与全局回归。

---

## 5.2 写入一致性硬规则（已锁定）

- 所有写库入口必须统一走 `ScheduleWritePort`（由 `StoreRootNode` 对外写口承接）。
- `schedule.changed` 事件统一在该写口发射，禁止在调用层分散发射。
- 禁止任何 Center 绕过写口直写 Room 主链（可保留只读查询直连 Query Port）。

---

## 6. 每阶段固定验收项

- `./gradlew :app:compileDebugKotlin`
- `./gradlew checkArchitectureGuardrails`
- 对应功能域冒烟验证（按当阶段范围）

---

## 7. 回滚策略

- 每阶段独立提交，保持可单阶段回滚。
- 先接入新链路、后删除旧链路，避免切换窗口期不可用。
- 若阶段验收失败，回滚到该阶段起点，不影响前序阶段。

---

## 8. 功能覆盖清单（本计划边界）

### 8.1 已覆盖功能域（纳入 Phase A-F）

- 日程主链：新增/更新/删除、归档/恢复、课程并入、自动归档。
- 同步主链：正向同步、反向同步、来源日历管理。
- 识别主链：OCR、纯文本、多模态、SMS 多入口入库。
- 通知主链：普通通知、胶囊通知、闹钟路由与调度策略。
- 悬浮窗主链：主浮窗、侧边条、浮窗触发识别与动作。
- APP 内业务：主页/全部/归档查询，便签动作，课表动作。
- 天气与网速胶囊：天气能力 API 化、网速胶囊单 Node 采样并由 CapsuleCenter 编排。
- 备份导入导出：独立 BackupCenter，Room 主写恢复链路。
- 内容源编排：独立 ContentIngestCenter，统一多来源入库。
- 权限与平台事件：PermissionCenter + RuntimeOrchestrationCenter。

### 8.2 当前未单列为独立功能域（暂按现状跟随收口）

- 稳定性与运维：CrashHandler、ANR 监控、日志规范。
- 外观与设置扩展：主题、实验室配置、快捷方式等。
- 规则实验室域：暂不单列新 Center，先保持 `RuleCenter`，后续根据复杂度决定是否拆 `RuleQueryApi/RuleOperationApi`。

### 8.3 结论

- 本计划已覆盖项目核心业务功能链路。
- 对于平台支撑能力，采用“跟随所在主链同步收口”的策略；若后续复杂度提升，再独立拆分 Center/Node。

---

## 9. 当前执行状态（持续更新）

- 已完成：
  - Phase A 主体收口（`StoreRootNode` 语义落地、`Store*Node` 命名迁移、dispatcher 侧写口事件发射）。
  - Phase B 核心链路（`RecognitionCenter` 事件生产、`ContentIngestCenter` Actor 化、Home/Note/Floating/Accessibility 识别入口统一 ingest 主链）。
  - 事件化 v0.1 基建（`DomainEventBus`、envelope、最小事件集）。
  - 提醒/胶囊联动收口（`schedule.changed` 消费编排已下沉到 `ReminderCenter`，并完成防抖/动作分流/全量兜底）。
  - Notification/Floating 第一轮收口（AlarmReceiver 模式判断通过 `ReminderCenter` 统一路由，入口识别失败提示与入库反馈已事件化）。
  - Weather API 化第一轮（`WeatherQueryApi/WeatherOperationApi` + repository adapter 已接入 Main/Floating/Receiver/Settings 页面）。
  - BackupCenter 独立第一轮（`SettingsViewModel` 备份导入导出调用已切到 `BackupCenter`）。
  - Runtime/Permission 收口主链（`RuntimeCenter` 接管 App 启动与 Boot 恢复编排，`PermissionCenter` 已接入 App/Floating/Accessibility/EdgeBar/Settings 页面权限判断）。
  - Network Capsule 收尾（`NetworkSpeedProbeQueryApi + Local adapter + NetworkSpeedProbeNode` 已接入运行时编排，App 侧直接监控逻辑已迁出）。
  - Notification/Floating 最终壳层收口（新增 `NotificationCenter`、`FloatingCenter`，`AlarmReceiver`/入口服务继续瘦身）。
  - Backup 域最终收口（新增 `BackupOperationApi + BackupStorePort + BackupStoreOperationApi`，`BackupCenter` 独立操作 API 并落回 `StoreRootNode` 主写链）。
  - Phase F 文档与护栏封板（全域编译与护栏校验通过）。
- 进行中：
  - 无。
- 待执行：
  - 无（本计划范围内任务已全部完成）。
