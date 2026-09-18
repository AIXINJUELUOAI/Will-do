package com.antgskds.calendarassistant.feature.accounting.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.util.ImageImportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 与日程附件一样置于 Sheet 正文下方；保持比例，可以随正文向下滚动查看。 */
@Composable
fun AccountingSourceImage(path: String?) {
    if (path.isNullOrBlank()) return
    val context = LocalContext.current
    val image by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path).canonicalFile
                val directory = File(context.filesDir, "accounting_images").canonicalFile
                if (file.parentFile == directory && file.isFile) ImageImportUtils.decodeSampledBitmapFromFile(file) else null
            }.getOrNull()
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("账单截图", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    image?.let {
        Image(it.asImageBitmap(), contentDescription = "识别此账单的截图",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
    } ?: Text("原截图暂不可用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
