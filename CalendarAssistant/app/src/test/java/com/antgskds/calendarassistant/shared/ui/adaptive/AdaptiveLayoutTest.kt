package com.antgskds.calendarassistant.shared.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun widthClassUsesExpectedBoundaries() {
        assertEquals(AppWindowWidthClass.COMPACT, appWindowWidthClass(599f))
        assertEquals(AppWindowWidthClass.MEDIUM, appWindowWidthClass(600f))
        assertEquals(AppWindowWidthClass.MEDIUM, appWindowWidthClass(839f))
        assertEquals(AppWindowWidthClass.EXPANDED, appWindowWidthClass(840f))
    }

    @Test
    fun mediumWidthUsesRailWithoutForcingTwoPane() {
        val info = AdaptiveLayoutInfo(widthClass = AppWindowWidthClass.MEDIUM)

        assertEquals(true, info.useNavigationRail)
        assertEquals(false, info.useTwoPaneContent)
    }

    @Test
    fun expandedWidthUsesRailAndTwoPane() {
        val info = AdaptiveLayoutInfo(widthClass = AppWindowWidthClass.EXPANDED)

        assertEquals(true, info.useNavigationRail)
        assertEquals(true, info.useTwoPaneContent)
    }

    @Test
    fun verticalSeparatingHingeEnablesTwoPaneContent() {
        val info = AdaptiveLayoutInfo(
            widthClass = AppWindowWidthClass.MEDIUM,
            hasVerticalSeparatingHinge = true,
        )

        assertEquals(true, info.useTwoPaneContent)
    }

    @Test
    fun tabletopPostureEnablesTwoPaneContent() {
        val info = AdaptiveLayoutInfo(
            widthClass = AppWindowWidthClass.MEDIUM,
            isTabletop = true,
        )

        assertEquals(true, info.useTwoPaneContent)
    }
}
