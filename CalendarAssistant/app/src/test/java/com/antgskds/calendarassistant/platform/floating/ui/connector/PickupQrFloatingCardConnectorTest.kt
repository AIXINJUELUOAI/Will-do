package com.antgskds.calendarassistant.platform.floating.ui.connector

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.platform.floating.ui.contract.PickupQrFloatingCardUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PickupQrFloatingCardConnectorTest {
    @Test
    fun `maps food event into pure presentation state`() {
        val event = Event(
            id = 7L,
            title = "",
            description = "A123|二食堂|南门",
            tag = EventTags.FOOD,
            codeQrPayload = "  qr://food/A123  "
        )

        val state = buildPickupQrFloatingCardUiState(event)

        assertEquals(
            PickupQrFloatingCardUiState(
                qrPayload = "qr://food/A123",
                typeLabel = "取餐二维码",
                title = "取餐二维码",
                code = "A123",
                location = "南门",
                completeLabel = "已取餐"
            ),
            state
        )
    }

    @Test
    fun `preserves title and falls back to second location field`() {
        val event = Event(
            id = 8L,
            title = "电影票",
            description = "T9|一号窗口|",
            tag = EventTags.TICKET,
            codeQrPayload = "ticket-payload"
        )

        val state = buildPickupQrFloatingCardUiState(event)

        assertEquals("电影票", state.title)
        assertEquals("取票二维码", state.typeLabel)
        assertEquals("T9", state.code)
        assertEquals("一号窗口", state.location)
        assertEquals("已取票", state.completeLabel)
    }
}
