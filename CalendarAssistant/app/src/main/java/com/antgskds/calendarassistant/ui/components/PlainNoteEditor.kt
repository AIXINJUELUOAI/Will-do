package com.antgskds.calendarassistant.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.note.NoteAttachmentStore
import com.antgskds.calendarassistant.core.note.NoteDocument
import com.antgskds.calendarassistant.core.note.NoteParagraph
import com.antgskds.calendarassistant.core.note.NoteParagraphStyle
import com.antgskds.calendarassistant.core.note.NoteParagraphType
import com.antgskds.calendarassistant.core.note.NoteTextSpan
import com.antgskds.calendarassistant.core.note.NoteTextStyle

class PlainNoteEditorController {
    internal var toggleCurrentTodoAction: (() -> NoteDocument)? = null
    internal var clearFocusAction: (() -> Unit)? = null
    internal var applyTextStyleAction: ((NoteTextStyle) -> NoteDocument)? = null
    internal var setParagraphStyleAction: ((NoteParagraphStyle) -> NoteDocument)? = null
    internal var insertAttachmentAction: ((Uri) -> NoteDocument)? = null

    fun toggleCurrentTodo(): NoteDocument? = toggleCurrentTodoAction?.invoke()
    fun applyTextStyle(style: NoteTextStyle): NoteDocument? = applyTextStyleAction?.invoke(style)
    fun setParagraphStyle(style: NoteParagraphStyle): NoteDocument? = setParagraphStyleAction?.invoke(style)
    fun insertAttachment(uri: Uri): NoteDocument? = insertAttachmentAction?.invoke(uri)
    fun clearFocus() = clearFocusAction?.invoke()
}

@Composable
fun PlainNoteEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    document: NoteDocument,
    onDocumentChange: (NoteDocument) -> Unit,
    controller: PlainNoteEditorController,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val context = LocalContext.current
    val colors = NativeNoteEditorColors(
        textColor = textColor.toArgb(),
        hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f).toArgb(),
        metaColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f).toArgb(),
        accentColor = MaterialTheme.colorScheme.primary.toArgb(),
        checkboxTint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f).toArgb(),
        fileCardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f).toArgb()
    )
    var editor by remember { mutableStateOf<NativeLineNoteEditor?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            NativeLineNoteEditor(context).apply {
                setColors(colors)
                bind(title, document, onTitleChange, onDocumentChange)
                editor = this
            }
        },
        update = { view ->
            view.setColors(colors)
            view.updateExternal(title, document)
            editor = view
        }
    )

    LaunchedEffect(controller, editor) {
        controller.toggleCurrentTodoAction = { editor?.toggleCurrentTodo() ?: document }
        controller.applyTextStyleAction = { style -> editor?.applyTextStyle(style) ?: document }
        controller.setParagraphStyleAction = { style -> editor?.setParagraphStyle(style) ?: document }
        controller.insertAttachmentAction = { uri -> editor?.insertAttachment(uri) ?: document }
        controller.clearFocusAction = { editor?.hideKeyboard() }
    }

    DisposableEffect(Unit) {
        onDispose { editor?.dispose() }
    }
}

private data class NativeNoteEditorColors(
    val textColor: Int,
    val hintColor: Int,
    val metaColor: Int,
    val accentColor: Int,
    val checkboxTint: Int,
    val fileCardColor: Int
)

private class NativeLineNoteEditor(context: Context) : ScrollView(context) {
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28.dp, 20.dp, 28.dp, 220.dp)
    }
    private val metaView = TextView(context).apply { textSize = 12f; setPadding(0, 0, 0, 24.dp) }
    private val titleEdit = EditText(context).apply {
        background = null
        hint = "标题"
        textSize = 37f
        typeface = Typeface.DEFAULT_BOLD
        minHeight = 50.dp
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setSingleLine(false)
        movementMethod = ArrowKeyMovementMethod.getInstance()
    }

    private val lineViews = mutableListOf<LineViewHolder>()
    private var paragraphs = mutableListOf<NoteParagraph>()
    private var onTitleChange: (String) -> Unit = {}
    private var onDocumentChange: (NoteDocument) -> Unit = {}
    private var internalUpdate = false
    private var colors = NativeNoteEditorColors(0xff111111.toInt(), 0xff999999.toInt(), 0xff777777.toInt(), 0xff3f6db5.toInt(), 0xff999999.toInt(), 0xffeef2f8.toInt())
    private var focusedLineId: String? = null
    private var selectedAttachmentId: String? = null
    private var lastDocumentHash = ""

    init {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        root.addView(metaView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        root.addView(titleEdit, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        titleEdit.doAfterTextChanged { if (!internalUpdate) onTitleChange(it?.toString().orEmpty()) }
        titleEdit.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) clearAttachmentSelection() }
        setOnClickListener { clearAttachmentSelection(); focusLastLine() }
        root.setOnClickListener { clearAttachmentSelection(); focusLastLine() }
    }

    fun bind(title: String, document: NoteDocument, onTitleChange: (String) -> Unit, onDocumentChange: (NoteDocument) -> Unit) {
        this.onTitleChange = onTitleChange
        this.onDocumentChange = onDocumentChange
        updateExternal(title, document, force = true)
    }

    fun updateExternal(title: String, document: NoteDocument, force: Boolean = false) {
        val hash = documentHash(title, document)
        if (!force && hash == lastDocumentHash) { updateMeta(); return }
        if (!force && (currentEditText()?.hasFocus() == true || selectedAttachmentId != null)) { updateMeta(); return }
        internalUpdate = true
        if (titleEdit.text.toString() != title) titleEdit.setText(title)
        internalUpdate = false
        paragraphs = document.paragraphs.ifEmpty { listOf(NoteParagraph()) }.toMutableList()
        rebuildLines(focusedLineId)
        lastDocumentHash = hash
        updateMeta()
    }

    fun setColors(colors: NativeNoteEditorColors) {
        this.colors = colors
        metaView.setTextColor(colors.metaColor)
        titleEdit.setTextColor(colors.textColor)
        titleEdit.setHintTextColor(colors.hintColor)
        lineViews.forEach { it.applyColors(colors) }
    }

    fun toggleCurrentTodo(): NoteDocument {
        val index = focusedIndex()
        val paragraph = paragraphs.getOrNull(index) ?: return emit()
        if (paragraph.isAttachmentLine()) return emit()
        paragraphs[index] = if (paragraph.type == NoteParagraphType.TODO) paragraph.copy(type = NoteParagraphType.TEXT, checked = false) else paragraph.copy(type = NoteParagraphType.TODO, checked = false)
        rebuildLines(paragraphs[index].id)
        return emit()
    }

    fun setParagraphStyle(style: NoteParagraphStyle): NoteDocument {
        val index = focusedIndex()
        val paragraph = paragraphs.getOrNull(index) ?: return emit()
        if (paragraph.isAttachmentLine()) return emit()
        paragraphs[index] = paragraph.copy(style = style)
        rebuildLines(paragraphs[index].id)
        return emit()
    }

    fun applyTextStyle(style: NoteTextStyle): NoteDocument {
        val index = focusedIndex()
        val holder = lineViews.getOrNull(index) as? LineViewHolder.TextLine ?: return emit()
        val edit = holder.editText
        val selectionStart = minOf(edit.selectionStart, edit.selectionEnd).coerceAtLeast(0)
        val selectionEnd = maxOf(edit.selectionStart, edit.selectionEnd).coerceAtMost(edit.text.length)
        val hasSelection = selectionStart != selectionEnd
        val start = if (hasSelection) selectionStart else 0
        val end = if (hasSelection) selectionEnd else edit.text.length
        if (start == end) return emit()
        val paragraph = paragraphs.getOrNull(index) ?: return emit()
        paragraphs[index] = paragraph.copy(spans = toggleSpanStyle(paragraph.copy(text = edit.text.toString()), start, end, style))
        holder.setTextAndSpans(paragraphs[index])
        if (hasSelection) edit.setSelection(start, end) else edit.setSelection(selectionEnd.coerceIn(0, edit.text.length))
        return emit()
    }

    fun insertAttachment(uri: Uri): NoteDocument {
        val stored = runCatching { NoteAttachmentStore.copyFromUri(context, uri) }.getOrNull() ?: return emit()
        val type = if (stored.isImage) NoteParagraphType.IMAGE else NoteParagraphType.FILE
        val line = NoteParagraph(type = type, attachmentPath = stored.relativePath, attachmentName = stored.displayName, attachmentMime = stored.mimeType)
        val insertAt = if (paragraphs.isEmpty()) 0 else focusedIndex() + 1
        paragraphs.add(insertAt.coerceIn(0, paragraphs.size), line)
        val nextLine = NoteParagraph()
        paragraphs.add((insertAt + 1).coerceIn(0, paragraphs.size), nextLine)
        selectedAttachmentId = null
        focusedLineId = nextLine.id
        rebuildLines(nextLine.id)
        (lineViews.getOrNull(insertAt + 1) as? LineViewHolder.TextLine)?.requestFocusAt(0)
        return emit()
    }

    fun hideKeyboard() {
        currentEditText()?.clearFocus()
        context.inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }

    fun dispose() {
        lineViews.forEach { it.dispose() }
        lineViews.clear()
    }

    private fun rebuildLines(preserveFocusId: String?) {
        lineViews.forEach { it.dispose() }
        lineViews.clear()
        while (root.childCount > 2) root.removeViewAt(2)
        paragraphs.forEachIndexed { index, paragraph ->
            val holder: LineViewHolder = if (paragraph.isAttachmentLine()) {
                LineViewHolder.AttachmentLine(context, { selectAttachment(index) }, { deleteAttachmentLine(index) })
            } else {
                LineViewHolder.TextLine(
                    context = context,
                    orderedIndexProvider = { orderedIndexFor(index) },
                    onTextChanged = { text, _, _ -> onLineTextChanged(index, text) },
                    onFocus = { selectedAttachmentId = null; focusedLineId = paragraphs.getOrNull(index)?.id; refreshAttachmentSelection() },
                    onEnter = { splitLine(index) },
                    onBackspaceAtStart = { handleBackspaceAtStart(index) },
                    onCheckedChanged = { checked -> toggleChecked(index, checked) }
                )
            }
            holder.applyColors(colors)
            holder.bind(paragraph)
            lineViews += holder
            root.addView(holder.container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        updateMeta()
        val index = paragraphs.indexOfFirst { it.id == preserveFocusId }
        if (index >= 0) {
            val holder = lineViews.getOrNull(index)
            if (holder is LineViewHolder.TextLine) holder.requestFocusAtEnd() else selectAttachment(index)
        }
    }

    private fun onLineTextChanged(index: Int, text: String) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        paragraphs[index] = paragraph.copy(text = text, spans = shiftSpansForText(paragraph.spans, text.length))
        focusedLineId = paragraph.id
        updateMeta()
        emit()
    }

    private fun splitLine(index: Int): Boolean {
        val holder = lineViews.getOrNull(index) as? LineViewHolder.TextLine ?: return false
        val paragraph = paragraphs.getOrNull(index) ?: return false
        val text = holder.editText.text.toString()
        val start = minOf(holder.editText.selectionStart, holder.editText.selectionEnd).coerceIn(0, text.length)
        val before = text.substring(0, start)
        val after = text.substring(start)
        if (paragraph.type == NoteParagraphType.TODO && before.isBlank() && after.isBlank()) {
            paragraphs[index] = paragraph.copy(text = "", type = NoteParagraphType.TEXT, checked = false, spans = emptyList())
            rebuildLines(paragraph.id)
            emit()
            return true
        }
        if (paragraph.style.isListStyle() && before.isBlank() && after.isBlank()) {
            paragraphs[index] = paragraph.copy(style = NoteParagraphStyle.BODY)
            rebuildLines(paragraph.id)
            emit()
            return true
        }
        paragraphs[index] = paragraph.copy(text = before, spans = paragraph.spans.filter { it.end <= before.length })
        val newLineStyle = if (paragraph.style.isListStyle() && before.isNotBlank()) paragraph.style else NoteParagraphStyle.BODY
        val newLine = NoteParagraph(text = after, style = newLineStyle)
        paragraphs.add(index + 1, newLine)
        rebuildLines(newLine.id)
        (lineViews.getOrNull(index + 1) as? LineViewHolder.TextLine)?.requestFocusAt(0)
        emit()
        return true
    }

    private fun handleBackspaceAtStart(index: Int): Boolean {
        val paragraph = paragraphs.getOrNull(index) ?: return false
        if (paragraph.type == NoteParagraphType.TODO) {
            paragraphs[index] = paragraph.copy(type = NoteParagraphType.TEXT, checked = false)
            rebuildLines(paragraph.id)
            (lineViews.getOrNull(index) as? LineViewHolder.TextLine)?.requestFocusAt(0)
            emit()
            return true
        }
        if (paragraph.isAttachmentLine()) {
            if (selectedAttachmentId == paragraph.id) deleteAttachmentLine(index) else selectAttachment(index)
            return true
        }
        if (index <= 0) return false
        val previous = paragraphs[index - 1]
        if (previous.isAttachmentLine()) {
            selectAttachment(index - 1)
            return true
        }
        val current = paragraphs[index]
        val previousLength = previous.text.length
        paragraphs[index - 1] = previous.copy(text = previous.text + current.text, style = previous.style)
        paragraphs.removeAt(index)
        rebuildLines(previous.id)
        (lineViews.getOrNull(index - 1) as? LineViewHolder.TextLine)?.requestFocusAt(previousLength)
        emit()
        return true
    }

    private fun toggleChecked(index: Int, checked: Boolean) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        paragraphs[index] = paragraph.copy(type = NoteParagraphType.TODO, checked = checked)
        emit()
    }

    private fun selectAttachment(index: Int) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        if (!paragraph.isAttachmentLine()) return
        currentEditText()?.clearFocus()
        selectedAttachmentId = paragraph.id
        focusedLineId = paragraph.id
        lineViews.getOrNull(index)?.container?.requestFocus()
        refreshAttachmentSelection()
    }

    private fun deleteAttachmentLine(index: Int) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        NoteAttachmentStore.delete(context, paragraph.attachmentPath)
        paragraphs.removeAt(index)
        if (paragraphs.isEmpty() || paragraphs.none { !it.isAttachmentLine() }) paragraphs += NoteParagraph()
        val focusIndex = index.coerceAtMost(paragraphs.lastIndex)
        val focus = paragraphs.getOrNull(focusIndex)
        selectedAttachmentId = null
        focusedLineId = focus?.id
        rebuildLines(focus?.id)
        if (focus?.isAttachmentLine() == false) (lineViews.getOrNull(focusIndex) as? LineViewHolder.TextLine)?.requestFocusAt(0)
        emit()
    }

    private fun refreshAttachmentSelection() {
        lineViews.forEachIndexed { index, holder ->
            if (holder is LineViewHolder.AttachmentLine) holder.setSelected(paragraphs.getOrNull(index)?.id == selectedAttachmentId)
        }
    }

    private fun clearAttachmentSelection() {
        if (selectedAttachmentId == null) return
        selectedAttachmentId = null
        refreshAttachmentSelection()
    }

    private fun emit(): NoteDocument {
        val document = NoteDocument(paragraphs.toList())
        lastDocumentHash = documentHash(titleEdit.text.toString(), document)
        onDocumentChange(document)
        return document
    }

    private fun updateMeta() {
        metaView.text = "${java.time.LocalDate.now()} | ${paragraphs.sumOf { if (it.isAttachmentLine()) 0 else it.text.length }} 字 | 默认笔记本"
    }

    private fun orderedIndexFor(index: Int): Int {
        var count = 0
        for (i in 0..index.coerceAtMost(paragraphs.lastIndex)) {
            val paragraph = paragraphs[i]
            if (paragraph.style == NoteParagraphStyle.ORDERED) count++
        }
        return count.coerceAtLeast(1)
    }

    private fun focusedIndex(): Int {
        val byId = paragraphs.indexOfFirst { it.id == focusedLineId }
        if (byId >= 0) return byId
        val byFocus = lineViews.indexOfFirst { it is LineViewHolder.TextLine && it.editText.hasFocus() }
        return byFocus.takeIf { it >= 0 } ?: 0
    }

    private fun currentEditText(): EditText? = (lineViews.firstOrNull { it is LineViewHolder.TextLine && it.editText.hasFocus() } as? LineViewHolder.TextLine)?.editText

    private fun focusLastLine() {
        if (paragraphs.isEmpty()) {
            paragraphs += NoteParagraph()
            rebuildLines(paragraphs.first().id)
        }
        (lineViews.lastOrNull() as? LineViewHolder.TextLine)?.requestFocusAtEnd()
    }
}

private sealed class LineViewHolder {
    abstract val container: View
    abstract fun bind(paragraph: NoteParagraph)
    abstract fun applyColors(colors: NativeNoteEditorColors)
    abstract fun dispose()

    class TextLine(
        context: Context,
        private val orderedIndexProvider: () -> Int,
        private val onTextChanged: (String, Int, Int) -> Unit,
        private val onFocus: () -> Unit,
        private val onEnter: () -> Boolean,
        private val onBackspaceAtStart: () -> Boolean,
        private val onCheckedChanged: (Boolean) -> Unit
    ) : LineViewHolder() {
        override val container = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = 29.dp }
        private val checkBox = CheckBox(context).apply { minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0; setPadding(0, 0, 8.dp, 0) }
        private val prefixView = TextView(context).apply {
            textSize = 17f
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, 2.dp, 8.dp, 0)
        }
        val editText = EditText(context).apply {
            background = null
            includeFontPadding = false
            minHeight = 29.dp
            setPadding(0, 2.dp, 0, 2.dp)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textSize = 17f
            setSingleLine(false)
            movementMethod = ArrowKeyMovementMethod.getInstance()
        }
        private var internalUpdate = false
        private var watcher: TextWatcher? = null

        init {
            container.addView(checkBox, LinearLayout.LayoutParams(32.dp, 32.dp))
            container.addView(prefixView, LinearLayout.LayoutParams(32.dp, LinearLayout.LayoutParams.WRAP_CONTENT))
            container.addView(editText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            checkBox.setOnClickListener { onCheckedChanged(checkBox.isChecked) }
            editText.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) onFocus() }
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_ENTER -> onEnter()
                    KeyEvent.KEYCODE_DEL -> if ((editText.selectionStart == 0 && editText.selectionEnd == 0) || editText.text.isEmpty()) onBackspaceAtStart() else false
                    else -> false
                }
            }
            watcher = editText.doAfterTextChanged { if (!internalUpdate) onTextChanged(editText.text.toString(), editText.selectionStart, editText.selectionEnd) }
        }

        override fun bind(paragraph: NoteParagraph) {
            checkBox.visibility = if (paragraph.type == NoteParagraphType.TODO) View.VISIBLE else View.GONE
            checkBox.isChecked = paragraph.checked
            prefixView.visibility = if (paragraph.style.isPrefixStyle()) View.VISIBLE else View.GONE
            prefixView.text = when (paragraph.style) {
                NoteParagraphStyle.BULLET -> "•"
                NoteParagraphStyle.ORDERED -> "${orderedIndexProvider()}."
                NoteParagraphStyle.QUOTE -> "┃"
                else -> ""
            }
            setTextAndSpans(paragraph)
        }

        fun setTextAndSpans(paragraph: NoteParagraph) {
            internalUpdate = true
            editText.text = buildEditable(paragraph)
            editText.setSelection(editText.text.length)
            internalUpdate = false
            applyTextAppearance(paragraph)
        }

        override fun applyColors(colors: NativeNoteEditorColors) {
            editText.setTextColor(colors.textColor)
            editText.setHintTextColor(colors.hintColor)
            prefixView.setTextColor(colors.accentColor)
            checkBox.buttonTintList = ColorStateList.valueOf(colors.checkboxTint)
        }

        fun requestFocusAtEnd() = requestFocusAt(editText.text.length)

        fun requestFocusAt(offset: Int) {
            editText.requestFocus()
            editText.setSelection(offset.coerceIn(0, editText.text.length))
            editText.context.inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }

        override fun dispose() { watcher?.let { editText.removeTextChangedListener(it) } }

        private fun applyTextAppearance(paragraph: NoteParagraph) {
            val textSize = paragraph.style.textSizeSp()
            editText.textSize = textSize
            editText.typeface = if (paragraph.style.isHeadingStyle()) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            editText.inputType = if (paragraph.style == NoteParagraphStyle.CODE) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            editText.background = when (paragraph.style) {
                NoteParagraphStyle.CODE -> roundedDrawable(0x11_000000, 8.dp)
                NoteParagraphStyle.QUOTE -> roundedDrawable(0x08_000000, 8.dp)
                else -> null
            }
            editText.setPadding(if (paragraph.style == NoteParagraphStyle.CODE || paragraph.style == NoteParagraphStyle.QUOTE) 8.dp else 0, 2.dp, 0, 2.dp)
            editText.minHeight = (textSize + 12).toInt().dp
        }

        private fun buildEditable(paragraph: NoteParagraph): Editable {
            val builder = SpannableStringBuilder(paragraph.text)
            paragraph.spans.forEach { span ->
                val start = span.start.coerceIn(0, paragraph.text.length)
                val end = span.end.coerceIn(0, paragraph.text.length)
                if (start < end) {
                    if (span.bold && span.italic) builder.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    else if (span.bold) builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    else if (span.italic) builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (span.underline) builder.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (span.strike) builder.setSpan(StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            return builder
        }
    }

    class AttachmentLine(
        private val context: Context,
        private val onSelected: () -> Unit,
        private val onDelete: () -> Unit
    ) : LineViewHolder() {
        override val container = FrameLayout(context).apply { setPadding(0, 8.dp, 0, 8.dp); isFocusable = true; isFocusableInTouchMode = true }
        private var selected = false
        private var colors = NativeNoteEditorColors(0, 0, 0, 0, 0, 0)
        private var paragraph: NoteParagraph? = null
        private var overlayView: View? = null

        init {
            container.setOnClickListener { onSelected() }
            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) { onDelete(); true } else false
            }
        }

        override fun bind(paragraph: NoteParagraph) { this.paragraph = paragraph; render() }
        override fun applyColors(colors: NativeNoteEditorColors) { this.colors = colors; render() }
        override fun dispose() = Unit

        fun setSelected(selected: Boolean) { this.selected = selected; renderSelection() }

        private fun render() {
            val data = paragraph ?: return
            container.removeAllViews()
            if (data.type == NoteParagraphType.IMAGE) renderImage(data) else renderFile(data)
            renderSelection()
        }

        private fun renderImage(data: NoteParagraph) {
            val imageFile = NoteAttachmentStore.fileForRelativePath(context, data.attachmentPath)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)
            val ratio = if (options.outWidth > 0 && options.outHeight > 0) {
                options.outHeight.toFloat() / options.outWidth.toFloat()
            } else {
                0.62f
            }
            val displayWidth = context.resources.displayMetrics.widthPixels - 56.dp
            val displayHeight = (displayWidth * ratio).roundToInt().coerceIn(120.dp, 420.dp)
            val contentFrame = FrameLayout(context)
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
                runCatching { setImageBitmap(BitmapFactory.decodeFile(imageFile.absolutePath)) }
            }
            contentFrame.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addOverlay(contentFrame, 0)
            container.addView(contentFrame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, displayHeight))
        }

        private fun renderFile(data: NoteParagraph) {
            val contentFrame = FrameLayout(context)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp, 12.dp, 16.dp, 12.dp)
                background = roundedDrawable(colors.fileCardColor, 14.dp)
            }
            val icon = ImageView(context).apply {
                setImageResource(R.drawable.ic_note_file_filled)
                imageTintList = ColorStateList.valueOf(colors.accentColor)
                scaleType = ImageView.ScaleType.CENTER
            }
            val label = TextView(context).apply {
                text = data.attachmentName.ifBlank { data.attachmentPath.substringAfterLast('/') }
                textSize = 15f
                setTextColor(colors.textColor)
                maxLines = 2
            }
            row.addView(icon, LinearLayout.LayoutParams(42.dp, 42.dp))
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            contentFrame.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addOverlay(contentFrame, 14.dp)
            container.addView(contentFrame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 72.dp))
        }

        private fun addOverlay(contentFrame: FrameLayout, radius: Int) {
            overlayView = View(context).apply {
                background = roundedDrawable(0x333f6db5, radius)
                visibility = if (selected) View.VISIBLE else View.GONE
            }
            contentFrame.addView(overlayView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        private fun renderSelection() {
            overlayView?.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }
}

private fun NoteParagraph.isAttachmentLine(): Boolean = type == NoteParagraphType.IMAGE || type == NoteParagraphType.FILE

private fun roundedDrawable(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius.toFloat()
}

private fun roundedStrokeDrawable(strokeColor: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
    setColor(AndroidColor.TRANSPARENT)
    cornerRadius = radius.toFloat()
    setStroke(2.dp, strokeColor)
}

private fun toggleSpanStyle(paragraph: NoteParagraph, start: Int, end: Int, style: NoteTextStyle): List<NoteTextSpan> {
    val selectedStart = start.coerceIn(0, paragraph.text.length)
    val selectedEnd = end.coerceIn(0, paragraph.text.length)
    if (selectedStart >= selectedEnd) return paragraph.spans
    val shouldRemove = paragraph.spans.any { it.start <= selectedStart && it.end >= selectedEnd && it.has(style) }
    val updated = mutableListOf<NoteTextSpan>()
    paragraph.spans.forEach { span ->
        if (!span.has(style) || span.end <= selectedStart || span.start >= selectedEnd) {
            updated += span
            return@forEach
        }
        if (span.start < selectedStart) updated += span.copy(end = selectedStart)
        if (span.end > selectedEnd) updated += span.copy(start = selectedEnd)
        val overlapStart = maxOf(span.start, selectedStart)
        val overlapEnd = minOf(span.end, selectedEnd)
        val withoutStyle = span.copy(start = overlapStart, end = overlapEnd).withStyle(style, false)
        if (!withoutStyle.isEmpty()) updated += withoutStyle
    }
    if (!shouldRemove) updated += NoteTextSpan(start = selectedStart, end = selectedEnd).withStyle(style, true)
    return updated.filterNot { it.isEmpty() || it.start >= it.end }
}

private fun shiftSpansForText(spans: List<NoteTextSpan>, textLength: Int): List<NoteTextSpan> = spans.mapNotNull { span ->
    val start = span.start.coerceIn(0, textLength)
    val end = span.end.coerceIn(0, textLength)
    if (start >= end || span.isEmpty()) null else span.copy(start = start, end = end)
}

private fun documentHash(title: String, document: NoteDocument): String = title + "|" + document.hashCode()

private val Context.inputMethodManager: InputMethodManager
    get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

private fun NoteParagraphStyle.textSizeSp(): Float = when (this) {
    NoteParagraphStyle.BODY, NoteParagraphStyle.QUOTE, NoteParagraphStyle.CODE, NoteParagraphStyle.BULLET, NoteParagraphStyle.ORDERED -> 17f
    NoteParagraphStyle.H5 -> 21f
    NoteParagraphStyle.H4 -> 25f
    NoteParagraphStyle.H3, NoteParagraphStyle.HEADING -> 29f
    NoteParagraphStyle.H2 -> 33f
    NoteParagraphStyle.H1 -> 37f
}

private fun NoteParagraphStyle.isHeadingStyle(): Boolean = when (this) {
    NoteParagraphStyle.H1,
    NoteParagraphStyle.H2,
    NoteParagraphStyle.H3,
    NoteParagraphStyle.H4,
    NoteParagraphStyle.H5,
    NoteParagraphStyle.HEADING -> true
    else -> false
}

private fun NoteParagraphStyle.isListStyle(): Boolean = this == NoteParagraphStyle.BULLET || this == NoteParagraphStyle.ORDERED

private fun NoteParagraphStyle.isPrefixStyle(): Boolean = this == NoteParagraphStyle.BULLET || this == NoteParagraphStyle.ORDERED || this == NoteParagraphStyle.QUOTE
