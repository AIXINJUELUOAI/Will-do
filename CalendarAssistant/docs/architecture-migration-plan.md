# CalendarAssistant 目录与双 UI 迁移计划

## 目标

- 保持一个仓库、一套业务和数据代码，输出 `native` 与 `hyperos` 两个 APK。
- `native` 继续使用现有 Material 3 / Compose 界面。
- `hyperos` 逐步使用 HyperOS 风格界面，并按需参考或引入必要组件。
- 整理历史目录时不改变数据库、同步、通知、识别等业务行为。
- 每个迁移批次可以独立编译、提交和回退。

## 构建约定

| Flavor | 日常任务 | 最终验收任务 | UI | applicationId | 数据兼容 |
|---|---|---|---|---|---|
| `native` | `assembleNativeDebug` | `assembleNativeRelease`（签名） | Material 3 | `com.antgskds.calendarassistant` | 与当前版本一致 |
| `hyperos` | `assembleHyperosDebug` | `assembleHyperosRelease`（签名） | 当前复用 Material renderer，后续逐页替换 | `com.antgskds.calendarassistant` | 与 native 互相覆盖安装 |

两个 flavor 必须保持相同签名、`applicationId`、Room schema、Provider authority、通知 key 和 Intent extra。
`BuildConfig.UI_EDITION` 只用于装配不同 UI，不得参与业务或数据分支。
日常开发只构建双 debug；只有最终发布验收才提供本地签名参数并构建双 release。

## 当前结构判断

| 当前目录 | 状态 | 目标 |
|---|---|---|
| `core/center` | 只剩遗留 `*Center.kt` 业务门面 | 调用方逐步改依赖 `operation/query/api` 或 feature 契约；禁止新增 Center |
| `core/operation`、`core/query` | 新契约层 | 继续保留，后续按 feature 归属评估移动 |
| `feature/update` | 更新检查与远端版本模型 | 已按 domain/model 归属，继续保持无 UI 依赖 |
| `feature/backup` | 课程导入解析与模型 | 已归入 `courseimport`，备份主流程后续继续收口 |
| `feature/schedule` | 日程展示纯逻辑与通知桥接 | 已按 domain/notification 分层 |
| `feature/weather` | 天气 API、领域逻辑、缓存与预警边界 | 继续作为天气能力主目录，并消费 `:location` 契约 |
| `ui` | main 中的 contract、connector 与共享 Material renderer | 业务连接留在 main；native/hyperos 只提供对称 flavor host 和各自渲染 |
| 旧 `miui` / 运行时 `UiStyle` | 源码和运行时分支已删除 | 不得恢复；旧备份字段只做反序列化兼容 |
| `platform` | Android、厂商和系统副作用 | 已承接 accessibility、clipboard、floating、notification、receiver、tile、widget、xposed |
| `service` | 胶囊业务和平台发布混合 | 业务编排归 feature，共享模型归 shared，系统发布归 platform |
| `calendar`、`store`、`data` | 日历领域、持久化和模型边界交叉 | 暂不大搬；先明确 API、实现和模型归属 |
| `shared/management` | 注册台账和展示资源 | 保留；所有新增页面、配置、通知类型继续先登记 |
| `shared/vendor/materialcolor` | 已隔离的外部 Material 色彩算法源码 | 只维护必要适配，不混入业务代码 |

## 目标源码集

```text
app/src/main/       业务、数据、平台、共享 UI 契约
app/src/native/     原生风格页面、主题、弹窗和组件实现
app/src/hyperos/    HyperOS 风格页面、主题、弹窗和组件实现
location/            独立 Android 定位模块，不依赖 app 或天气

app/src/main/.../feature/update/     更新 domain/model
app/src/main/.../feature/backup/     备份与课程导入
app/src/main/.../feature/schedule/   日程 domain/notification
app/src/main/.../feature/weather/    天气 api/domain
app/src/main/.../platform/           Android 与 ROM 副作用
app/src/main/.../shared/vendor/materialcolor/  外部色彩算法隔离区
```

定位模块只负责权限状态、设备坐标、精度、时间与来源。城市搜索、行政区名称、天气位置缓存和预警稳定性策略仍属于 weather。

页面采用“main connector + 共享 UiState/UiAction + flavor host + Material renderer”的过渡结构。只有具备稳定契约的页面才进入 flavor；不得把 ViewModel、Center 或 Repository 直接暴露给 flavor。

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

- `native`、`hyperos` product flavor 已建立。
- 两个版本当前可通过对称 host 复用同一套 Material renderer，后续分别替换视觉。
- 包名、签名和覆盖安装属于最终双 release 验收项，不在日常迁移批次重复打包。

### Phase 1：共享 UI 契约

- 为页面建立稳定的 `UiState`、`UiAction` 和回调接口。
- ViewModel 不引用 flavor 页面实现。
- 页面不直接访问 Room、Repository 或具体平台 Publisher。
- 导航目标保持共享，导航外观允许由 flavor 实现。
- flavor host 不得导入具体 ViewModel、`core.center`、Repository 或 Store 实现。
- 关于页作为首个试点：`AboutUiState/AboutUiAction` 和连接层留在 main，`AboutScreen` 分别由 native/hyperos 源码集提供。
- 天气详情页已建立 `WeatherDetailUiState/WeatherDetailUiAction`；数据连接留在 main，native/hyperos 源码集分别提供页面入口，当前共同复用 Material 渲染。
- 软件更新页已建立独立展示 DTO 和 `AppUpdateUiAction`；检查更新与打开链接留在 main，两个 flavor 分别提供页面入口。
- 课程管理页已将课程列表状态和增删改动作收敛到 UI 契约；课程派生与 ViewModel 操作保留在 main。
- 学期设置页已将日期、周数和导航动作收敛到 UI 契约；周次计算与选择器交互保持在 flavor 渲染层。
- 归档页已在 main 完成过滤、排序、分组和展示模型转换；flavor 仅消费分组状态并发送恢复/删除动作。
- 正则规则页已将偏好存储和识别测试保留在 main；flavor 仅编辑规则并展示测试反馈。
- 捐赠页已将捐赠状态写入和二维码相册保存保留在 main；flavor 负责二维码弹窗和礼花动画。
- 配置编辑页已将设置流读取和写回保留在 main；flavor 继续通过共享 ConfigCatalog 动态生成控件。
- 日程色盘页已将色盘读取、清洗与持久化保留在 main；flavor 负责色块和添加颜色弹层交互。
- 底栏编辑页已将配置清洗、自愈和持久化保留在 main；flavor 只消费活动项/候选项并发送保存动作。
- 实验室页已将模型导入、权限判断、服务启停和设置写入保留在 main；flavor 只渲染实验项并发送动作。

### Phase 2：低风险页面试迁移

迁移顺序：

1. 关于页或独立开发者 UI 试验页
2. 天气详情页
3. 普通设置页
4. 随口记列表与详情
5. 日程首页和编辑弹窗
6. 悬浮窗与复杂编辑器

每个页面先抽共享状态，再分别提供 native/hyperos 渲染，不复制业务操作。
当前迁移工作树已覆盖多类设置页以及首页、列表、详情和部分弹窗边界；这些状态描述不等于本轮已经完成统一构建验证。

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

当前目录落点：

- 定位已拆为独立 `:location` 模块。
- 更新检查与远端版本模型位于 `feature/update/domain`、`feature/update/model`。
- 课程导入解析位于 `feature/backup/courseimport`。
- 日程展示逻辑与通知桥接位于 `feature/schedule/domain`、`feature/schedule/notification`。
- 天气契约和领域逻辑位于 `feature/weather/api`、`feature/weather/domain`。
- Android/ROM 系统入口逐步收口在 `platform`。

### Phase 4：清理历史结构

- 旧 `miui` 占位源码与运行时 `UiStyle` 分支已经删除；不得恢复应用内 UI 版本切换。
- Material 色彩算法已迁入 `shared/vendor/materialcolor`。
- `core/center` 已清除非 Center 混放文件，只保留遗留 `*Center.kt`；禁止新增 Center。
- 清理无调用方的 Center、Helper 和重复模型。
- 统一 `calendar/store/data` 边界。
- 更新技术文档、架构守卫和注册台账路径。

## 日常迁移验证

```powershell
gradlew.bat :location:testDebugUnitTest checkArchitectureGuardrails `
  :app:assembleNativeDebug :app:assembleHyperosDebug
```

日常只构建双 debug。最终发布验收才传入本地签名参数并执行：

```powershell
gradlew.bat :location:testDebugUnitTest checkArchitectureGuardrails `
  :app:assembleNativeRelease :app:assembleHyperosRelease `
  -PRELEASE_STORE_FILE=<本地签名文件> `
  -PRELEASE_STORE_PASSWORD=<本地密码> `
  -PRELEASE_KEY_ALIAS=<本地别名> `
  -PRELEASE_KEY_PASSWORD=<本地密码>
```

最终验收同时检查：

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
- 普通迁移批次至少完成双 debug 验证；准备发布时必须额外完成双签名 release 验收。
