package com.antgskds.calendarassistant.shared.management.catalog

import com.antgskds.calendarassistant.ui.contract.SettingsDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCatalogTest {
    @Test
    fun `registers every settings destination exactly once`() {
        val registeredDestinations = PageCatalog.pages.map { it.destination }

        assertEquals(SettingsDestination.entries.toSet(), registeredDestinations.toSet())
        assertEquals(registeredDestinations.size, registeredDestinations.distinct().size)
    }

    @Test
    fun `owns unique routes and nonblank titles`() {
        val routes = PageCatalog.navigablePages.map { requireNotNull(it.route) }

        assertEquals(routes.size, routes.distinct().size)
        assertFalse(PageCatalog.weatherDetailPage.route in routes)
        assertTrue(PageCatalog.pages.all { it.title.isNotBlank() })
        assertTrue(
            PageCatalog.pages
                .filterNot { it.destination == SettingsDestination.Logout }
                .all { !it.route.isNullOrBlank() },
        )
        assertNull(PageCatalog.routeFor(SettingsDestination.Logout))
        PageCatalog.pages.forEach { page ->
            assertEquals(page.route, PageCatalog.routeFor(page.destination))
            assertEquals(page.title, PageCatalog.titleFor(page.destination))
        }
    }

    @Test
    fun `resolves legacy aliases and safe fallback`() {
        assertEquals(
            SettingsDestination.CourseManage,
            PageCatalog.resolveDestination("course_manager"),
        )
        assertEquals(
            SettingsDestination.TimeTableManage,
            PageCatalog.resolveDestination("timetable_editor"),
        )
        assertEquals(
            SettingsDestination.Weather,
            PageCatalog.resolveDestination(SettingsDestination.Weather.name),
        )
        assertEquals(
            SettingsDestination.Preference,
            PageCatalog.resolveDestination("not_registered"),
        )
    }

    @Test
    fun `registers weather detail as a weather child page`() {
        assertEquals(
            SettingsDestination.Weather,
            PageCatalog.weatherDetailPage.parentDestination,
        )
        assertEquals("settings_weather_detail", PageCatalog.weatherDetailPage.route)
        assertEquals("天气详情", PageCatalog.weatherDetailPage.title)
    }
}
