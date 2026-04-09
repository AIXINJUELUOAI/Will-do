package com.antgskds.calendarassistant.ui.components.markdown

import android.text.Editable
import android.text.Spanned
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.BulletListItemSpan
import io.noties.markwon.editor.EditHandler
import io.noties.markwon.editor.PersistedSpans

class BulletListEditHandler : EditHandler<BulletListItemSpan> {
    private lateinit var theme: MarkwonTheme

    override fun init(markwon: Markwon) {
        theme = markwon.configuration().theme()
    }

    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        builder.persistSpan(BulletListItemSpan::class.java) { BulletListItemSpan(theme, 0) }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: BulletListItemSpan,
        spanStart: Int,
        spanTextLength: Int
    ) {
        val persisted = persistedSpans.get(BulletListItemSpan::class.java)
        editable.setSpan(persisted, spanStart, spanStart + spanTextLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    override fun markdownSpanType(): Class<BulletListItemSpan> = BulletListItemSpan::class.java
}
