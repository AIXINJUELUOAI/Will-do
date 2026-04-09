package com.antgskds.calendarassistant.ui.components.markdown

import android.text.Editable
import android.text.Spanned
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.ThematicBreakSpan
import io.noties.markwon.editor.EditHandler
import io.noties.markwon.editor.PersistedSpans

class ThematicBreakEditHandler : EditHandler<ThematicBreakSpan> {
    private lateinit var theme: MarkwonTheme

    override fun init(markwon: Markwon) {
        theme = markwon.configuration().theme()
    }

    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        builder.persistSpan(ThematicBreakSpan::class.java) { ThematicBreakSpan(theme) }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: ThematicBreakSpan,
        spanStart: Int,
        spanTextLength: Int
    ) {
        val persisted = persistedSpans.get(ThematicBreakSpan::class.java)
        editable.setSpan(persisted, spanStart, spanStart + spanTextLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    override fun markdownSpanType(): Class<ThematicBreakSpan> = ThematicBreakSpan::class.java
}
