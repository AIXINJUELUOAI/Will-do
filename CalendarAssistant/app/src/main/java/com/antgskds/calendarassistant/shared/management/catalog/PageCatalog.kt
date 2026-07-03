package com.antgskds.calendarassistant.shared.management.catalog

import com.antgskds.calendarassistant.ui.components.SettingsDestination

/**
 * 页面登记清单（代码内台账，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「设置页面」的唯一总台账。打开本文件即可一眼看全：有哪些页面、各是干嘛的、
 * 给谁看、对应路由。让不直接读代码的维护者也能管住「项目里到底有哪些页面、谁加的」。
 *
 * ## 怎么登记（agent 加新页面必须做）
 * 加一个新设置页时，**必须先在下面 [pages] 里登记一条 [PageEntry]**（带说明注释），
 * 然后才去 SettingsDetailScreen 接渲染。架构守卫 PAGE_NOT_REGISTERED 会强制这条规则：
 * SettingsDestination 枚举里出现、但这里没登记的页面，会让 `checkArchitectureGuardrails` 失败。
 *
 * ## 边界
 * - 本清单只登记「元信息」（名字/路由/标题/可见级别/说明），不持有页面渲染逻辑。
 * - 渲染仍在 SettingsDetailScreen（各页参数不同，暂不强行统一）。本台账只保证「先登记」。
 * - 不要把本清单做成给 App 用户看的 UI——它是代码内的管理台账。
 */
object PageCatalog {

    /** 页面可见级别：决定这页给谁、从哪进。 */
    enum class PageVisibility {
        /** 普通用户可见（侧边栏/偏好页入口）。 */
        USER,
        /** 开发者/实验室专属（需开启开发者选项）。 */
        DEVELOPER,
        /** 操作类，不是真正的导航页（如退出、主题切换）。 */
        ACTION,
    }

    /**
     * 一个页面的登记项。
     * @param destination 页面的导航枚举值（SettingsDestination）。
     * @param route 内部路由名（与 SettingsDetailScreen 的 SettingsRoutes 对应；ACTION 类可为 null）。
     * @param title 页面标题（与 settingsTitle 对应）。
     * @param visibility 可见级别。
     * @param note 一句话说明这页是干嘛的（给维护者看）。
     */
    data class PageEntry(
        val destination: SettingsDestination,
        val route: String?,
        val title: String,
        val visibility: PageVisibility,
        val note: String,
    )

    val pages: List<PageEntry> = listOf(
        // —— 课表相关 ——
        PageEntry(SettingsDestination.CourseManage, "settings_course_manage", "课程管理", PageVisibility.USER, "管理课程表里的课程条目"),
        PageEntry(SettingsDestination.TimeTableManage, "settings_timetable_manage", "作息表管理", PageVisibility.USER, "编辑上课作息时间段"),
        PageEntry(SettingsDestination.SemesterConfig, "settings_semester_config", "学期配置", PageVisibility.USER, "设置学期开始日期、总周数"),
        PageEntry(SettingsDestination.Schedule, "settings_schedule", "课表设置", PageVisibility.USER, "综合课表设置（旧版，建议用上面细分项）"),

        // —— 其他设置（普通用户）——
        PageEntry(SettingsDestination.AI, "settings_ai", "模型配置", PageVisibility.USER, "配置 AI 识别模型（API、多模态等）"),
        PageEntry(SettingsDestination.Weather, "settings_weather", "天气", PageVisibility.USER, "天气位置与预警设置"),
        PageEntry(SettingsDestination.Preference, "settings_preference", "偏好设置", PageVisibility.USER, "通用偏好：显示、操作、通知、日程等开关"),
        PageEntry(SettingsDestination.ScheduleColors, "settings_schedule_colors", "日程颜色", PageVisibility.USER, "自定义新建和识别日程使用的颜色色盘（从偏好设置进入）"),
        PageEntry(SettingsDestination.Archives, "settings_archives", "归档", PageVisibility.USER, "查看与恢复已归档的过期日程"),
        PageEntry(SettingsDestination.Backup, "settings_backup", "数据备份", PageVisibility.USER, "导入导出备份"),
        PageEntry(SettingsDestination.AppUpdate, "settings_app_update", "软件更新", PageVisibility.USER, "检查与下载新版本"),
        PageEntry(SettingsDestination.BottomBarEditor, "settings_bottom_bar_editor", "底栏编辑", PageVisibility.USER, "自定义首页底栏入口（从偏好设置进入）"),
        PageEntry(SettingsDestination.WidgetSettings, "settings_widget_settings", "桌面小组件", PageVisibility.USER, "桌面小组件外观设置（从偏好设置进入）"),

        // —— 操作类（不是导航页，直接执行）——
        PageEntry(SettingsDestination.Theme, "settings_theme", "主题设置", PageVisibility.ACTION, "切换深浅色/主题色"),
        PageEntry(SettingsDestination.About, "settings_about", "关于应用", PageVisibility.ACTION, "版本与关于信息"),
        PageEntry(SettingsDestination.Donate, "settings_donate", "捐赠开发者", PageVisibility.ACTION, "捐赠入口"),
        PageEntry(SettingsDestination.Logout, null, "退出应用", PageVisibility.ACTION, "退出登录/应用（不导航）"),

        // —— 实验室 / 开发者 ——
        PageEntry(SettingsDestination.Laboratory, "settings_laboratory", "实验室", PageVisibility.USER, "实验性功能开关与开发者选项入口"),
        PageEntry(SettingsDestination.Developer, "settings_developer", "开发者", PageVisibility.DEVELOPER, "测试中心：列表排序开关 + DebugActionRegistry 调试动作（从实验室进入）"),
        PageEntry(SettingsDestination.ConfigEditor, "settings_config_editor", "配置编辑", PageVisibility.DEVELOPER, "由 ConfigCatalog 驱动的底层配置编辑（从开发者页进入）"),
        PageEntry(SettingsDestination.RegexRuleEditor, "settings_regex_rule_editor", "正则规则", PageVisibility.DEVELOPER, "编辑本地正则日程识别规则并测试匹配结果"),
    )

    /** 按枚举值查登记项。 */
    fun find(destination: SettingsDestination): PageEntry? =
        pages.firstOrNull { it.destination == destination }

    /** 按可见级别筛选。 */
    fun byVisibility(visibility: PageVisibility): List<PageEntry> =
        pages.filter { it.visibility == visibility }
}
