package com.antgskds.calendarassistant.core.developer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.course.CourseMeta
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.theme.EventColors
import java.time.LocalDateTime
import java.time.ZoneId

object DeveloperTestDataFactory {
    enum class TestEventType(val label: String) {
        GENERAL("普通日程"),
        RECURRING("重复日程"),
        PICKUP("取件码"),
        FOOD("取餐码"),
        TICKET("取票码"),
        SENDER("寄件码"),
        TRAIN("列车"),
        FLIGHT("航班"),
        TAXI("打车"),
        COURSE("课程")
    }

    data class TestEventBundle(
        val patches: List<EventPatch> = emptyList(),
        val events: List<Event> = emptyList()
    )

    val allTypes: List<TestEventType> = TestEventType.entries

    fun build(type: TestEventType, sequence: Int, settings: MySettings): TestEventBundle {
        return when (type) {
            TestEventType.GENERAL -> TestEventBundle(patches = listOf(generalPatch(sequence)))
            TestEventType.RECURRING -> TestEventBundle(patches = listOf(recurringPatch(sequence)))
            TestEventType.PICKUP -> TestEventBundle(patches = listOf(pickupPatch(sequence)))
            TestEventType.FOOD -> TestEventBundle(patches = listOf(foodPatch(sequence)))
            TestEventType.TICKET -> TestEventBundle(patches = listOf(ticketPatch(sequence)))
            TestEventType.SENDER -> TestEventBundle(patches = listOf(senderPatch(sequence)))
            TestEventType.TRAIN -> TestEventBundle(patches = listOf(trainPatch(sequence)))
            TestEventType.FLIGHT -> TestEventBundle(patches = listOf(flightPatch(sequence)))
            TestEventType.TAXI -> TestEventBundle(patches = listOf(taxiPatch(sequence)))
            TestEventType.COURSE -> TestEventBundle(events = listOf(courseEvent(sequence)))
        }
    }

    fun buildAll(sequence: Int, settings: MySettings): TestEventBundle {
        return allTypes
            .mapIndexed { index, type -> build(type, sequence + index, settings) }
            .fold(TestEventBundle()) { acc, bundle ->
                TestEventBundle(
                    patches = acc.patches + bundle.patches,
                    events = acc.events + bundle.events
                )
            }
    }

    private fun generalPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        return patch(
            title = "[DEV] 普通日程 $sequence",
            start = start,
            end = start.plusHours(1),
            location = "测试会议室 ${sequence % 5 + 1}",
            description = "【日程】开发者测试普通日程 #$sequence",
            tag = EventTags.GENERAL,
            color = color(sequence)
        )
    }

    private fun recurringPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        return patch(
            title = "[DEV] 每周重复日程 $sequence",
            start = start,
            end = start.plusMinutes(45),
            location = "重复测试地点",
            description = "【日程】每周重复 4 次的开发者测试日程 #$sequence",
            tag = EventTags.GENERAL,
            color = color(sequence + 1),
            rrule = "FREQ=WEEKLY;INTERVAL=1;COUNT=4"
        )
    }

    private fun pickupPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        val code = "A${1000 + sequence}"
        return patch(
            title = "[DEV] 菜鸟取件 $code",
            start = start,
            end = start.plusMinutes(30),
            location = "东门驿站",
            description = "【取件】$code|菜鸟驿站|东门货架 ${sequence % 9 + 1}",
            tag = EventTags.PICKUP,
            color = color(sequence + 2)
        )
    }

    private fun foodPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        val code = "F${200 + sequence}"
        return patch(
            title = "[DEV] 外卖取餐 $code",
            start = start,
            end = start.plusMinutes(25),
            location = "取餐柜 ${sequence % 6 + 1} 号",
            description = "【取餐】$code|麦当劳|取餐柜 ${sequence % 6 + 1} 号",
            tag = EventTags.FOOD,
            color = color(sequence + 3)
        )
    }

    private fun ticketPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        val code = "T${3000 + sequence}"
        return patch(
            title = "[DEV] 取票 $code",
            start = start,
            end = start.plusMinutes(40),
            location = "剧院自助取票机",
            description = "【取票】$code|城市剧院|自助取票机",
            tag = EventTags.TICKET,
            color = color(sequence + 4)
        )
    }

    private fun senderPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        val code = "S${4000 + sequence}"
        return patch(
            title = "[DEV] 寄件 $code",
            start = start,
            end = start.plusMinutes(35),
            location = "快递服务点",
            description = "【寄件】$code|顺丰|快递服务点",
            tag = EventTags.SENDER,
            color = color(sequence + 5)
        )
    }

    private fun trainPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        val trainNo = "G${1000 + sequence % 800}"
        return patch(
            title = "[DEV] $trainNo 深圳-武汉",
            start = start,
            end = start.plusHours(4).plusMinutes(30),
            location = "深圳北站",
            description = "【列车】$trainNo|A${sequence % 20 + 1}|${sequence % 12 + 1}车${sequence % 80 + 1}A",
            tag = EventTags.TRAIN,
            color = color(sequence + 6),
            reminder1Minutes = 30
        )
    }

    private fun flightPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        val flightNo = "CA${1200 + sequence % 700}"
        return patch(
            title = "[DEV] $flightNo 北京-上海",
            start = start,
            end = start.plusHours(2).plusMinutes(20),
            location = "首都机场 T3",
            description = "【航班】$flightNo|${sequence % 30 + 1}号登机口|${sequence % 40 + 1}A",
            tag = EventTags.FLIGHT,
            color = color(sequence + 7),
            reminder1Minutes = 60
        )
    }

    private fun taxiPatch(sequence: Int): EventPatch {
        val start = eventAnchor()
        val plate = "粤B${10000 + sequence % 90000}"
        return patch(
            title = "[DEV] 网约车 $plate",
            start = start,
            end = start.plusMinutes(45),
            location = "公司南门",
            description = "【用车】白色|比亚迪秦|$plate",
            tag = EventTags.TAXI,
            color = color(sequence + 8)
        )
    }

    private fun courseEvent(sequence: Int): Event {
        val start = eventAnchor()
        val startNode = sequence % 4 * 2 + 1
        val meta = CourseMeta(
            uid = "dev-course-$sequence-${System.currentTimeMillis()}",
            teacher = "测试教师 ${sequence % 5 + 1}",
            dayOfWeek = start.dayOfWeek.value.coerceIn(1, 7),
            startNode = startNode,
            endNode = startNode + 1,
            startWeek = 1,
            endWeek = 4,
            weekType = 0
        )
        val zone = ZoneId.systemDefault()
        return Event(
            id = null,
            title = "[DEV] 测试课程 $sequence",
            startTS = start.atZone(zone).toEpochSecond(),
            endTS = start.plusMinutes(90).atZone(zone).toEpochSecond(),
            location = "教学楼 ${sequence % 6 + 1}01",
            description = CourseEventMapper.buildParentDescription(meta),
            color = color(sequence + 9),
            rrule = "FREQ=WEEKLY;INTERVAL=1;COUNT=4",
            timeZone = zone.id,
            tag = EventTags.COURSE
        )
    }

    private fun patch(
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        location: String,
        description: String,
        tag: String,
        color: Int,
        rrule: String = "",
        reminder1Minutes: Int = -1,
        reminder2Minutes: Int = -1,
        reminder3Minutes: Int = -1
    ): EventPatch {
        val zone = ZoneId.systemDefault()
        return EventPatch(
            title = title,
            startTS = start.atZone(zone).toEpochSecond(),
            endTS = end.atZone(zone).toEpochSecond(),
            location = location,
            description = description,
            tag = tag,
            color = color,
            rrule = rrule,
            reminder1Minutes = reminder1Minutes,
            reminder2Minutes = reminder2Minutes,
            reminder3Minutes = reminder3Minutes
        )
    }

    private fun eventAnchor(): LocalDateTime {
        return LocalDateTime.now()
            .withSecond(0)
            .withNano(0)
    }

    private fun color(sequence: Int): Int {
        return EventColors.getOrNull(sequence.mod(EventColors.size))?.toArgb() ?: Color(0xFF91A3B0).toArgb()
    }
}
