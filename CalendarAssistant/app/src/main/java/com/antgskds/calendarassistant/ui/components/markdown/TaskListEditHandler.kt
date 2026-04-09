package com.antgskds.calendarassistant.ui.components.markdown

import android.content.Context
import android.text.Editable
import android.text.Spanned
import android.util.TypedValue
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.editor.EditHandler
import io.noties.markwon.editor.PersistedSpans
import io.noties.markwon.ext.tasklist.TaskListDrawable
import io.noties.markwon.ext.tasklist.TaskListSpan

class TaskListEditHandler(
    private val context: Context
) : EditHandler<TaskListSpan> {
    private lateinit var theme: MarkwonTheme

    override fun init(markwon: Markwon) {
        theme = markwon.configuration().theme()
    }

    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        val checkedFillColor = resolve(context, android.R.attr.textColorLink)
        val normalOutlineColor = resolve(context, android.R.attr.textColorLink)
        val checkMarkColor = resolve(context, android.R.attr.colorBackground)
        val drawable = TaskListDrawable(checkedFillColor, normalOutlineColor, checkMarkColor)
        builder.persistSpan(TaskListSpan::class.java) { TaskListSpan(theme, drawable, false) }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: TaskListSpan,
        spanStart: Int,
        spanTextLength: Int
    ) {
        val persisted = persistedSpans.get(TaskListSpan::class.java)
        persisted.setDone(span.isDone())
        editable.setSpan(persisted, spanStart, spanStart + spanTextLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    override fun markdownSpanType(): Class<TaskListSpan> = TaskListSpan::class.java

    private fun resolve(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        val attrs = intArrayOf(attr)
        val typedArray = context.obtainStyledAttributes(typedValue.data, attrs)
        return try {
            typedArray.getColor(0, 0)
        } finally {
            typedArray.recycle()
        }
    }
}
