package com.antgskds.calendarassistant.core.service.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoAudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalRecorderTestActivity : ComponentActivity() {
    private val recorder by lazy { QuickMemoAudioRecorder(this) }
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var toggleButton: Button
    private var recording = false

    private val requestRecordAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else updateStatus("录音权限未授权")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildContentView()
    }

    private fun buildContentView() {
        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
        }
        statusText = TextView(this).apply {
            text = "本地录音测试"
            textSize = 18f
        }
        resultText = TextView(this).apply {
            text = "点击开始后录几秒，再点击停止。"
            textSize = 16f
        }
        toggleButton = Button(this).apply {
            text = "开始本地录音"
            setOnClickListener {
                if (recording) stopRecording() else ensurePermissionAndStart()
            }
        }
        root.addView(statusText, LinearLayout.LayoutParams(-1, -2))
        root.addView(toggleButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(resultText, LinearLayout.LayoutParams(-1, -2))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        lifecycleScope.launch {
            runCatching { recorder.start() }
                .onSuccess { path ->
                    recording = true
                    toggleButton.text = "停止本地录音"
                    updateStatus("本地录音中")
                    resultText.text = "录音文件：$path"
                }
                .onFailure { error ->
                    Log.e(TAG, "Local recorder test start failed", error)
                    updateStatus("本地录音启动失败：${error.message ?: error.javaClass.simpleName}")
                }
        }
    }

    private fun stopRecording() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { recorder.stop() }
            recording = false
            toggleButton.text = "开始本地录音"
            if (result == null) {
                updateStatus("没有录到文件")
            } else {
                updateStatus("本地录音完成：${result.durationMs}ms")
                resultText.text = "录音文件：${result.path}\n时长：${result.durationMs}ms"
            }
        }
    }

    private fun updateStatus(message: String) {
        Log.d(TAG, message)
        if (::statusText.isInitialized) statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        recorder.stopAndDiscard()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocalRecorderTest"
    }
}
