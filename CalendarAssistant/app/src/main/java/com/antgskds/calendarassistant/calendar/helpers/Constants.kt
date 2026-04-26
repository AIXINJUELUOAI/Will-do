package com.antgskds.calendarassistant.calendar.helpers

const val CALDAV = "Caldav"
const val REGULAR_EVENT_TYPE_ID = 1L

const val TYPE_EVENT = 0
const val TYPE_TASK = 1

const val REMINDER_OFF = -1
const val REMINDER_NOTIFICATION = 0
const val REMINDER_EMAIL = 1

const val FLAG_ALL_DAY = 1
const val FLAG_IS_IN_PAST = 1 shl 1
const val FLAG_MISSING_YEAR = 1 shl 2
const val FLAG_TASK_COMPLETED = 1 shl 3

const val SOURCE_SIMPLE_CALENDAR = "simple-calendar"
const val SOURCE_IMPORTED_ICS = "imported-ics"

const val TAG_GENERAL = "general"
const val TAG_FLIGHT = "flight"
const val TAG_TRAIN = "train"

const val STATE_PENDING = 0
const val STATE_COMPLETED = 1
const val STATE_CHECKED_IN = 2

const val CALDAV_SYNC = "caldav_sync"
const val CALDAV_SYNCED_CALENDAR_IDS = "caldav_synced_calendar_ids"
const val LAST_USED_CALDAV_CALENDAR = "last_used_caldav_calendar"

const val SCHEDULE_CALDAV_REQUEST_CODE = 10000
