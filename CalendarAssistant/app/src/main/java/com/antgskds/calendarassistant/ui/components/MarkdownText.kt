package com.antgskds.calendarassistant.ui.components

import android.text.TextUtils
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.antgskds.calendarassistant.ui.components.markdown.LinksPlusArrowKeysMovementMethod
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.ext.tables.TablePlugin

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    textColor: Int,
    linkColor: Int = textColor,
    enableLinkClicks: Boolean = false,
    textSizeSp: Float,
    lineSpacingExtraPx: Float = 0f
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                ellipsize = TextUtils.TruncateAt.END
                linksClickable = false
                movementMethod = if (enableLinkClicks) LinksPlusArrowKeysMovementMethod.instance else null
            }
        },
        update = { view ->
            view.maxLines = maxLines
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.textSize = textSizeSp
            view.setLineSpacing(lineSpacingExtraPx, 1f)
            view.movementMethod = if (enableLinkClicks) LinksPlusArrowKeysMovementMethod.instance else null
            markwon.setMarkdown(view, markdown)
        }
    )
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    textColor: Color,
    linkColor: Color = textColor,
    enableLinkClicks: Boolean = false,
    textSizeSp: Float,
    lineSpacingExtraPx: Float = 0f
) {
    MarkdownText(
        markdown = markdown,
        modifier = modifier,
        maxLines = maxLines,
        textColor = textColor.toArgb(),
        linkColor = linkColor.toArgb(),
        enableLinkClicks = enableLinkClicks,
        textSizeSp = textSizeSp,
        lineSpacingExtraPx = lineSpacingExtraPx
    )
}
