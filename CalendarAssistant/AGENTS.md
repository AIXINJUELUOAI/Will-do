# CalendarAssistant（Will do）开发约定

Will do 是 Android 智能信息记录助手，使用 Kotlin + Jetpack Compose，包名 `com.antgskds.calendarassistant`。

## 工作区与事实来源

- 当前仓库根目录是 `F:/CalendarAssistant`，Android 工程仍位于内层 `CalendarAssistant/`。尚未执行目录上移；Gradle 命令在有 `gradlew.bat` 的工程目录执行。
- 版本、SDK、ABI 和 flavor 以 [app/build.gradle.kts](app/build.gradle.kts) 为准，不在此重复维护版本号。
- 构建模块以 [settings.gradle.kts](settings.gradle.kts) 为准，目前包含 `:app`、`:location`；`ai-engine/` 不参与构建。
- 当前仅维护 `native` UI flavor；HyperOS/Flyme 是系统适配，不是独立 UI flavor。
- 功能说明见 [项目首页](../README.md)，架构说明见 [技术文档](技术文档.md)。历史开发记录不代表当前实现。
- 先讨论、确定范围再实施；已获授权的任务继续完成。不要自行扩大为目录重构、打包、装机、提交或推送。

## 核心纪律：先注册，再开发

下表路径相对于 `app/src/main/java/com/antgskds/calendarassistant/`。新增对应能力前，先在清单中登记，并用 `note` 或注释说明用途。

| 新增内容 | 登记位置 |
| --- | --- |
| 功能模块 | `shared/management/catalog/FeatureCatalog.kt` |
| 设置页面 | `shared/management/catalog/PageCatalog.kt` |
| 可调配置、阈值、时长、开关 | `shared/management/catalog/ConfigCatalog.kt` |
| 通知类型 `NotificationKind` | `shared/management/catalog/NotificationKindCatalog.kt` |
| 事件类型 / `EventTags` | `feature/recognition/domain/rule/RecognitionRuleCatalog.kt` |
| 内容源 `ContentSourceType` | `App.kt` 中的 `ContentRegistry.register(...)` |
| 调试动作 | `feature/settings/developer/application/DebugActionRegistry.kt` |
| 流程主线 | `shared/management/catalog/PipelineCatalog.kt` |
| 辅助工具 | `shared/management/catalog/HelperCatalog.kt` |
| 策略 | `shared/management/catalog/PolicyCatalog.kt` |
| 后台任务 | `shared/management/catalog/WorkerCatalog.kt` |

这些是维护台账，不是面向用户的功能说明。用户开关与策略判断应集中在对应 Policy，避免散落在入口中。禁止在业务代码里裸写可调策略常量。

**区分开发约定与自动检查：**

- `checkArchitectureGuardrails` 当前检查旧仓库访问模式、UI contract / native host 的部分依赖、运行时 `UiStyle` 残留、新增 `*Center.kt`、设置页和通知类型漏登记。
- `registry-check` 是开发者调试动作，检查台账字段、调试动作 ID 等，不是 Gradle 任务，也不能证明所有实现都已登记。
- 配置常量、事件类型、内容源及通知模板边界仍须遵守上述约定；当前 Gradle 守卫没有旧文档所写的 `POLICY_CONSTANT_IN_BUSINESS`、`EVENT_TAG_NOT_REGISTERED`、`CONTENT_SOURCE_NOT_REGISTERED`、`TEMPLATE_NO_*` 检查。
- 不通过放宽 baseline 掩盖新违规；以 [build.gradle.kts](build.gradle.kts) 的实际规则为准。

## 分层与统一入口

源码按 `app / feature / platform / shared` 分层：装配 / 业务 / Android 与厂商副作用 / 公共契约与组件。不要恢复旧的 `core/data/calendar/store/ui/service` 顶层目录，不新增 `*Center.kt`。

`App.kt` 中仍存在 `recognitionCenter` 等历史属性名，其对象已使用职责类名；不要因此另造一套入口。部分平台类保留旧 package/FQCN 以兼容系统入口，移动文件不等于可以改类名。

新调用方应依赖契约：

| 能力 | 入口 | 当前实现 |
| --- | --- | --- |
| 识别 | `shared/operation/RecognitionApi.kt` | `RecognitionOrchestrator` |
| 入库 | `shared/operation/IngestCommandApi.kt` | `IngestPipeline`，分别委派日程与账单写入 |
| 系统日历同步 | `shared/operation/SyncApi.kt` | `CalendarSyncService` |
| 通知 | `feature/notification/api/NotificationApi.kt` | `NotificationOrchestrator` |
| 账单存储与查询 | `feature/accounting/domain/AccountingApi.kt` | `AccountingRepository` |

UI、Receiver、Service 不直接写 DAO。同步失败不回滚本地入库。WebDAV 同步属于 `feature/cloudsync`，不能与系统日历 `SyncApi` 混为一条能力。

## 识别与记账约束

- 截图和图片统一走多模态；独立 OCR 链路与旧文本 AI 配置模式已下架。不要恢复 OCR 兜底或为账单另造 AI 路由。
- 直接文字与语音转写仍支持文字识别，按 `RecognitionModePolicy` 选择 AI / 日程正则及回退策略。“下架文本模式”不等于删除文字输入能力。
- 统一 AI JSON 使用 `events` 与 `bills`；取件属于 `events` 分类，不是独立 `pickup` 数组。`AnalysisResult.Success` 携带日程数据、账单候选及实际入库结果。
- 主动识别账单自动入库并显示结果；缺少时间的主动文字记录可用当前时间。详情页与退款必须使用有依据的交易时间，不随意补成现在。
- 无障碍自动记账截图会调用模型；Xposed 支付消息、通知与短信仅本地解析，不调用 AI。
- 通知与短信共用一个默认关闭子开关，只有自动记账开启、通知使用权与 `READ_SMS` / `RECEIVE_SMS` 全部满足才运行。权限申请先通知使用权，再短信；取消保持关闭。原取件短信开关独立。
- 通知与短信只显示结果，不显示识别进度；规则未命中静默跳过。开发者规则编辑与运行时必须使用同一配置。
- 复用现有账单去重。疑似重复暂存并提示，可由用户“仍然计入”；不要按测试金额或商家硬编码。
- 新识别来源通过统一入库和反馈链路，截图附件与账单关联存储。具体测试范围见 [自动记账验证](docs/automatic-accounting-testing.md) 和 [通知与短信规则](docs/accounting-message-rules.md)。

## 通知边界

- 业务层不直接调用 `NotificationManager.notify()` 或构建 `NotificationCompat.Builder`；普通通知走 `NotificationApi → NotificationOrchestrator → platform/notification`。
- 单次、重复、错过补发日程提醒走 `feature/schedule/notification/ScheduleNotificationBridge`。旧 `NotificationScheduler` 仅承担胶囊闹钟等遗留职责，不用于新增普通提醒。
- `CapsuleStateManager` 计算状态，发布交 `platform/capsule/CapsuleDispatcher`；小米超级岛的 Xposed/SystemUI 跨进程传输是平台例外。
- 展示模板 `shared/management/resource/notification/display/` 不引入 NotificationManager、Compat、Builder、PendingIntent、Repository、Room 或业务编排器。

## 数据、权限与日志

- Room 主库为 `events.db`；实体与版本以 `feature/schedule/data/db/EventsDatabase.kt` 为准。升级保留用户数据，显式维护迁移；不能用清库代替迁移修复。
- `MySettings` 的遗留字段可能用于配置迁移，不凭字段名判断其仍有 UI 或可以直接删除。
- 权限不足时保留开关可点击，开启前检查，授权返回后再核实；运行时也要检查撤销。复用已有权限工具，串行双权限按记账专门流程处理。
- 无障碍诊断只在开发者主动启动的会话中采集 UI 树与截图；普通日志不要输出支付正文、金额、联系人或单号。
- 不打印或提交 `local.properties`、签名凭据、API Key、用户支付诊断包。中文文件使用 UTF-8。

## 验证与交付

代码变更先编译，再运行架构守卫与相关测试，使用当前 flavor 的任务名：

```powershell
.\gradlew.bat --no-daemon --console=plain :app:compileNativeDebugKotlin
.\gradlew.bat --no-daemon --console=plain checkArchitectureGuardrails
.\gradlew.bat --no-daemon --console=plain :app:testNativeDebugUnitTest
```

- 测试可按变更范围用 `--tests` 筛选；修改 `:location` 时运行对应模块测试。
- 纯文档改动检查路径、链接及 `git diff --check`，不为此打包 APK。
- 构建以日志 `BUILD SUCCESSFUL` 为准，不依赖后台任务提示。区分编译/单测通过与真机验证通过。
- 当前用户自行打 Release 验证；不要主动执行 `assembleNativeDebug` 或安装应用。需要打包时依用户当次指令选择 variant。
- 当前没有可用测试机。以后获准装机也仅使用 `adb -s 36e06fca`，绝不安装到主力机 `3B162U0051H00000`。
- `NotificationAlarmReceiver` 非导出，获准使用测试机后可通过 root 广播执行 `debug:<id>`；动作定义见 `DebugActionRegistry`，日志看 `WillDoNotify`。
- 不自行提交或推送。保留已有未提交改动，完成后报告实际检查结果及尚未真机验证的范围。
