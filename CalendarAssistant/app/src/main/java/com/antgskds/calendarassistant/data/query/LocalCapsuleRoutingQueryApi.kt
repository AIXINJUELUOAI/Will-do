package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.CapsuleRouteMode
import com.antgskds.calendarassistant.core.query.CapsuleRoutingQueryApi
import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.xposed.XposedModuleStatus

class LocalCapsuleRoutingQueryApi : CapsuleRoutingQueryApi {
    override fun resolveMode(liveCapsuleEnabled: Boolean): CapsuleRouteMode {
        if (!liveCapsuleEnabled) return CapsuleRouteMode.STANDARD_NOTIFICATION
        if (OsUtils.isHyperOS() && XposedModuleStatus.isActive()) {
            return CapsuleRouteMode.MIUI_ISLAND
        }
        return CapsuleRouteMode.LIVE_CAPSULE
    }
}
