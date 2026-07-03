# CalendarAssistant（Will do）开发约定

智能日历助手，Kotlin + Jetpack Compose，包名 `com.antgskds.calendarassistant`。
版本以 `app/build.gradle.kts` 为准（当前 2.1.4 / versionCode 71 / minSdk 33），**不要采信 README 里的版本**。

## 核心纪律：先注册，再开发

本项目用「代码内注册清单 + 架构守卫」来防止越写越乱。**加任何下列东西，必须先在对应清单登记一条（带注释说明），再写实现。** 守卫 `./gradlew.bat checkArchitectureGuardrails` 会强制这些规则，漏登记会编译失败。

| 你要加的东西 | 先登记到 | 守卫规则 |
|---|---|---|
| 新功能模块（新业务能力） | `shared/management/catalog/FeatureCatalog.kt`（`features` 里加 `FeatureEntry`） | —（registry-check 纳入；CLAUDE.md 约定） |
| 新设置页面 | `shared/management/catalog/PageCatalog.kt`（`pages` 里加 `PageEntry`） | `PAGE_NOT_REGISTERED` |
| 可调配置项（阈值/时长/开关） | `shared/management/catalog/ConfigCatalog.kt`（`items` 里加 `ConfigItem`） | `POLICY_CONSTANT_IN_BUSINESS`（禁止裸写策略常量） |
| 新通知类型（NotificationKind） | `shared/management/catalog/NotificationKindCatalog.kt`（`kinds` 里加 `KindEntry`） | `KIND_NOT_REGISTERED` |
| 新事件类型（EventTags 分类） | `core/rule/RecognitionRuleCatalog.kt`（加对应 Rule，`tag = EventTags.X`） | `EVENT_TAG_NOT_REGISTERED` |
| 新内容源（ContentSourceType） | `App.kt` 用 `ContentRegistry.register(...)` 注册 | `CONTENT_SOURCE_NOT_REGISTERED` |
| 调试动作/测试项 | `core/developer/DebugActionRegistry.kt`（`actions` 里加 `DebugAction`） | —（开发者页/ADB 自动消费） |
| 新流程主线（Pipeline/Orchestrator） | `shared/management/catalog/PipelineCatalog.kt`（`pipelines` 里加 `PipelineEntry`） | —（registry-check 纳入；CLAUDE.md 约定） |
| 新辅助工具（Helper/Mapper/Support） | `shared/management/catalog/HelperCatalog.kt`（`helpers` 里加 `HelperEntry`） | —（registry-check 纳入；CLAUDE.md 约定） |
| 新策略（Policy） | `shared/management/catalog/PolicyCatalog.kt`（`policies` 里加 `PolicyEntry`） | —（registry-check 纳入；CLAUDE.md 约定） |
| 新后台任务（Worker） | `shared/management/catalog/WorkerCatalog.kt`（`workers` 里加 `WorkerEntry`） | —（registry-check 纳入；CLAUDE.md 约定） |

这些清单是**代码内台账，不暴露给 App 用户**。它们让维护者打开文件就能一眼看全项目有哪些页面/配置/流程/工具/策略/后台任务/调试动作。登记项务必带一句 `note`/注释说明用途。

## 通知系统（已重构，走新链路）

业务层**不要**直接 `NotificationManager.notify()` 或建 `NotificationCompat.Builder`。
- 普通通知：经 `feature/api/notification`（NotificationApi）→ `NotificationCenter` → `platform/notification` 的 Publisher。
- 日程提醒：单次/重复/错过补发都走 `core/center/ScheduleNotificationBridge` → NotificationApi，已统一，勿走旧 `NotificationScheduler` 排普通提醒（它现在只排胶囊闹钟）。
- 胶囊（实况通知）：`CapsuleStateManager` 只算状态，发布交 `service/capsule/CapsuleDispatcher`（内部分流原生/魅族/小米超级岛）。小米超级岛走 Xposed/SystemUI 跨进程，是底层 transport 例外。
- 通知「展示模板层」(`shared/management/resource/notification/display/`) 禁止 import NotificationManager/Compat/Builder/PendingIntent/Repository/Room/*Center（守卫 `TEMPLATE_NO_*` 强制）。

## 主链路：入口可多，主流程一条（同类能力统一入口）

主流程 `入口 → 识别 → 入库 → 同步 → 通知`。各链路有统一入口契约，新入口/调用方应**依赖契约接口，不要直接拿 Center 实现类**：
- 识别：`core/operation/RecognitionApi`（RecognitionCenter 实现）——所有识别入口（截图/图片/文本/语音）统一走它，输出 `AnalysisResult<RecognitionDraft>`。
- 入库：`core/operation/IngestCommandApi`（ContentIngestCenter/ImportCenter 实现）——识别结果/短信/即时码统一入库。
- 同步：`core/operation/SyncApi`（SyncCenter 实现）——启停/立即同步/选日历/状态查询。同步失败不回滚本地入库。
- 通知：`feature/api/notification/NotificationApi`（见下）。
旧 `*Center` 是过渡实现，可继续作为这些契约的实现体；不要新增 `*Center.kt`。

## 验证纪律

- 每次改动后：`./gradlew.bat :app:compileDebugKotlin` → `checkArchitectureGuardrails` → `:app:assembleDebug`。
- **判断构建成败只看日志里的 `BUILD SUCCESSFUL`**，后台任务的 failed 通知不可靠。
- 装机测试只用测试机 `36e06fca`（已 root），**绝不装主力机 `3B162U0051H00000`**，命令显式 `adb -s 36e06fca`。
- `NotificationAlarmReceiver` 非导出，adb 触发调试动作需 root broadcast：
  `adb -s 36e06fca shell su -c 'am broadcast -n com.antgskds.calendarassistant/.platform.notification.receiver.NotificationAlarmReceiver --es extra_notification_key "debug:<id>"'`，日志看 `-s WillDoNotify`。

## 顶层结构方向

`app`（启动装配）/ `feature`（业务）/ `platform`（系统厂商副作用）/ `shared`（跨业务公共 + 管理清单）。
旧 `core/center/*Center` 是过渡 adapter，**不要新增 `*Center.kt`**。
