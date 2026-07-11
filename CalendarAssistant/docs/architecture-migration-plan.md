# CalendarAssistant 目录与双 UI 迁移计划

## 目标

- 保持一个仓库、一套业务和数据代码，输出 `native` 与 `hyperos` 两个 APK。
- `native` 继续使用现有 Material 3 / Compose 界面。
- `hyperos` 逐步使用 HyperOS 风格界面和必要的 MIUIX 组件。
- 整理历史目录时不改变数据库、同步、通知、识别等业务行为。
- 每个迁移批次可以独立编译、提交和回退。

## 构建约定

| Flavor | 任务 | UI | applicationId | 数据兼容 |
|---|---|---|---|---|
| `native` | `assembleNativeRelease` | Material 3 | `com.antgskds.calendarassistant` | 与当前版本一致 |
| `hyperos` | `assembleHyperosRelease` | 初期复用 Material 3，后续逐页替换 | `com.antgskds.calendarassistant` | 与 native 互相覆盖安装 |

两个 flavor 必须保持相同签名、`applicationId`、Room schema、Provider authority、通知 key 和 Intent extra。
`BuildConfig.UI_EDITION` 只用于装配不同 UI，不得参与业务或数据分支。

## 当前结构判断

| 当前目录 | 状态 | 目标 |
|---|---|---|
| `core/center` | 历史业务门面和过渡实现集中区 | 保留实现，调用方逐步改依赖 `operation/query/api` 契约；禁止新增 Center |
| `core/operation`、`core/query` | 新契约层 | 继续保留，后续按 feature 归属评估移动 |
| `feature` | 新业务结构，目前仅覆盖部分功能 | 作为业务能力的主要归属目录 |
| `ui` | Material 页面、共享状态、导航、动画混合 | 状态与契约留在 main；渲染实现逐步拆到 flavor |
| `miui` | 早期占位实现 | HyperOS flavor 成型后删除或迁入 `src/hyperos` |
| `platform` | Android、厂商和系统副作用 | 保留并继续收口 Widget、通知、悬浮窗、Xposed 等能力 |
| `service` | 胶囊业务和平台发布混合 | 业务编排归 feature，共享模型归 shared，系统发布归 platform |
| `calendar`、`store`、`data` | 日历领域、持久化和模型边界交叉 | 暂不大搬；先明确 API、实现和模型归属 |
| `shared/management` | 注册台账和展示资源 | 保留；所有新增页面、配置、通知类型继续先登记 |
| `materialcolor` | 外部颜色算法源码 | 隔离为 shared/vendor，最后处理 |

## 目标源码集

```text
app/src/main/       业务、数据、平台、共享 UI 契约
app/src/native/     原生风格页面、主题、弹窗和组件实现
app/src/hyperos/    HyperOS 风格页面、主题、弹窗和组件实现
location/            独立 Android 定位模块，不依赖 app 或天气
```

定位模块只负责权限状态、设备坐标、精度、时间与来源。城市搜索、行政区名称、天气位置缓存和预警稳定性策略仍属于 weather。

初期不移动现有 `main/ui`。只有当某个页面具备共享状态和操作契约后，才将其渲染实现拆到 flavor，避免一次性移动 70 多个 UI 文件。

## 禁止首批改包名的入口

以下类型会被 Manifest、系统、闹钟、RemoteViews、Xposed 或外部 Intent 按类名引用，目录整理初期只允许内部重构：

- `App`、`MainActivity`
- Manifest 中的 Activity、Service、Receiver、Provider
- AccessibilityService 与 Quick Settings Tile
- AppWidgetProvider 和配置 Activity
- WorkManager Worker
- `platform.xposed.SelfHook`
- `platform.xposed.MiuiIslandDispatcherHook`
- Room Database、Entity、DAO 和迁移类
- FileProvider authority、通知 key、PendingIntent request code 和 Intent extra

## 迁移批次

### Phase 0：双 flavor 空壳

- 建立 `native`、`hyperos` product flavor。
- 两个版本继续编译同一套现有 UI。
- 验证两个 release APK 使用相同包名和签名，能够互相覆盖安装。

### Phase 1：共享 UI 契约

- 为页面建立稳定的 `UiState`、`UiAction` 和回调接口。
- ViewModel 不引用 flavor 页面实现。
- 页面不直接访问 Room、Repository 或具体平台 Publisher。
- 导航目标保持共享，导航外观允许由 flavor 实现。
- 关于页作为首个试点：`AboutUiState/AboutUiAction` 和连接层留在 main，`AboutScreen` 分别由 native/hyperos 源码集提供。
- 天气详情页已建立 `WeatherDetailUiState/WeatherDetailUiAction`；数据连接留在 main，native/hyperos 源码集分别提供页面入口，当前共同复用 Material 渲染。
- 软件更新页已建立独立展示 DTO 和 `AppUpdateUiAction`；检查更新与打开链接留在 main，两个 flavor 分别提供页面入口。

### Phase 2：低风险页面试迁移

迁移顺序：

1. 关于页或独立开发者 UI 试验页
2. 天气详情页
3. 普通设置页
4. 随口记列表与详情
5. 日程首页和编辑弹窗
6. 悬浮窗与复杂编辑器

每个页面先抽共享状态，再分别提供 native/hyperos 渲染，不复制业务操作。

### Phase 3：按领域整理 main

建议顺序：

1. `weather`
2. `update`、`backup`
3. `quickmemo`
4. `note`
5. `recognition`
6. `schedule`、`calendar`
7. `notification`、`capsule`
8. `floating`、`xposed` 等平台入口

一次只迁移一个领域。纯移动和行为修改必须拆成不同提交。

### Phase 4：清理历史结构

- 删除已被 flavor 实现替代的旧 `miui` 占位代码。
- 清理无调用方的 Center、Helper 和重复模型。
- 统一 `calendar/store/data` 边界。
- 更新技术文档、架构守卫和注册台账路径。

## 每批验证

```powershell
gradlew.bat checkArchitectureGuardrails
gradlew.bat :app:assembleNativeRelease
gradlew.bat :app:assembleHyperosRelease
```

同时检查：

- 两个 APK 的 `applicationId`、`versionCode` 和签名一致。
- native APK 不包含 HyperOS 专用依赖。
- Room schema 和备份格式未变化。
- Manifest 与 Xposed 入口类仍存在。
- R8 没有新增 missing class 或反射裁剪问题。
- 两个版本可以互相覆盖安装且数据保留。

## 提交纪律

- 每个迁移批次开始前创建稳定提交。
- 文件移动使用独立提交，避免与大段逻辑修改混合。
- 用户 Bug 修复优先落到共享业务层，再验证两个 flavor。
- HyperOS 专用修复不得改变 native 行为。
- 未同时通过两个 release 构建的迁移不得进入主分支。
