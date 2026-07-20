package com.antgskds.calendarassistant.feature.capsule.data

import com.antgskds.calendarassistant.shared.query.CapsuleRouteMode
import com.antgskds.calendarassistant.shared.query.CapsuleRoutingQueryApi
import com.antgskds.calendarassistant.shared.util.OsUtils
import com.antgskds.calendarassistant.platform.xposed.XposedModuleStatus

class LocalCapsuleRoutingQueryApi : CapsuleRoutingQueryApi {
    override fun resolveMode(liveCapsuleEnabled: Boolean): CapsuleRouteMode {
        if (!liveCapsuleEnabled) return CapsuleRouteMode.STANDARD_NOTIFICATION
        if (OsUtils.isHyperOS() && XposedModuleStatus.isActive()) {
            return CapsuleRouteMode.MIUI_ISLAND
        }
        return CapsuleRouteMode.LIVE_CAPSULE
    }
}
