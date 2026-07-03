package com.antgskds.calendarassistant.shared.management.resource.notification.display.normal

data class NormalNotificationContent(
    val title: String,
    val contentText: String,
    val bigText: String = contentText
)

object RecognitionNormalDisplay {
    private const val RESULT_TEXT_MAX_CHARS = 56

    fun success(eventTitle: String, contentLines: List<String>, fallbackContent: String): NormalNotificationContent {
        val content = contentLines.joinToString("\n").ifBlank { fallbackContent }
        return NormalNotificationContent(
            title = compactTitle("识别成功", eventTitle),
            contentText = contentLines.firstOrNull() ?: content,
            bigText = content
        )
    }

    fun failure(reason: String, suggestion: String): NormalNotificationContent {
        return NormalNotificationContent(
            title = compactTitle("识别失败", reason),
            contentText = suggestion,
            bigText = suggestion
        )
    }

    fun quickMemoSuggestion(draftTitle: String, contentLines: List<String>): NormalNotificationContent {
        val content = contentLines.joinToString("\n").ifBlank { fallbackDetail() }
        return NormalNotificationContent(
            title = compactTitle("识别日程", draftTitle.ifBlank { "未命名日程" }),
            contentText = contentLines.firstOrNull() ?: content,
            bigText = content
        )
    }

    fun createdEventsSummary(count: Int): NormalNotificationContent {
        return NormalNotificationContent(
            title = "识别成功",
            contentText = "已识别 $count 个日程"
        )
    }

    fun quickMemoSuggestionsSummary(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "识别到日程",
            contentText = "随口记中发现可创建的日程"
        )
    }

    fun completedNoNewEvents(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "识别完成",
            contentText = "没有可入库的新事件"
        )
    }

    fun saveFailed(message: String): NormalNotificationContent {
        return NormalNotificationContent(
            title = "保存失败",
            contentText = message.ifBlank { "入库失败" }
        )
    }

    fun analyzing(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "正在分析",
            contentText = "正在分析屏幕内容..."
        )
    }

    fun screenshotFailed(content: String): NormalNotificationContent {
        return NormalNotificationContent(
            title = "截图失败",
            contentText = content
        )
    }

    fun screenshotProcessFailed(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "截图处理失败",
            contentText = "请重试"
        )
    }

    fun configMissing(content: String): NormalNotificationContent {
        return NormalNotificationContent(
            title = "配置缺失",
            contentText = content
        )
    }

    fun analysisCompletedNoValidSchedule(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "分析完成",
            contentText = "未识别到有效日程"
        )
    }

    fun analysisError(message: String?): NormalNotificationContent {
        return NormalNotificationContent(
            title = "分析出错",
            contentText = "错误: ${message.orEmpty()}"
        )
    }

    fun resultLines(time: String, description: String?, location: String?): List<String> {
        return listOfNotNull(cleanText(time), cleanText(description) ?: cleanText(location))
    }

    fun screenshotFailureContent(errorCode: Int): String {
        return when (errorCode) {
            android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "截图太频繁，请稍后再试"
            android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "截图权限不可用，请检查无障碍服务"
            android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "当前屏幕不可截图，请重试"
            android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW -> "当前窗口不可截图，请重试"
            android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "当前页面禁止截图"
            android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "系统截图失败，请重试"
            else -> "系统截图失败，请重试"
        }
    }

    fun compactTitle(status: String, detail: String): String {
        val cleanDetail = cleanTitleDetail(detail)
        return if (cleanDetail.isBlank()) status else "$status|$cleanDetail"
    }

    fun fallbackDetail(): String = "点击查看详情"

    fun fallbackCreateEvent(): String = "点击创建事件"

    fun cleanText(value: String?): String? {
        val clean = value
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: return null
        return if (clean.length > RESULT_TEXT_MAX_CHARS) {
            clean.take(RESULT_TEXT_MAX_CHARS - 1) + "..."
        } else {
            clean
        }
    }

    private fun cleanTitleDetail(value: String): String {
        return value
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            .orEmpty()
    }
}
