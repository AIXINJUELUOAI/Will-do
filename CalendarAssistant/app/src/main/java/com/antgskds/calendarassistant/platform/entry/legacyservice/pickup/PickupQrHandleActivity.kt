package com.antgskds.calendarassistant.core.service.pickup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity

class PickupQrHandleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val eventId = intent.getLongExtra(MainActivity.EXTRA_OPEN_EVENT_ID, -1L).takeIf { it > 0L }
        if (eventId == null) {
            finishWithoutAnimation()
            return
        }

        val app = applicationContext as App
        val event = app.scheduleCenter.events.value.firstOrNull { it.id == eventId }
        val openedFloatingCard = event?.codeQrPayload?.isNotBlank() == true && app.floatingCenter.startPickupQrCard(eventId)
        if (!openedFloatingCard) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, eventId)
            })
        }
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }
}
