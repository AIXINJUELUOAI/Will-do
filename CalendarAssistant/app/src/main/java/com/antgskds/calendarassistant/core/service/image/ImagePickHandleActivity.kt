package com.antgskds.calendarassistant.core.service.image

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.antgskds.calendarassistant.service.floating.FloatingScheduleService

/**
 * Transparent proxy Activity.
 * Used by [FloatingScheduleService] to launch the system image picker.
 */
class ImagePickHandleActivity : ComponentActivity() {

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val serviceIntent = Intent(this, FloatingScheduleService::class.java).apply {
            action = if (uri != null) {
                putExtra(FloatingScheduleService.EXTRA_IMAGE_URI, uri.toString())
                FloatingScheduleService.ACTION_IMAGE_PICKED
            } else {
                FloatingScheduleService.ACTION_IMAGE_PICK_CANCELLED
            }
        }
        startService(serviceIntent)

        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (savedInstanceState == null) {
            pickImageLauncher.launch("image/*")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overridePendingTransition(0, 0)
    }
}
