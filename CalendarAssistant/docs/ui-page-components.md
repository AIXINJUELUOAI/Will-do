# 公共页面组件

关于页、更新页首批接入后，用户已确认底部导航栏表现正常。本批将其余页面的 Material 顶部栏与 Scaffold 入口统一到公共组件，尚待用户构建验收。

## 覆盖范围

| 入口 | 使用方式 |
|---|---|
| 设置外壳 `MaterialSettingsPage` | 全部设置子页共用 `AppTopBar` 与 `AppPageScaffold`，包括课表、作息表、偏好、AI、天气、备份、外观、归档、实验室、开发者工具、底栏编辑、小组件设置、引导、关于及更新 |
| 首页 `MaterialHomePage` | 顶部标题始终居中，原切换/长按菜单保留；列表沿用各自滚动，宽屏布局仍由自适应容器控制 |
| 天气详情 | 独立入口使用骨架与顶部栏；设置内入口复用设置外壳，仅渲染内容 |
| 随口记详情 | 独立和宽屏嵌入入口均使用骨架；嵌入时不放返回按钮，标题仍居中 |
| 便签编辑 | 骨架处理编辑区与键盘避让，`NoteEditorTopBar` 仅组合公共顶部栏和置顶/删除操作 |
| 桌面小组件配置 | 骨架整页滚动，底部保存按钮可滚入安全区 |
| 首次启动引导 | 独立入口使用无顶部栏的骨架；设置内入口由设置外壳承载，步骤标题不重复加状态栏高度 |

应用内浮动工具栏、侧栏与确认卡片保留各自交互布局；Sheet、Dialog、系统悬浮窗暂不迁移。

## 职责

- `AppTopBar`：手机和宽屏均固定标题居中，左右按钮独立可选；长标题省略，不向空的一侧偏移。负责顶部及横向安全区。
- `AppTopBarBackButton`：统一返回图标、48 dp 点击区域和无障碍描述；调用方提供导航及触感行为。
- `AppPageScaffold`：统一普通/壁纸背景、系统栏安全区、键盘避让、内容限宽及居中；支持整页滚动，提供顶部栏、底栏、悬浮按钮、Snackbar 插槽。
- `LocalAppPageBottomPadding`：由骨架提供的内容末尾安全留白，供自行滚动的列表及浮动按钮使用，业务不再直接读取导航栏高度。

页面骨架不会重复绘制主窗口壁纸，也不会增加玻璃采样层。Sheet、Dialog 和系统悬浮窗不属于本组件的迁移范围。

## 接入示例

```kotlin
AppPageScaffold(
    scrollState = rememberScrollState(),
    topBar = {
        AppTopBar(
            title = "页面标题",
            navigationIcon = {
                AppTopBarBackButton(onClick = onBack)
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            },
        )
    },
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp),
    ) {
        // 业务内容
    }
}
```

左右不需要按钮时省略对应插槽。顶部栏也可省略，此时骨架仍会给内容预留顶部系统栏安全区。

## 安全区与迁移规则

- 普通整页滚动使用 `scrollState = rememberScrollState()`，由骨架统一滚动；业务内容只保留排版间距，不再添加 `verticalScroll`，也不要嵌套 `LazyColumn` 等同方向滚动容器。
- 整页滚动且没有固定底栏时，视口延伸到系统导航栏背后，底部安全留白放在滚动内容内部。滚动中卡片不会在导航栏上沿提前截断，滚到底时最后一项仍可完整露出。短内容保留安全区内的最小高度，支持关于页纵向居中。
- 默认不传 `scrollState`、不开启 `edgeToEdgeContent`，`content` 整体位于安全区域，适用于固定布局。业务无需额外处理安全区。
- 自行滚动的列表使用 `edgeToEdgeContent = true`。读取 `LocalAppPageBottomPadding.current`，将其叠加到 `LazyColumn.contentPadding`、`verticalScroll` 后的内部 padding 或末尾 Spacer；浮动按钮叠加同一个值。不要把这份留白加到列表外层，否则会重新出现提前截断。
- 骨架只消费实际处理掉的 insets。列表模式的底部 inset 保留给嵌套详情骨架，避免宽屏内嵌随口记丢失安全区。业务内容不再自行添加 `statusBarsPadding`、`navigationBarsPadding` 或 `imePadding`。
- `bottomBar` 由骨架预留底部和横向安全区，内容不重复添加系统栏间距。FAB、Snackbar 的位置由 Scaffold 按系统栏和底栏高度安排。
- 默认避让键盘，特殊页面可使用 `avoidKeyboard = false`；首页保留原搜索及浮动栏的处理。外观设置不再额外叠加完整键盘高度；随口记底部操作栏使用骨架提供的留白。
- 内容区默认最大宽度 960 dp；小组件配置为 720 dp，随口记详情为 760 dp；首页使用 `Dp.Unspecified`，由原有自适应容器分别限宽。
- 设置页面已有外层路由壳。关于/更新在 `MaterialSettingsPage` 中启用整页滚动，内容函数不再放滚动容器。其余设置子页保留自身滚动、输入状态及浮动操作，只消费公共留白。
- 引导页固定底部操作行已预留安全区；其上方嵌入的设置页面通过局部 `LocalAppPageBottomPadding provides 0.dp` 避免重复留白。
- 返回栈、页面切换动画仍由导航宿主负责，公共组件不自行创建导航控制器或接管系统返回。

## 验收范围

待用户构建装机确认：手机/宽屏、普通/壁纸背景下的标题居中与返回；手势导航/三键导航下长列表到底及浮动按钮；课表/作息表新增与清空；首页视图短按切换及长按菜单；随口记、便签和设置输入框的键盘避让；首次引导与设置内引导；小组件配置保存。

已检查所有修改的 Kotlin 文件及公共组件的语法，并通过 `git diff --check`。源码搜索确认业务页不再直接创建 Material `Scaffold`、`TopAppBar` 或 `CenterAlignedTopAppBar`。

按用户自行构建的约定，本批不运行 Gradle、不装机、不提交。语法检查未解析 Android/Compose 依赖，不能代替编译、架构守卫和上述实机验收。
