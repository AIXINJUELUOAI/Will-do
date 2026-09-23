package com.antgskds.calendarassistant.feature.schedule.domain.course

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.schedule.domain.model.isCourse
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

/** 总开关与手势偏好分离，课程数据不因关闭而删除。 */
object CourseFeaturePolicy {
    fun enabled(settings: MySettings): Boolean = settings.courseModuleEnabled
    fun swipeEnabled(settings: MySettings): Boolean = enabled(settings) && settings.courseFeatureEnabled
    fun allows(event: Event, settings: MySettings): Boolean = !event.isCourse || enabled(settings)
    fun allowsTag(tag: String, settings: MySettings): Boolean = tag != EventTags.COURSE || enabled(settings)
    fun allowsReminder(tag: String, triggerAtMillis: Long, settings: MySettings): Boolean =
        tag != EventTags.COURSE || (enabled(settings) && triggerAtMillis >= settings.courseRemindersResumeAtMillis)

    fun onSettingsChanged(previous: MySettings, next: MySettings, nowMillis: Long): MySettings =
        if (!enabled(previous) && enabled(next)) next.copy(courseRemindersResumeAtMillis = nowMillis) else next
}
