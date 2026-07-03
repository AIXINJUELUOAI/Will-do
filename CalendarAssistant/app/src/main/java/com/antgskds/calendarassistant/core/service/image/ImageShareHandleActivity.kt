package com.antgskds.calendarassistant.core.service.image

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.util.ImageImportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Receives system image shares and dispatches them to the selected Sharesheet target. */
class ImageShareHandleActivity : ComponentActivity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handled = false
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (handled) return
        handled = true
        val uri = resolveSharedImageUri(intent)
        if (uri == null) {
            Toast.makeText(this, "未找到可导入的图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        when (resolveShareMode(intent)) {
            ShareMode.RECOGNIZE -> enqueueImageRecognition(uri)
            ShareMode.QUICK_MEMO -> createImageQuickMemo(uri)
        }
    }

    private fun enqueueImageRecognition(uri: Uri) {
        lifecycleScope.launch {
            try {
                val imageFile = withContext(Dispatchers.IO) {
                    ImageImportUtils.createImportedImageFile(this@ImageShareHandleActivity, prefix = "SHARE_").also { file ->
                        check(ImageImportUtils.copyUriToFile(this@ImageShareHandleActivity, uri, file)) { "图片读取失败" }
                    }
                }
                ImageShareRecognitionWorker.enqueue(
                    context = applicationContext,
                    imagePath = imageFile.absolutePath,
                    sourceId = uri.toString()
                )
                Toast.makeText(this@ImageShareHandleActivity, "已开始识别，可离开应用", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ImageShareHandleActivity, e.message ?: "图片识别启动失败", Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }

    private fun createImageQuickMemo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val app = application as App
                val imageFile = withContext(Dispatchers.IO) {
                    ImageImportUtils.createQuickMemoImageFile(this@ImageShareHandleActivity).also { file ->
                        check(ImageImportUtils.copyUriToFile(this@ImageShareHandleActivity, uri, file)) { "图片读取失败" }
                    }
                }
                withContext(Dispatchers.IO) {
                    app.quickMemoCenter.createImageMemo(imageFile.absolutePath)
                }
                Toast.makeText(this@ImageShareHandleActivity, "已保存到随口记", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ImageShareHandleActivity, e.message ?: "保存图片随口记失败", Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }

    private fun resolveShareMode(intent: Intent?): ShareMode {
        val explicitMode = intent?.getStringExtra(EXTRA_SHARE_MODE)
        if (explicitMode == MODE_QUICK_MEMO) return ShareMode.QUICK_MEMO
        if (explicitMode == MODE_RECOGNIZE) return ShareMode.RECOGNIZE

        val resolvedComponent = intent?.component ?: componentName
        val className = resolvedComponent.className
        if (className.endsWith("ImageShareQuickMemoActivity")) return ShareMode.QUICK_MEMO
        if (className.endsWith("ImageShareRecognizeActivity")) return ShareMode.RECOGNIZE

        val metaMode = runCatching {
            packageManager.getActivityInfo(
                resolvedComponent,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            ).metaData?.getString(META_SHARE_MODE)
        }.getOrNull()

        return if (metaMode == MODE_QUICK_MEMO) ShareMode.QUICK_MEMO else ShareMode.RECOGNIZE
    }

    private fun resolveSharedImageUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            else -> null
        }
    }

    private enum class ShareMode { RECOGNIZE, QUICK_MEMO }

    companion object {
        const val EXTRA_SHARE_MODE = "com.antgskds.calendarassistant.extra.SHARE_MODE"
        const val META_SHARE_MODE = "com.antgskds.calendarassistant.meta.SHARE_MODE"
        const val MODE_RECOGNIZE = "recognize"
        const val MODE_QUICK_MEMO = "quick_memo"
    }
}
