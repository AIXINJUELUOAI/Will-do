package com.antgskds.calendarassistant.platform.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object MapNavigationLauncher {
    fun openLocation(context: Context, location: String): Boolean {
        val query = location.trim()
        if (query.isEmpty()) return false

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        ).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
