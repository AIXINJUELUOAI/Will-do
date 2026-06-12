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
import com.antgskds.calendarassistant.BuildConfig
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.Setting
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.cloud.SpeechUtility
import com.iflytek.cloud.ui.RecognizerDialog
import com.iflytek.cloud.ui.RecognizerDialogListener
import org.json.JSONObject
import java.util.LinkedHashMap

class IflytekRecognizerTestActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var recognizerDialog: RecognizerDialog? = null
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private val results = LinkedHashMap<String, String>()

    private val requestRecordAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startRecognizerDialog()
        } else {
            updateStatus("录音权限未授权")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildContentView()
        initializeIflytek()
        if (intent.getBooleanExtra(EXTRA_AUTO_START_DIALOG, false)) {
            statusText.post { ensurePermissionAndStart() }
        }
    }

    private fun buildContentView() {
        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        statusText = TextView(this).apply {
            text = "讯飞 SDK 录音测试"
            textSize = 18f
        }
        resultText = TextView(this).apply {
            text = "识别结果会显示在这里"
            textSize = 16f
        }
        val startButton = Button(this).apply {
            text = "打开讯飞录音对话框"
            setOnClickListener { ensurePermissionAndStart() }
        }
        val resetButton = Button(this).apply {
            text = "清空结果"
            setOnClickListener {
                results.clear()
                resultText.text = "识别结果会显示在这里"
                updateStatus("已清空")
            }
        }
        root.addView(statusText, LinearLayout.LayoutParams(-1, -2))
        root.addView(startButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(resetButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(resultText, LinearLayout.LayoutParams(-1, -2))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun initializeIflytek() {
        val appId = BuildConfig.IFLYTEK_TEST_APP_ID
        if (appId.isBlank()) {
            updateStatus("未配置讯飞 APPID")
            return
        }
        SpeechUtility.createUtility(this, "${SpeechConstant.APPID}=$appId")
        Setting.setShowLog(true)
        recognizer = SpeechRecognizer.createRecognizer(this, initListener)
        recognizerDialog = RecognizerDialog(this, initListener)
        updateStatus("SDK 初始化中，APPID=$appId")
    }

    private fun ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecognizerDialog()
        } else {
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecognizerDialog() {
        val recognizer = recognizer
        val dialog = recognizerDialog
        if (recognizer == null || dialog == null) {
            updateStatus("识别对象创建失败，请检查 Msc.jar/libmsc.so")
            return
        }
        results.clear()
        resultText.text = "请开始说话..."
        applyRecognizerParams(recognizer::setParameter)
        applyRecognizerParams(dialog::setParameter)
        dialog.setListener(dialogListener)
        dialog.show()
        updateStatus("已打开讯飞录音对话框")
    }

    private fun applyRecognizerParams(setParameter: (String, String?) -> Unit) {
        val audioPath = getExternalFilesDir("msc")!!.absolutePath + "/iflytek_test.wav"
        setParameter(SpeechConstant.PARAMS, null)
        setParameter(SpeechConstant.CLOUD_GRAMMAR, null)
        setParameter(SpeechConstant.SUBJECT, null)
        setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
        setParameter(SpeechConstant.DOMAIN, "iat")
        setParameter(SpeechConstant.RESULT_TYPE, "json")
        setParameter(SpeechConstant.LANGUAGE, "zh_cn")
        setParameter(SpeechConstant.ACCENT, "mandarin")
        setParameter(SpeechConstant.SAMPLE_RATE, "16000")
        setParameter(SpeechConstant.VAD_BOS, "4000")
        setParameter(SpeechConstant.VAD_EOS, "1000")
        setParameter(SpeechConstant.ASR_PTT, "1")
        setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true")
        setParameter(SpeechConstant.AUDIO_FORMAT, "wav")
        setParameter(SpeechConstant.ASR_AUDIO_PATH, audioPath)
    }

    private val initListener = InitListener { code ->
        Log.d(TAG, "SpeechRecognizer init code=$code")
        if (code == ErrorCode.SUCCESS) {
            updateStatus("SDK 初始化成功")
        } else {
            updateStatus("SDK 初始化失败：$code")
        }
    }

    private val dialogListener = object : RecognizerDialogListener {
        override fun onResult(result: RecognizerResult, isLast: Boolean) {
            appendResult(result)
            if (isLast) updateStatus("识别完成")
        }

        override fun onError(error: SpeechError) {
            val message = error.getPlainDescription(true)
            updateStatus("识别失败：$message")
        }
    }

    private fun appendResult(result: RecognizerResult) {
        val json = result.resultString
        val text = parseIatText(json)
        val sn = runCatching { JSONObject(json).optString("sn") }.getOrDefault(results.size.toString())
        results[sn] = text
        resultText.text = results.values.joinToString(separator = "")
    }

    private fun parseIatText(json: String): String {
        return runCatching {
            val root = JSONObject(json)
            val ws = root.optJSONArray("ws") ?: return@runCatching ""
            buildString {
                for (i in 0 until ws.length()) {
                    val cw = ws.getJSONObject(i).optJSONArray("cw") ?: continue
                    if (cw.length() > 0) append(cw.getJSONObject(0).optString("w"))
                }
            }
        }.getOrDefault("")
    }

    private fun updateStatus(message: String) {
        Log.d(TAG, message)
        if (::statusText.isInitialized) statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        recognizer?.cancel()
        recognizer?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "IflytekRecognizerTest"
        const val EXTRA_AUTO_START_DIALOG = "extra_auto_start_dialog"
    }
}
