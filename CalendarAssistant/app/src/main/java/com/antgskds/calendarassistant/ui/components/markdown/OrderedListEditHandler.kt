package com.antgskds.calendarassistant.ui.components.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import java.util.regex.Pattern
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.OrderedListItemSpan
import io.noties.markwon.editor.EditHandler
import io.noties.markwon.editor.PersistedSpans

class OrderedListEditHandler : EditHandler<OrderedListItemSpan> {
    private lateinit var theme: MarkwonTheme

    override fun init(markwon: Markwon) {
        theme = markwon.configuration().theme()
    }

    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        builder.persistSpan(DynamicOrderedListItemSpan::class.java) { DynamicOrderedListItemSpan(theme) }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: OrderedListItemSpan,
        spanStart: Int,
        spanTextLength: Int
    ) {
        val persisted = persistedSpans.get(DynamicOrderedListItemSpan::class.java)
        editable.setSpan(persisted, spanStart, spanStart + spanTextLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    override fun markdownSpanType(): Class<OrderedListItemSpan> = OrderedListItemSpan::class.java

    class DynamicOrderedListItemSpan(
        private val theme: MarkwonTheme
    ) : LeadingMarginSpan {
        private val pattern = Pattern.compile("^\\s*(\\d+\\.)")

        override fun getLeadingMargin(first: Boolean): Int = theme.blockMargin

        override fun drawLeadingMargin(
            c: Canvas,
            p: Paint,
            x: Int,
            dir: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            first: Boolean,
            layout: Layout
        ) {
            if (!first) return
            val lineStart = text.toString().lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.toString().indexOf('\n', start).let { if (it == -1) text.length else it }
            val lineStr = text.subSequence(lineStart, lineEnd).toString()
            val matcher = pattern.matcher(lineStr)
            val numStr = if (matcher.find()) matcher.group(1) ?: "1." else "1."

            theme.applyListItemStyle(p)
            val textWidth = p.measureText(numStr)
            val margin = theme.blockMargin.toFloat()
            val drawX = x + (dir * (margin - textWidth) / 2f)
            c.drawText(numStr, drawX, baseline.toFloat(), p)
        }
    }
}
