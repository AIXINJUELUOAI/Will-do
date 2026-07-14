# CalendarAssistant 最终目录重组映射

## 目标

本轮以“物理目录和代码职责一致”为完成标准，不再把“能够编译”视为目录整理完成。

- `main` 保存共享业务、连接层、Material 渲染和 Android 平台实现。
- `native` / `hyperos` 只保存各自风格的渲染入口。
- 页面、契约、连接器和领域代码优先归入对应 `feature`。
- `platform` 只保存 Android、厂商、系统组件和外部入口。
- `shared` 只保存真正跨功能复用的代码和第三方源码。
- `core` 是待清空的遗留区；迁移期间禁止新增无归属代码。

## 必须保留的 Android / Gradle 标准层级

```text
app/src/
├── main/          # 共享源码、Manifest、资源和 assets
├── native/        # 原生风格 source set
├── hyperos/       # HyperOS 风格 source set
├── test/          # JVM 单元测试
└── androidTest/   # 设备测试
```

`app/src/main/{java,res,assets,jniLibs,AndroidManifest.xml}` 和
`java/com/antgskds/calendarassistant` 属于标准结构，不做扁平化。

空的 `app/src/debug` 与旧 `miui` 目录已经确认无 Git 文件，可直接清理。

## 最终 package 根结构

```text
com/antgskds/calendarassistant/
├── App.kt
├── MainActivity.kt
├── app/
│   └── ui/
│       ├── navigation/
│       ├── prompt/
│       ├── state/                 # Main/Settings ViewModel 的明确遗留区
│       └── theme/
├── feature/
│   ├── appearance/
│   ├── backup/
│   ├── capsule/
│   ├── home/
│   ├── note/
│   ├── quickmemo/
│   ├── recognition/
│   ├── schedule/
│   ├── settings/
│   ├── update/
│   └── weather/
├── platform/
│   ├── accessibility/
│   ├── capsule/
│   ├── clipboard/
│   ├── floating/
│   ├── notification/
│   ├── receiver/
│   ├── tile/
│   ├── widget/
│   └── xposed/
└── shared/
    ├── management/
    ├── ui/
    └── vendor/
```

迁移完成后，不再保留顶级 `ui`、`service`。`core`、`data`、`calendar`、`store`
只有在剩余文件被明确登记为稳定基础设施时才允许继续存在。

## UI 目录约定

每个功能的 UI 使用同一套分层：

```text
feature/<name>/ui/
├── contract/          # UiState、UiAction、展示 DTO
├── connector/         # ViewModel / Android / 领域对象连接
└── render/
    └── material/      # main 中的 Material 实现
```

两个 flavor 镜像相同的功能路径：

```text
app/src/native/java/.../feature/<name>/ui/render/
app/src/hyperos/java/.../feature/<name>/ui/render/
```

主要 flavor host 归属：

| 目标 | 文件组 |
|---|---|
| `app.ui.theme` | `EditionTheme` |
| `app.ui.prompt.render` | `GlobalPromptHost` |
| `feature.home.ui.render` | Home、HomePage、BottomBarEditor |
| `feature.schedule.ui.render` | Schedule、AllEvents、事件/课程弹窗、课表设置、归档 |
| `feature.quickmemo.ui.render` | QuickMemo 列表与详情 |
| `feature.note.ui.render` | Note 列表与编辑器 |
| `feature.weather.ui.render` | Weather 设置与详情 |
| `feature.backup.ui.render` | Backup |
| `feature.update.ui.render` | AppUpdate |
| `feature.appearance.ui.render` | ThemeSettings |
| `feature.recognition.ui.render` | AI、RegexRuleEditor |
| `feature.settings.*.ui.render` | 设置壳、偏好、关于、开发者、实验室 |
| `platform.floating.ui.render` | FloatingSchedule、PickupQr |
| `platform.widget.ui.render` | WidgetSettings、WidgetConfigure |

`shared/ui` 只保存通用交互、布局、基础 Material 组件和 UI 模型；不得放业务页面。

## 领域迁移映射

| 当前目录 | 最终归属 |
|---|---|
| `core/note` | `feature/note/{data,domain}` |
| `core/quickmemo` | `feature/quickmemo/{data,domain,application}` |
| `core/ai`、`core/rule`、识别相关 node | `feature/recognition` |
| `calendar`、`store`、日程相关 `data` | `feature/schedule`，系统入口例外 |
| `data/repository`、配置读写 | `feature/settings/data` |
| `feature/api` | 拆回对应 feature 的 `api` |
| `service/capsule` | `feature/capsule` 与 `platform/capsule` |
| `core/center` | 按 feature/application 逐个替换；禁止新增 Center |
| `materialcolor` | 已迁入 `shared/vendor/materialcolor` |

## 不得直接改 FQCN 的入口

以下类型被 Manifest、系统设置、快捷方式、PendingIntent、WorkManager、Widget Host、
JobScheduler 或 Xposed 按类名引用。整理时保持现有 package，或保留同 FQCN 薄入口：

- `App`、`MainActivity`
- `core.service.shortcut.ShortcutHandleActivity`
- `core.service.image.*Activity` 与 `ImageShareRecognitionWorker`
- `core.service.voice.VoiceCaptureHandleActivity`
- `core.service.pickup.PickupQrHandleActivity`
- `platform.accessibility.TextAccessibilityService`
- `platform.tile.*TileService`
- `platform.floating` 下 Manifest 中声明的 Service
- `platform.clipboard.ClipboardCodeMonitorService`
- `platform.receiver` 下 Manifest 中声明的 Receiver / ListenerService
- `platform.widget` 下 Provider 与 ConfigureActivity
- `calendar.receivers.*`、`calendar.jobs.CalDAVUpdateListener`
- `feature.weather.domain.WeatherSyncWorker`
- `platform.notification.receiver.NotificationAlarmReceiver`
- `platform.xposed.SelfHook`、`MiuiIslandDispatcherHook`

Room 的 Database、Entity、DAO 以及 `MySettings` / 备份 DTO 可以整理物理位置，但改变
package 前必须单独完成覆盖安装、数据库和旧备份兼容验证。

## 迁移批次

1. 重组 `native` / `hyperos` flavor host、app navigation/theme 和架构守卫。
2. 按 feature 移动 UI contract、connector、Material renderer 与共享组件。
3. 拆分 `service/capsule` 到 `feature/capsule` / `platform/capsule`。
4. 迁移 note、quickmemo、recognition、schedule 等领域代码。
5. 处理 `core/center` 和 `calendar/data/store` 最终边界。
6. 删除空目录和无调用文件，补禁止写入旧目录的守卫。
7. 统一运行单测、架构检查、`nativeDebug` 和 `hyperosDebug`。

每个批次中，纯移动与行为修改必须分开提交。

## 完成标准

- `native` / `hyperos` 文件集合与功能路径完全对称。
- package 根不再同时出现多套含义相同的 `service/platform/core.service`、
  `ui/feature` 分类。
- 遗留目录要么清空，要么在本文明确说明保留原因。
- Manifest、Room、WorkManager、Widget、Xposed 和备份兼容检查通过。
- Git 不跟踪任何 `build/` 生成文件。
- 单测、架构守卫与两个 debug APK 构建成功。

## 独立问题

审计发现 `NotificationAlarmReceiver` 被新闹钟链路作为显式广播目标使用，但当前 Manifest
中没有对应声明。该问题需要单独诊断和修复，不与纯目录移动混在同一提交。
