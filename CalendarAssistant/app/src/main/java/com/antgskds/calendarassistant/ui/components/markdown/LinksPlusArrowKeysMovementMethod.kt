package com.antgskds.calendarassistant.ui.components.markdown

import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.method.MovementMethod
import android.text.style.URLSpan
import android.view.MotionEvent
import android.widget.TextView

class LinksPlusArrowKeysMovementMethod : ArrowKeyMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.action
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            var x = event.x.toInt()
            var y = event.y.toInt()
            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop
            x += widget.scrollX
            y += widget.scrollY
            val layout: Layout = widget.layout
            val line: Int = layout.getLineForVertical(y)
            val off: Int = layout.getOffsetForHorizontal(line, x.toFloat())
            val link = buffer.getSpans(off, off, LinkEditHandler.EditLinkSpan::class.java)
            if (link.isNotEmpty()) {
                if (action == MotionEvent.ACTION_UP) {
                    link[0].onClick(widget)
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]))
                }
                return true
            }
            val urlSpan = buffer.getSpans(off, off, URLSpan::class.java)
            if (urlSpan.isNotEmpty()) {
                if (action == MotionEvent.ACTION_UP) {
                    urlSpan[0].onClick(widget)
                } else {
                    Selection.setSelection(
                        buffer,
                        buffer.getSpanStart(urlSpan[0]),
                        buffer.getSpanEnd(urlSpan[0])
                    )
                }
                return true
            }
        }
        return false
    }

    companion object {
        private var instanceRef: LinksPlusArrowKeysMovementMethod? = null
        val instance: MovementMethod
            get() {
                if (instanceRef == null) instanceRef = LinksPlusArrowKeysMovementMethod()
                return instanceRef!!
            }
    }
}
