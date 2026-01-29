package com.antgskds.calendarassistant.core.importer

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.TimeNode
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpCourseBaseDTO
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpScheduleDTO
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpSettingsDTO
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.serialization.json.Json
import java.util.UUID

class WakeUpCourseImporter : ICourseImporter {

    companion object {
        private const val TAG = "WakeUpCourseImporter"
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // 随机获取课程颜色（使用 Color.kt 中的 EventColors）
    private fun getRandomCourseColor(): Color {
        return EventColors.random()
    }

    override fun supports(content: String): Boolean {
        val hasCourseLen = content.contains("\"courseLen\"")
        val hasStartNode = content.contains("\"startNode\"")
        val hasStep = content.contains("\"step\"")

        val supported = hasCourseLen || (hasStartNode && hasStep)

        Log.d(TAG, "supports() 检测结果: " +
                "courseLen=$hasCourseLen, " +
                "startNode=$hasStartNode, " +
                "step=$hasStep, " +
                "supported=$supported")

        // 打印文件前 200 字符用于调试
        val preview = content.take(200).replace("\n", "\\n")
        Log.d(TAG, "文件内容预览: $preview...")

        return supported
    }

    override suspend fun parse(content: String): Result<ImportResult> {
        Log.d(TAG, "开始解析文件，总长度: ${content.length} 字符")

        return try {
            val lines = content.lines().filter { it.isNotBlank() }
            Log.d(TAG, "文件共 ${lines.size} 行非空内容")

            var timeNodes: List<TimeNode> = emptyList()  // 不解析文件中的时间表，保持为空
            var semesterStartDate: String? = null
            var totalWeeks: Int? = null

            // 临时存储课程基础信息: Map<InternalId, BaseInfo>
            val courseBaseMap = mutableMapOf<Int, WakeUpCourseBaseDTO>()
            val courses = mutableListOf<Course>()

            // 用于统计解析情况
            var parsedSettings = false
            var parsedCourseBase = 0
            var parsedSchedules = 0

            lines.forEachIndexed { index, line ->
                val trimLine = line.trim()

                // 1. 尝试解析全局设置 (Line 3) - 只导入开学日期和总周数，忽略时间表
                if (trimLine.startsWith("{") && trimLine.contains("\"startDate\"")) {
                    try {
                        val settings = jsonParser.decodeFromString<WakeUpSettingsDTO>(trimLine)
                        if (settings.startDate.isNotBlank()) {
                            semesterStartDate = settings.startDate
                            Log.d(TAG, "✓ 解析到开学日期: $semesterStartDate")
                        }
                        if (settings.maxWeek > 0) {
                            totalWeeks = settings.maxWeek
                            Log.d(TAG, "✓ 解析到总周数: $totalWeeks")
                        }
                        parsedSettings = true
                    } catch (e: Exception) {
                        Log.w(TAG, "✗ 全局设置解析失败 (第 ${index + 1} 行): ${e.message}")
                        Log.d(TAG, "失败行内容: $trimLine")
                    }
                }

                // 2. 尝试解析课程字典 (Line 4) - 包含 courseName
                else if (trimLine.startsWith("[") && trimLine.contains("\"courseName\"")) {
                    try {
                        val baseInfos = jsonParser.decodeFromString<List<WakeUpCourseBaseDTO>>(trimLine)
                        baseInfos.forEach { courseBaseMap[it.id] = it }
                        parsedCourseBase = baseInfos.size
                        Log.d(TAG, "✓ 课程字典解析成功: $parsedCourseBase 门课程")
                    } catch (e: Exception) {
                        Log.w(TAG, "✗ 课程字典解析失败 (第 ${index + 1} 行): ${e.message}")
                        Log.d(TAG, "失败行内容: $trimLine")
                    }
                }

                // 3. 尝试解析排课表 (Line 5) - 包含 step, startNode，且有 node 字段
                else if (trimLine.startsWith("[") && trimLine.contains("\"startNode\"") && trimLine.contains("\"step\"") && trimLine.contains("\"day\"")) {
                    try {
                        val schedules = jsonParser.decodeFromString<List<WakeUpScheduleDTO>>(trimLine)
                        parsedSchedules = schedules.size
                        Log.d(TAG, "✓ 排课表解析成功: $parsedSchedules 条记录")

                        schedules.forEach { schedule ->
                            // 查找对应的基础信息
                            val baseInfo = courseBaseMap[schedule.id]

                            if (baseInfo != null) {
                                // 使用随机颜色（忽略文件中的颜色）
                                val colorObj = getRandomCourseColor()

                                // 生成 Course 对象
                                val course = Course(
                                    id = UUID.randomUUID().toString(),
                                    name = baseInfo.courseName,
                                    location = schedule.room,
                                    teacher = if (schedule.teacher.isNotBlank()) schedule.teacher else baseInfo.teacher,
                                    color = colorObj,
                                    dayOfWeek = schedule.day,
                                    startNode = schedule.startNode,
                                    endNode = schedule.startNode + schedule.step - 1, // 计算结束节次
                                    startWeek = schedule.startWeek,
                                    endWeek = schedule.endWeek,
                                    weekType = schedule.type, // WakeUp: 0=全,1=单,2=双。兼容 App 逻辑。
                                    isTemp = false
                                )
                                courses.add(course)
                            } else {
                                Log.w(TAG, "⚠ 课程 ID ${schedule.id} 在课程字典中未找到")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "✗ 排课表解析失败 (第 ${index + 1} 行): ${e.message}")
                        Log.d(TAG, "失败行内容: $trimLine")
                    }
                }
                // 注意：文件中的作息时间表（timeNodes）被忽略，只使用 APP 内的设置
            }

            // 解析结果汇总
            Log.d(TAG, "=== 解析结果汇总 ===")
            Log.d(TAG, "作息时间: 忽略文件中的，使用 APP 内的设置")
            Log.d(TAG, "全局设置: ${if (parsedSettings) "已解析" else "未找到"}")
            Log.d(TAG, "课程字典: $parsedCourseBase 门课程")
            Log.d(TAG, "排课记录: $parsedSchedules 条")
            Log.d(TAG, "生成课程: ${courses.size} 门")

            if (courses.isEmpty()) {
                val error = "无法解析有效数据，请确认文件格式。" +
                        "已解析: 课程字典=$parsedCourseBase, 排课记录=$parsedSchedules"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            } else {
                Log.d(TAG, "✓ 解析成功")
                Result.success(ImportResult(courses, timeNodes, semesterStartDate, totalWeeks))
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析过程发生异常", e)
            Result.failure(e)
        }
    }
}
