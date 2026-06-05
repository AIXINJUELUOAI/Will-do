package com.antgskds.calendarassistant.ui.components

import android.content.Context
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.text.InputType
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
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
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.note.NoteAttachmentStore
import com.antgskds.calendarassistant.core.note.NoteDocument
import com.antgskds.calendarassistant.core.note.NoteListStyle
import com.antgskds.calendarassistant.core.note.NoteParagraph
import com.antgskds.calendarassistant.core.note.NoteParagraphStyle
import com.antgskds.calendarassistant.core.note.NoteParagraphType
import com.antgskds.calendarassistant.core.note.NoteTableData
import com.antgskds.calendarassistant.core.note.NoteTextSpan
import com.antgskds.calendarassistant.core.note.NoteTextStyle
import com.antgskds.calendarassistant.core.note.effectiveListStyle
import com.antgskds.calendarassistant.core.note.effectiveParagraphStyle
import com.antgskds.calendarassistant.core.note.withMigratedParagraphStyles
import com.antgskds.calendarassistant.core.note.withMigratedParagraphStyle

class PlainNoteEditorController {
    internal var toggleCurrentTodoAction: (() -> NoteDocument)? = null
    internal var clearFocusAction: (() -> Unit)? = null
    internal var applyTextStyleAction: ((NoteTextStyle) -> NoteDocument)? = null
    internal var setParagraphStyleAction: ((NoteParagraphStyle) -> NoteDocument)? = null
    internal var setListStyleAction: ((NoteListStyle) -> NoteDocument)? = null
    internal var insertAttachmentAction: ((Uri) -> NoteDocument)? = null
    internal var insertDividerAction: (() -> NoteDocument)? = null
    internal var insertTableAction: (() -> NoteDocument)? = null
    internal var onImageShortcut: (() -> Unit)? = null
    internal var onFileShortcut: (() -> Unit)? = null

    fun toggleCurrentTodo(): NoteDocument? = toggleCurrentTodoAction?.invoke()
    fun applyTextStyle(style: NoteTextStyle): NoteDocument? = applyTextStyleAction?.invoke(style)
    fun setParagraphStyle(style: NoteParagraphStyle): NoteDocument? = setParagraphStyleAction?.invoke(style)
    fun setListStyle(style: NoteListStyle): NoteDocument? = setListStyleAction?.invoke(style)
    fun insertAttachment(uri: Uri): NoteDocument? = insertAttachmentAction?.invoke(uri)
    fun insertDivider(): NoteDocument? = insertDividerAction?.invoke()
    fun insertTable(): NoteDocument? = insertTableAction?.invoke()
    fun clearFocus() = clearFocusAction?.invoke()
}

@Composable
fun PlainNoteEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    document: NoteDocument,
    onDocumentChange: (NoteDocument) -> Unit,
    controller: PlainNoteEditorController,
    onOpenAttachment: (NoteParagraph) -> Unit = {},
    onImageShortcut: () -> Unit = {},
    onFileShortcut: () -> Unit = {},
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
                bind(title, document, onTitleChange, onDocumentChange, onOpenAttachment, onImageShortcut, onFileShortcut)
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
        controller.setListStyleAction = { style -> editor?.setListStyle(style) ?: document }
        controller.insertAttachmentAction = { uri -> editor?.insertAttachment(uri) ?: document }
        controller.insertDividerAction = { editor?.insertDivider() ?: document }
        controller.insertTableAction = { editor?.insertTable() ?: document }
        controller.onImageShortcut = onImageShortcut
        controller.onFileShortcut = onFileShortcut
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

private enum class InlineShortcutToken {
    TIME,
    DATE
}

private data class TableCellRef(
    val paragraphId: String,
    val row: Int,
    val column: Int
)

private enum class TableSelectionAxis {
    ROW,
    COLUMN
}

private data class TableSelectionRef(
    val axis: TableSelectionAxis,
    val index: Int
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
    private var onOpenAttachment: (NoteParagraph) -> Unit = {}
    private var onImageShortcut: () -> Unit = {}
    private var onFileShortcut: () -> Unit = {}
    private var internalUpdate = false
    private var colors = NativeNoteEditorColors(0xff111111.toInt(), 0xff999999.toInt(), 0xff777777.toInt(), 0xff3f6db5.toInt(), 0xff999999.toInt(), 0xffeef2f8.toInt())
    private var focusedLineId: String? = null
    private var pendingSelectionOffset: Int? = null
    private var selectedAttachmentId: String? = null
    private var activeTableCell: TableCellRef? = null
    private var lastBoundTitle = ""
    private var lastBoundDocument: NoteDocument? = null
    private var largeDocumentMode = false

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

    fun bind(
        title: String,
        document: NoteDocument,
        onTitleChange: (String) -> Unit,
        onDocumentChange: (NoteDocument) -> Unit,
        onOpenAttachment: (NoteParagraph) -> Unit,
        onImageShortcut: () -> Unit,
        onFileShortcut: () -> Unit
    ) {
        this.onTitleChange = onTitleChange
        this.onDocumentChange = onDocumentChange
        this.onOpenAttachment = onOpenAttachment
        this.onImageShortcut = onImageShortcut
        this.onFileShortcut = onFileShortcut
        updateExternal(title, document, force = true)
    }

    fun updateExternal(title: String, document: NoteDocument, force: Boolean = false) {
        if (!force && title == lastBoundTitle && document === lastBoundDocument) { updateMeta(); return }
        if (!force && (currentEditText()?.hasFocus() == true || currentTableEditText()?.hasFocus() == true || selectedAttachmentId != null)) { updateMeta(); return }
        internalUpdate = true
        if (titleEdit.text.toString() != title) titleEdit.setText(title)
        internalUpdate = false
        paragraphs = document.withMigratedParagraphStyles().paragraphs.ifEmpty { listOf(NoteParagraph()) }.toMutableList()
        largeDocumentMode = shouldUseLargeDocumentMode()
        rebuildLines(focusedLineId)
        lastBoundTitle = title
        lastBoundDocument = document
        updateMeta()
    }

    fun setColors(colors: NativeNoteEditorColors) {
        this.colors = colors
        metaView.setTextColor(colors.metaColor)
        titleEdit.setTextColor(colors.textColor)
        titleEdit.setHintTextColor(colors.hintColor)
        lineViews.forEach { it.applyColors(colors) }
    }

    private fun currentIndexOfParagraph(paragraphId: String?): Int {
        if (paragraphId == null) return -1
        return paragraphs.indexOfFirst { it.id == paragraphId }
    }

    private fun buildHolder(index: Int, paragraph: NoteParagraph, forceEditable: Boolean? = null): LineViewHolder {
        val paragraphId = paragraph.id
        val editable = forceEditable ?: isEditableLine(index, paragraph, focusedLineId)
        val holder: LineViewHolder = if (paragraph.isBlockLine()) {
            if (paragraph.type == NoteParagraphType.TABLE) {
                LineViewHolder.TableLine(
                    context = context,
                    onSelected = {
                        val currentIndex = currentIndexOfParagraph(paragraphId)
                        if (currentIndex >= 0) selectAttachment(currentIndex)
                    },
                    onDelete = {
                        val currentIndex = currentIndexOfParagraph(paragraphId)
                        if (currentIndex >= 0) deleteAttachmentLine(currentIndex)
                    },
                    onActivateCell = { row, column ->
                        val currentIndex = currentIndexOfParagraph(paragraphId)
                        if (currentIndex >= 0) activateTableCell(currentIndex, row, column)
                    },
                    onTableChanged = { table ->
                        val currentIndex = currentIndexOfParagraph(paragraphId)
                        if (currentIndex >= 0) updateTable(currentIndex, table)
                    }
                )
            } else {
                LineViewHolder.AttachmentLine(
                    context = context,
                    onSelected = {
                        val currentIndex = currentIndexOfParagraph(paragraphId)
                        if (currentIndex >= 0) selectAttachment(currentIndex)
                    },
                    onDelete = {
                        val currentIndex = currentIndexOfParagraph(paragraphId)
                        if (currentIndex >= 0) deleteAttachmentLine(currentIndex)
                    },
                    onOpen = {
                        val currentIndex = currentIndexOfParagraph(paragraphId)
                        if (currentIndex >= 0) onOpenAttachment(paragraphs[currentIndex])
                    }
                )
            }
        } else if (!editable) {
            LineViewHolder.ReadOnlyTextLine(
                context = context,
                orderedIndexProvider = {
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    orderedIndexFor(currentIndex.takeIf { it >= 0 } ?: index)
                },
                onClick = { offset ->
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    if (currentIndex >= 0) activateLine(currentIndex, offset)
                },
                onCheckedChanged = { checked ->
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    if (currentIndex >= 0) toggleChecked(currentIndex, checked)
                }
            )
        } else {
            LineViewHolder.TextLine(
                context = context,
                orderedIndexProvider = {
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    orderedIndexFor(currentIndex.takeIf { it >= 0 } ?: index)
                },
                onTextChanged = { text, _, _ ->
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    if (currentIndex >= 0) onLineTextChanged(currentIndex, text)
                },
                onFocus = {
                    val previousFocusedId = focusedLineId
                    clearActiveTableCell()
                    selectedAttachmentId = null
                    focusedLineId = paragraphId
                    refreshAttachmentSelection()
                    refreshActiveWindow(previousFocusedId, focusedLineId)
                },
                onEnter = { start, end ->
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    currentIndex >= 0 && splitLine(currentIndex, start, end)
                },
                onBackspaceAtStart = {
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    currentIndex >= 0 && handleBackspaceAtStart(currentIndex)
                },
                onMultilinePaste = { text, start, end ->
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    currentIndex >= 0 && insertMultilineText(currentIndex, text, start, end)
                },
                onCommittedMultiline = { start, end ->
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    currentIndex >= 0 && normalizeCommittedMultiline(currentIndex, start, end)
                },
                onCheckedChanged = { checked ->
                    val currentIndex = currentIndexOfParagraph(paragraphId)
                    if (currentIndex >= 0) toggleChecked(currentIndex, checked)
                }
            )
        }
        holder.applyColors(colors)
        holder.bind(paragraph)
        when (holder) {
            is LineViewHolder.AttachmentLine -> holder.setSelected(paragraphId == selectedAttachmentId)
            is LineViewHolder.TableLine -> {
                holder.setSelected(paragraphId == selectedAttachmentId)
                val active = activeTableCell?.takeIf { it.paragraphId == paragraphId }
                if (active != null) holder.setActiveCell(active.row, active.column) else holder.clearActiveCell()
            }
            else -> Unit
        }
        return holder
    }

    private fun replaceLine(index: Int, forceEditable: Boolean? = null): LineViewHolder? {
        val paragraph = paragraphs.getOrNull(index) ?: return null
        val previous = lineViews.getOrNull(index) ?: return null
        previous.dispose()
        root.removeViewAt(index + 2)
        val holder = buildHolder(index, paragraph, forceEditable)
        lineViews[index] = holder
        root.addView(
            holder.container,
            index + 2,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return holder
    }

    private fun insertLine(index: Int, paragraph: NoteParagraph, active: Boolean): LineViewHolder {
        val holder = buildHolder(index, paragraph, forceEditable = if (paragraph.isBlockLine()) false else active)
        lineViews.add(index, holder)
        root.addView(
            holder.container,
            index + 2,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return holder
    }

    private fun removeLine(index: Int) {
        val holder = lineViews.getOrNull(index) ?: return
        holder.dispose()
        lineViews.removeAt(index)
        root.removeViewAt(index + 2)
    }

    private fun bindLine(index: Int) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        val holder = lineViews.getOrNull(index) ?: return
        holder.applyColors(colors)
        holder.bind(paragraph)
    }

    private fun deactivateLine(index: Int) {
        replaceLine(index, forceEditable = false)
    }

    private fun activateLine(index: Int, offset: Int = Int.MAX_VALUE) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        if (paragraph.isBlockLine()) return
        val previousFocusedId = focusedLineId
        clearActiveTableCell()
        focusedLineId = paragraph.id
        selectedAttachmentId = null
        val holder = if (lineViews.getOrNull(index) is LineViewHolder.TextLine) {
            lineViews[index]
        } else {
            replaceLine(index, forceEditable = true)
        }
        refreshActiveWindow(previousFocusedId, focusedLineId)
        val targetOffset = if (offset == Int.MAX_VALUE) paragraph.text.length else offset
        (holder as? LineViewHolder.TextLine ?: lineViews.getOrNull(index) as? LineViewHolder.TextLine)
            ?.requestFocusAt(targetOffset.coerceIn(0, paragraph.text.length))
    }

    private fun refreshTailFrom(startIndex: Int) {
        val start = startIndex.coerceAtLeast(0)
        while (lineViews.size > paragraphs.size) removeLine(lineViews.lastIndex)
        for (index in start until paragraphs.size) {
            val paragraph = paragraphs[index]
            val shouldEditable = if (paragraph.isBlockLine()) false else isEditableLine(index, paragraph, focusedLineId)
            val holder = lineViews.getOrNull(index)
            when {
                holder == null -> insertLine(index, paragraph, shouldEditable)
                shouldEditable && holder !is LineViewHolder.TextLine -> replaceLine(index, forceEditable = true)
                !shouldEditable && holder is LineViewHolder.TextLine -> replaceLine(index, forceEditable = false)
                else -> bindLine(index)
            }
        }
        updateMeta()
        refreshAttachmentSelection()
    }

    private fun refreshActiveWindow(previousFocusedId: String?, currentFocusedId: String?) {
        val previousIndex = currentIndexOfParagraph(previousFocusedId).takeIf { it >= 0 }
        val currentIndex = currentIndexOfParagraph(currentFocusedId).takeIf { it >= 0 }
        val affected = linkedSetOf<Int>()
        previousIndex?.let { affected.addAll(activeWindowRange(it).toList()) }
        currentIndex?.let { affected.addAll(activeWindowRange(it).toList()) }
        affected.forEach { index ->
            val paragraph = paragraphs.getOrNull(index) ?: return@forEach
            if (paragraph.isBlockLine()) return@forEach
            val shouldEditable = isEditableLine(index, paragraph, currentFocusedId)
            val holder = lineViews.getOrNull(index)
            when {
                shouldEditable && holder !is LineViewHolder.TextLine -> replaceLine(index, forceEditable = true)
                !shouldEditable && holder is LineViewHolder.TextLine -> deactivateLine(index)
                else -> bindLine(index)
            }
        }
        updateMeta()
    }

    fun toggleCurrentTodo(): NoteDocument {
        val index = focusedIndex()
        val paragraph = paragraphs.getOrNull(index)?.withMigratedParagraphStyle() ?: return emit()
        if (paragraph.isBlockLine()) return emit()
        paragraphs[index] = if (paragraph.type == NoteParagraphType.TODO) paragraph.copy(type = NoteParagraphType.TEXT, checked = false) else paragraph.copy(type = NoteParagraphType.TODO, checked = false)
        focusedLineId = paragraphs[index].id
        bindLine(index)
        refreshActiveWindow(null, focusedLineId)
        return emit()
    }

    fun setParagraphStyle(style: NoteParagraphStyle): NoteDocument {
        when (style) {
            NoteParagraphStyle.BULLET -> return setListStyle(NoteListStyle.BULLET)
            NoteParagraphStyle.ORDERED -> return setListStyle(NoteListStyle.ORDERED)
            else -> Unit
        }
        val index = focusedIndex()
        val paragraph = paragraphs.getOrNull(index)?.withMigratedParagraphStyle() ?: return emit()
        if (paragraph.isBlockLine()) return emit()
        val cleanStyle = when (style) {
            NoteParagraphStyle.BULLET,
            NoteParagraphStyle.ORDERED -> NoteParagraphStyle.BODY
            else -> style
        }
        val nextListStyle = if (cleanStyle == NoteParagraphStyle.CODE || cleanStyle == NoteParagraphStyle.QUOTE) NoteListStyle.NONE else paragraph.effectiveListStyle()
        paragraphs[index] = paragraph.copy(style = cleanStyle, listStyle = nextListStyle)
        if (style == NoteParagraphStyle.CODE) ensurePlainLineAfter(index)
        focusedLineId = paragraphs[index].id
        refreshTailFrom(index)
        return emit()
    }

    fun setListStyle(style: NoteListStyle): NoteDocument {
        val index = focusedIndex()
        val paragraph = paragraphs.getOrNull(index)?.withMigratedParagraphStyle() ?: return emit()
        if (paragraph.isBlockLine()) return emit()
        val paragraphStyle = paragraph.effectiveParagraphStyle()
        val nextListStyle = if (paragraphStyle == NoteParagraphStyle.CODE || paragraphStyle == NoteParagraphStyle.QUOTE) NoteListStyle.NONE else style
        paragraphs[index] = paragraph.copy(style = paragraphStyle, listStyle = nextListStyle)
        focusedLineId = paragraphs[index].id
        refreshTailFrom(index)
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
        refreshTailFrom(insertAt)
        activateLine(insertAt + 1, 0)
        return emit()
    }

    fun insertDivider(): NoteDocument {
        val insertAt = if (paragraphs.isEmpty()) 0 else focusedIndex() + 1
        val divider = NoteParagraph(type = NoteParagraphType.DIVIDER)
        val nextLine = NoteParagraph()
        paragraphs.add(insertAt.coerceIn(0, paragraphs.size), divider)
        paragraphs.add((insertAt + 1).coerceIn(0, paragraphs.size), nextLine)
        focusedLineId = nextLine.id
        selectedAttachmentId = null
        refreshTailFrom(insertAt)
        activateLine(insertAt + 1, 0)
        return emit()
    }

    fun insertTable(): NoteDocument {
        val insertAt = if (paragraphs.isEmpty()) 0 else focusedIndex() + 1
        val tableParagraph = NoteParagraph(
            type = NoteParagraphType.TABLE,
            table = NoteTableData(columnCount = 2, headerRowCount = 1, cells = List(4) { "" }).normalized()
        )
        val nextLine = NoteParagraph()
        paragraphs.add(insertAt.coerceIn(0, paragraphs.size), tableParagraph)
        paragraphs.add((insertAt + 1).coerceIn(0, paragraphs.size), nextLine)
        selectedAttachmentId = tableParagraph.id
        activeTableCell = null
        focusedLineId = tableParagraph.id
        refreshTailFrom(insertAt)
        selectAttachment(insertAt)
        return emit()
    }

    fun hideKeyboard() {
        currentEditText()?.clearFocus()
        currentTableEditText()?.clearFocus()
        context.inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }

    fun dispose() {
        lineViews.forEach { it.dispose() }
        lineViews.clear()
    }

    private fun rebuildLines(preserveFocusId: String?) {
        largeDocumentMode = shouldUseLargeDocumentMode()
        val focusOffset = pendingSelectionOffset
        pendingSelectionOffset = null
        root.suppressLayout(true)
        try {
            lineViews.forEach { it.dispose() }
            lineViews.clear()
            while (root.childCount > 2) root.removeViewAt(2)
            paragraphs.forEachIndexed { index, paragraph ->
                val holder = buildHolder(index, paragraph, forceEditable = if (paragraph.isBlockLine()) false else isEditableLine(index, paragraph, preserveFocusId))
                lineViews += holder
                root.addView(holder.container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        } finally {
            root.suppressLayout(false)
        }
        updateMeta()
        val index = paragraphs.indexOfFirst { it.id == preserveFocusId }
        if (index >= 0) {
            val holder = lineViews.getOrNull(index)
            if (holder is LineViewHolder.TextLine) {
                val targetOffset = focusOffset?.coerceIn(0, holder.editText.text.length) ?: holder.editText.text.length
                holder.requestFocusAt(targetOffset)
            } else {
                selectAttachment(index)
            }
        }
    }

    private fun onLineTextChanged(index: Int, text: String) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        paragraphs[index] = paragraph.copy(text = text, spans = shiftSpansForText(paragraph.spans, text.length))
        focusedLineId = paragraph.id
        activeTableCell = null
        largeDocumentMode = shouldUseLargeDocumentMode()
        updateMeta()
        emit()
    }

    private fun isEditableLine(index: Int, paragraph: NoteParagraph, preserveFocusId: String?): Boolean {
        if (paragraph.isBlockLine()) return false
        val centerId = preserveFocusId ?: focusedLineId
        val centerIndex = when {
            centerId != null -> paragraphs.indexOfFirst { it.id == centerId }.takeIf { it >= 0 }
            else -> null
        } ?: paragraphs.indexOfFirst { !it.isBlockLine() }.takeIf { it >= 0 } ?: 0
        return index in activeWindowRange(centerIndex)
    }

    private fun activateReadOnlyLine(index: Int, offset: Int = Int.MAX_VALUE) {
        activateLine(index, offset)
    }

    private fun activateTableCell(index: Int, row: Int, column: Int) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        val table = paragraph.table?.normalized() ?: return
        clearActiveTableCell()
        currentEditText()?.clearFocus()
        selectedAttachmentId = null
        focusedLineId = paragraph.id
        activeTableCell = TableCellRef(
            paragraphId = paragraph.id,
            row = row.coerceIn(0, table.rowCount.coerceAtLeast(1) - 1),
            column = column.coerceIn(0, table.columnCount.coerceAtLeast(1) - 1)
        )
        refreshAttachmentSelection()
        (lineViews.getOrNull(index) as? LineViewHolder.TableLine)?.setActiveCell(activeTableCell!!.row, activeTableCell!!.column)
    }

    private fun clearActiveTableCell() {
        val active = activeTableCell ?: return
        val index = currentIndexOfParagraph(active.paragraphId)
        activeTableCell = null
        (lineViews.getOrNull(index) as? LineViewHolder.TableLine)?.clearActiveCell()
    }

    private fun updateTable(index: Int, table: NoteTableData) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        paragraphs[index] = paragraph.copy(table = table.normalized())
        largeDocumentMode = shouldUseLargeDocumentMode()
        updateMeta()
        emit()
    }

    private fun shouldUseLargeDocumentMode(): Boolean {
        if (paragraphs.size > LARGE_DOCUMENT_PARAGRAPH_THRESHOLD) return true
        var chars = 0
        for (paragraph in paragraphs) {
            chars += paragraph.text.length
            if (chars > LARGE_DOCUMENT_CHAR_THRESHOLD) return true
        }
        return false
    }

    private fun splitLine(index: Int, selectionStart: Int? = null, selectionEnd: Int? = null): Boolean {
        val holder = lineViews.getOrNull(index) as? LineViewHolder.TextLine ?: return false
        val paragraph = paragraphs.getOrNull(index)?.withMigratedParagraphStyle() ?: return false
        val text = holder.editText.text.toString()
        if (paragraph.effectiveParagraphStyle() == NoteParagraphStyle.CODE) {
            return insertCodeLineBreak(index, selectionStart, selectionEnd)
        }
        val start = minOf(selectionStart ?: holder.editText.selectionStart, selectionEnd ?: holder.editText.selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart ?: holder.editText.selectionStart, selectionEnd ?: holder.editText.selectionEnd).coerceIn(0, text.length)
        if (start == end) {
            when (findInlineShortcutToken(text, start)) {
                InlineShortcutToken.TIME -> {
                    return replaceInlineShortcut(index, paragraph, start, "/t", currentTimeShortcutValue())
                }
                InlineShortcutToken.DATE -> {
                    return replaceInlineShortcut(index, paragraph, start, "/d", currentDateShortcutValue())
                }
                null -> Unit
            }
        }
        when (text.trim()) {
            "/i" -> {
                paragraphs[index] = paragraph.copy(text = "", spans = emptyList())
                bindLine(index)
                emit()
                onImageShortcut()
                return true
            }
            "/f" -> {
                paragraphs[index] = paragraph.copy(text = "", spans = emptyList())
                bindLine(index)
                emit()
                onFileShortcut()
                return true
            }
        }
        val before = text.substring(0, start)
        val after = text.substring(end)
        if (start == 0 && end == 0 && text.isNotEmpty()) {
            val blankLine = NoteParagraph()
            paragraphs.add(index, blankLine)
            val previousFocusedId = focusedLineId
            focusedLineId = blankLine.id
            insertLine(index, blankLine, active = true)
            refreshTailFrom(index + 1)
            refreshActiveWindow(previousFocusedId, focusedLineId)
            (lineViews.getOrNull(index) as? LineViewHolder.TextLine)?.requestFocusAt(0)
            emit()
            return true
        }
        if (paragraph.type == NoteParagraphType.TODO && before.isBlank() && after.isBlank()) {
            paragraphs[index] = paragraph.copy(text = "", type = NoteParagraphType.TEXT, checked = false, spans = emptyList())
            bindLine(index)
            emit()
            return true
        }
        if (paragraph.effectiveListStyle() != NoteListStyle.NONE && before.isBlank() && after.isBlank()) {
            paragraphs[index] = paragraph.copy(style = paragraph.effectiveParagraphStyle(), listStyle = NoteListStyle.NONE)
            bindLine(index)
            emit()
            return true
        }
        val currentParagraphStyle = paragraph.effectiveParagraphStyle()
        val currentListStyle = paragraph.effectiveListStyle()
        paragraphs[index] = paragraph.copy(style = currentParagraphStyle, text = before, spans = paragraph.spans.filter { it.end <= before.length })
        val newLineListStyle = if (currentListStyle != NoteListStyle.NONE && before.isNotBlank()) currentListStyle else NoteListStyle.NONE
        val newLine = NoteParagraph(text = after, listStyle = newLineListStyle)
        paragraphs.add(index + 1, newLine)
        val previousFocusedId = focusedLineId
        focusedLineId = newLine.id
        bindLine(index)
        insertLine(index + 1, newLine, active = true)
        refreshTailFrom(index + 2)
        refreshActiveWindow(previousFocusedId, focusedLineId)
        (lineViews.getOrNull(index + 1) as? LineViewHolder.TextLine)?.requestFocusAt(0)
        emit()
        return true
    }

    private fun replaceInlineShortcut(
        index: Int,
        paragraph: NoteParagraph,
        cursor: Int,
        token: String,
        replacement: String
    ): Boolean {
        val text = paragraph.text
        val tokenStart = (cursor - token.length).coerceAtLeast(0)
        if (text.substring(tokenStart, cursor) != token) return false
        val beforeRaw = text.substring(0, tokenStart)
        val afterRaw = text.substring(cursor)
        val needsLeadingSpace = beforeRaw.isNotEmpty() && !beforeRaw.last().isWhitespace() && beforeRaw.last() != '/'
        val needsTrailingSpace = afterRaw.isNotEmpty() && !afterRaw.first().isWhitespace()
        val before = if (needsLeadingSpace) "$beforeRaw " else beforeRaw
        val after = if (needsTrailingSpace) " $afterRaw" else afterRaw
        val updated = before + replacement + after
        val newCursor = before.length + replacement.length
        paragraphs[index] = paragraph.copy(text = updated, spans = shiftSpansForText(paragraph.spans, updated.length))
        bindLine(index)
        (lineViews.getOrNull(index) as? LineViewHolder.TextLine)?.requestFocusAt(newCursor)
        emit()
        return true
    }

    private fun findInlineShortcutToken(text: String, cursor: Int): InlineShortcutToken? {
        if (cursor < 2) return null
        return when {
            matchesInlineShortcutToken(text, cursor, "/t") -> InlineShortcutToken.TIME
            matchesInlineShortcutToken(text, cursor, "/d") -> InlineShortcutToken.DATE
            else -> null
        }
    }

    private fun matchesInlineShortcutToken(text: String, cursor: Int, token: String): Boolean {
        val tokenStart = cursor - token.length
        if (tokenStart < 0) return false
        if (text.substring(tokenStart, cursor) != token) return false
        if (cursor < text.length && !text[cursor].isWhitespace()) return false
        if (tokenStart == 0) return true
        val previous = text[tokenStart - 1]
        return previous.isWhitespace() || previous.isDigit() || previous == ':' || previous == ')' || previous == '）'
    }

    private fun currentTimeShortcutValue(): String {
        return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun currentDateShortcutValue(): String {
        return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }

    private fun insertCodeLineBreak(index: Int, selectionStart: Int?, selectionEnd: Int?): Boolean {
        val holder = lineViews.getOrNull(index) as? LineViewHolder.TextLine ?: return false
        val paragraph = paragraphs.getOrNull(index) ?: return false
        val text = holder.editText.text.toString()
        val start = minOf(selectionStart ?: holder.editText.selectionStart, selectionEnd ?: holder.editText.selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart ?: holder.editText.selectionStart, selectionEnd ?: holder.editText.selectionEnd).coerceIn(0, text.length)
        val updated = text.substring(0, start) + "\n" + text.substring(end)
        paragraphs[index] = paragraph.copy(text = updated, spans = emptyList())
        ensurePlainLineAfter(index)
        bindLine(index)
        refreshTailFrom(index + 1)
        (lineViews.getOrNull(index) as? LineViewHolder.TextLine)?.requestFocusAt(start + 1)
        emit()
        return true
    }

    private fun insertMultilineText(index: Int, rawText: String, selectionStart: Int, selectionEnd: Int): Boolean {
        val holder = lineViews.getOrNull(index) as? LineViewHolder.TextLine ?: return false
        val paragraph = paragraphs.getOrNull(index) ?: return false
        val text = holder.editText.text.toString()
        if (paragraph.effectiveParagraphStyle() == NoteParagraphStyle.CODE) {
            return insertMultilineIntoCode(index, rawText, selectionStart, selectionEnd)
        }
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val lines = normalizeLineBreaks(rawText).split('\n')
        if (lines.size <= 1) return false

        val before = text.substring(0, start)
        val after = text.substring(end)
        val replacement = mutableListOf<NoteParagraph>()
        val lastLineText = lines.last() + after
        replacement += paragraph.copy(text = before + lines.first(), spans = emptyList())
        lines.drop(1).dropLast(1).forEach { line ->
            replacement += NoteParagraph(text = line)
        }
        replacement += NoteParagraph(text = lastLineText)

        paragraphs.removeAt(index)
        paragraphs.addAll(index, replacement)
        val focusId = replacement.last().id
        val previousFocusedId = focusedLineId
        focusedLineId = focusId
        selectedAttachmentId = null
        removeLine(index)
        replacement.forEachIndexed { offset, line ->
            insertLine(index + offset, line, active = line.id == focusId)
        }
        refreshTailFrom(index + replacement.size)
        refreshActiveWindow(previousFocusedId, focusedLineId)
        (lineViews.getOrNull(index + replacement.lastIndex) as? LineViewHolder.TextLine)?.requestFocusAt(lines.last().length)
        emit()
        return true
    }

    private fun insertMultilineIntoCode(index: Int, rawText: String, selectionStart: Int, selectionEnd: Int): Boolean {
        val holder = lineViews.getOrNull(index) as? LineViewHolder.TextLine ?: return false
        val paragraph = paragraphs.getOrNull(index) ?: return false
        val text = holder.editText.text.toString()
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val insertText = normalizeLineBreaks(rawText)
        val updated = text.substring(0, start) + insertText + text.substring(end)
        paragraphs[index] = paragraph.copy(text = updated, spans = emptyList())
        ensurePlainLineAfter(index)
        bindLine(index)
        refreshTailFrom(index + 1)
        (lineViews.getOrNull(index) as? LineViewHolder.TextLine)?.requestFocusAt(start + insertText.length)
        emit()
        return true
    }

    private fun normalizeCommittedMultiline(index: Int, selectionStart: Int, selectionEnd: Int): Boolean {
        val holder = lineViews.getOrNull(index) as? LineViewHolder.TextLine ?: return false
        val paragraph = paragraphs.getOrNull(index) ?: return false
        val text = holder.editText.text.toString()
        if (!text.contains('\n') && !text.contains('\r')) return false
        if (paragraph.effectiveParagraphStyle() == NoteParagraphStyle.CODE) {
            val normalized = normalizeLineBreaks(text)
            paragraphs[index] = paragraph.copy(text = normalized, spans = emptyList())
            ensurePlainLineAfter(index)
            val offset = selectionStart.coerceIn(0, normalized.length)
            bindLine(index)
            refreshTailFrom(index + 1)
            (lineViews.getOrNull(index) as? LineViewHolder.TextLine)?.requestFocusAt(offset)
            emit()
            return true
        }
        val normalized = normalizeLineBreaks(text)
        val lines = normalized.split('\n')
        if (lines.size <= 1) return false
        val beforeCursor = normalizeLineBreaks(text.substring(0, selectionStart.coerceIn(0, text.length)))
        val focusOffset = beforeCursor.substringAfterLast('\n').length
        val focusLineOffset = beforeCursor.count { it == '\n' }.coerceIn(0, lines.lastIndex)
        val replacement = lines.mapIndexed { lineIndex, line ->
            if (lineIndex == 0) paragraph.copy(text = line, spans = emptyList()) else NoteParagraph(text = line)
        }
        paragraphs.removeAt(index)
        paragraphs.addAll(index, replacement)
        val focusId = replacement[focusLineOffset].id
        val previousFocusedId = focusedLineId
        focusedLineId = focusId
        selectedAttachmentId = null
        removeLine(index)
        replacement.forEachIndexed { offset, line ->
            insertLine(index + offset, line, active = line.id == focusId)
        }
        refreshTailFrom(index + replacement.size)
        refreshActiveWindow(previousFocusedId, focusedLineId)
        (lineViews.getOrNull(index + focusLineOffset) as? LineViewHolder.TextLine)?.requestFocusAt(focusOffset)
        emit()
        return true
    }

    private fun ensurePlainLineAfter(index: Int) {
        val next = paragraphs.getOrNull(index + 1)
        val hasExitLine = next != null &&
            next.type == NoteParagraphType.TEXT &&
            next.effectiveParagraphStyle() == NoteParagraphStyle.BODY &&
            next.effectiveListStyle() == NoteListStyle.NONE &&
            next.text.isBlank() &&
            next.spans.isEmpty()
        if (!hasExitLine) paragraphs.add(index + 1, NoteParagraph())
    }

    private fun handleBackspaceAtStart(index: Int): Boolean {
        val paragraph = paragraphs.getOrNull(index) ?: return false
        if (paragraph.type == NoteParagraphType.TODO) {
            paragraphs[index] = paragraph.copy(type = NoteParagraphType.TEXT, checked = false)
            bindLine(index)
            (lineViews.getOrNull(index) as? LineViewHolder.TextLine)?.requestFocusAt(0)
            emit()
            return true
        }
        val migratedCurrent = paragraph.withMigratedParagraphStyle()
        if (migratedCurrent.effectiveListStyle() != NoteListStyle.NONE) {
            paragraphs[index] = migratedCurrent.copy(listStyle = NoteListStyle.NONE)
            bindLine(index)
            (lineViews.getOrNull(index) as? LineViewHolder.TextLine)?.requestFocusAt(0)
            emit()
            return true
        }
        if (paragraph.isBlockLine()) {
            if (selectedAttachmentId == paragraph.id) deleteAttachmentLine(index) else selectAttachment(index)
            return true
        }
        if (index <= 0) return false
        val previous = paragraphs[index - 1]
        if (previous.isBlockLine()) {
            selectAttachment(index - 1)
            return true
        }
        val current = paragraphs[index]
        val previousLength = previous.text.length
        paragraphs[index - 1] = previous.withMigratedParagraphStyle().copy(text = previous.text + current.text)
        paragraphs.removeAt(index)
        focusedLineId = previous.id
        bindLine(index - 1)
        removeLine(index)
        refreshTailFrom(index)
        refreshActiveWindow(current.id, focusedLineId)
        (lineViews.getOrNull(index - 1) as? LineViewHolder.TextLine)?.requestFocusAt(previousLength)
        emit()
        return true
    }

    private fun toggleChecked(index: Int, checked: Boolean) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        paragraphs[index] = paragraph.copy(type = NoteParagraphType.TODO, checked = checked)
        bindLine(index)
        emit()
    }

    private fun selectAttachment(index: Int) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        if (!paragraph.isBlockLine()) return
        currentEditText()?.clearFocus()
        clearActiveTableCell()
        selectedAttachmentId = paragraph.id
        focusedLineId = paragraph.id
        lineViews.getOrNull(index)?.container?.requestFocus()
        refreshAttachmentSelection()
    }

    private fun deleteAttachmentLine(index: Int) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        NoteAttachmentStore.delete(context, paragraph.attachmentPath)
        if (activeTableCell?.paragraphId == paragraph.id) activeTableCell = null
        paragraphs.removeAt(index)
        if (paragraphs.isEmpty() || paragraphs.none { !it.isBlockLine() }) paragraphs += NoteParagraph()
        val focusIndex = index.coerceAtMost(paragraphs.lastIndex)
        val focus = paragraphs.getOrNull(focusIndex)
        selectedAttachmentId = null
        focusedLineId = focus?.id
        removeLine(index)
        refreshTailFrom(index)
        refreshActiveWindow(paragraph.id, focusedLineId)
        if (focus?.isBlockLine() == false) (lineViews.getOrNull(focusIndex) as? LineViewHolder.TextLine)?.requestFocusAt(0)
        emit()
    }

    private fun refreshAttachmentSelection() {
        lineViews.forEachIndexed { index, holder ->
            if (holder is LineViewHolder.AttachmentLine) holder.setSelected(paragraphs.getOrNull(index)?.id == selectedAttachmentId)
            if (holder is LineViewHolder.TableLine) holder.setSelected(paragraphs.getOrNull(index)?.id == selectedAttachmentId)
        }
    }

    private fun clearAttachmentSelection() {
        if (selectedAttachmentId == null) return
        selectedAttachmentId = null
        refreshAttachmentSelection()
    }

    private fun emit(): NoteDocument {
        val document = NoteDocument(paragraphs.toList())
        lastBoundTitle = titleEdit.text.toString()
        lastBoundDocument = document
        onDocumentChange(document)
        return document
    }

    private fun updateMeta() {
        metaView.text = if (largeDocumentMode) {
            "${java.time.LocalDate.now()} | 大文档模式 | ${paragraphs.size} 段 | 默认笔记本"
        } else {
            "${java.time.LocalDate.now()} | ${paragraphs.sumOf { if (it.isBlockLine()) 0 else it.text.length }} 字 | 默认笔记本"
        }
    }

    private fun orderedIndexFor(index: Int): Int {
        var count = 0
        for (i in 0..index.coerceAtMost(paragraphs.lastIndex)) {
            val paragraph = paragraphs[i]
            if (paragraph.effectiveListStyle() == NoteListStyle.ORDERED) count++
        }
        return count.coerceAtLeast(1)
    }

    private fun focusedIndex(): Int {
        activeTableCell?.let {
            val index = paragraphs.indexOfFirst { paragraph -> paragraph.id == it.paragraphId }
            if (index >= 0) return index
        }
        val byId = paragraphs.indexOfFirst { it.id == focusedLineId }
        if (byId >= 0) return byId
        val byFocus = lineViews.indexOfFirst { it is LineViewHolder.TextLine && it.editText.hasFocus() }
        return byFocus.takeIf { it >= 0 } ?: 0
    }

    private fun currentEditText(): EditText? = (lineViews.firstOrNull { it is LineViewHolder.TextLine && it.editText.hasFocus() } as? LineViewHolder.TextLine)?.editText

    private fun currentTableEditText(): EditText? = (lineViews.firstOrNull { it is LineViewHolder.TableLine && it.activeEditText?.hasFocus() == true } as? LineViewHolder.TableLine)?.activeEditText

    private fun focusLastLine() {
        if (paragraphs.isEmpty()) {
            paragraphs += NoteParagraph()
            rebuildLines(paragraphs.first().id)
        }
        val lastIndex = paragraphs.lastIndex
        if (largeDocumentMode && lineViews.getOrNull(lastIndex) !is LineViewHolder.TextLine) {
            activateReadOnlyLine(lastIndex)
        } else {
            (lineViews.lastOrNull() as? LineViewHolder.TextLine)?.requestFocusAtEnd()
        }
    }

    private fun activeWindowRange(centerIndex: Int): IntRange {
        val start = (centerIndex - ACTIVE_WINDOW_RADIUS).coerceAtLeast(0)
        val end = (centerIndex + ACTIVE_WINDOW_RADIUS).coerceAtMost(paragraphs.lastIndex)
        return start..end
    }

    companion object {
        private const val ACTIVE_WINDOW_RADIUS = 2
        private const val LARGE_DOCUMENT_PARAGRAPH_THRESHOLD = 250
        private const val LARGE_DOCUMENT_CHAR_THRESHOLD = 15_000
    }
}

private class NoteLineEditText(context: Context) : EditText(context) {
    private var onEnter: (Int, Int) -> Boolean = { _, _ -> false }
    private var onBackspaceAtStart: () -> Boolean = { false }
    private var onMultilinePaste: (String, Int, Int) -> Boolean = { _, _, _ -> false }
    private var onCommittedMultiline: (Int, Int) -> Boolean = { _, _ -> false }
    private var enterPostScheduled = false
    private var backspacePostScheduled = false
    private var pasteConsumedAt = 0L

    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        ViewCompat.setOnReceiveContentListener(this, arrayOf("text/*")) { _, payload ->
            val text = payload.clip.toPlainText(context)
            if (text.containsLineBreak()) {
                val start = selectionStart
                val end = selectionEnd
                pasteConsumedAt = SystemClock.uptimeMillis()
                post { onMultilinePaste(text, start, end) }
                null
            } else {
                payload
            }
        }
    }

    fun setEditorCallbacks(
        onEnter: (Int, Int) -> Boolean,
        onBackspaceAtStart: () -> Boolean,
        onMultilinePaste: (String, Int, Int) -> Boolean,
        onCommittedMultiline: (Int, Int) -> Boolean
    ) {
        this.onEnter = onEnter
        this.onBackspaceAtStart = onBackspaceAtStart
        this.onMultilinePaste = onMultilinePaste
        this.onCommittedMultiline = onCommittedMultiline
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        return object : InputConnectionWrapper(base, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val value = text?.toString().orEmpty()
                if (value == "\n") {
                    postEnter(selectionStart, selectionEnd)
                    return true
                }
                val result = super.commitText(text, newCursorPosition)
                if (value.containsLineBreak()) {
                    val start = selectionStart
                    val end = selectionEnd
                    post { onCommittedMultiline(start, end) }
                }
                return result
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> {
                            postEnter(selectionStart, selectionEnd)
                            return true
                        }
                        KeyEvent.KEYCODE_DEL -> {
                            if (isAtSelectionStart()) {
                                postBackspaceAtStart()
                                return true
                            }
                        }
                    }
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0 && isAtSelectionStart()) {
                    postBackspaceAtStart()
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0 && isAtSelectionStart()) {
                    postBackspaceAtStart()
                    return true
                }
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            if (SystemClock.uptimeMillis() - pasteConsumedAt < PASTE_DEDUPE_WINDOW_MS) return true
            val text = context.getSystemService(ClipboardManager::class.java)
                ?.primaryClip
                ?.toPlainText(context)
                .orEmpty()
            if (text.containsLineBreak()) {
                val start = selectionStart
                val end = selectionEnd
                pasteConsumedAt = SystemClock.uptimeMillis()
                post { onMultilinePaste(text, start, end) }
                return true
            }
        }
        return super.onTextContextMenuItem(id)
    }

    private fun postEnter(start: Int, end: Int) {
        if (enterPostScheduled) return
        enterPostScheduled = true
        post {
            enterPostScheduled = false
            onEnter(start, end)
        }
    }

    private fun postBackspaceAtStart() {
        if (backspacePostScheduled) return
        backspacePostScheduled = true
        post {
            backspacePostScheduled = false
            onBackspaceAtStart()
        }
    }

    private fun isAtSelectionStart(): Boolean = selectionStart == 0 && selectionEnd == 0

    companion object {
        private const val PASTE_DEDUPE_WINDOW_MS = 350L
    }
}

private sealed class LineViewHolder {
    abstract val container: View
    abstract fun bind(paragraph: NoteParagraph)
    abstract fun applyColors(colors: NativeNoteEditorColors)
    abstract fun dispose()

    class ReadOnlyTextLine(
        context: Context,
        private val orderedIndexProvider: () -> Int,
        private val onClick: (Int) -> Unit,
        private val onCheckedChanged: (Boolean) -> Unit
    ) : LineViewHolder() {
        override val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 29.dp
            isClickable = true
            setOnClickListener { onClick(textView.text.length) }
        }
        private val checkBox = CheckBox(context).apply { minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0; setPadding(0, 0, 8.dp, 0) }
        private val prefixView = TextView(context).apply {
            textSize = 17f
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, 2.dp, 8.dp, 0)
        }
        private val textView = TextView(context).apply {
            includeFontPadding = false
            minHeight = 29.dp
            setPadding(0, 2.dp, 0, 2.dp)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textSize = 17f
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    onClick(getOffsetForPosition(event.x, event.y))
                    true
                } else {
                    false
                }
            }
        }
        private var colors = NativeNoteEditorColors(0xff111111.toInt(), 0xff999999.toInt(), 0xff777777.toInt(), 0xff3f6db5.toInt(), 0xff999999.toInt(), 0xffeef2f8.toInt())
        private var paragraph: NoteParagraph? = null

        init {
            container.addView(checkBox, LinearLayout.LayoutParams(32.dp, 32.dp))
            container.addView(prefixView, LinearLayout.LayoutParams(32.dp, LinearLayout.LayoutParams.WRAP_CONTENT))
            container.addView(textView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            checkBox.setOnClickListener { onCheckedChanged(checkBox.isChecked) }
        }

        override fun bind(paragraph: NoteParagraph) {
            this.paragraph = paragraph
            checkBox.visibility = if (paragraph.type == NoteParagraphType.TODO) View.VISIBLE else View.GONE
            checkBox.isChecked = paragraph.checked
            bindPrefix(paragraph)
            textView.text = buildReadOnlyText(paragraph)
            applyTextAppearance(paragraph)
        }

        private fun bindPrefix(paragraph: NoteParagraph) {
            val paragraphStyle = paragraph.effectiveParagraphStyle()
            val listStyle = paragraph.effectiveListStyle()
            prefixView.visibility = if (paragraphStyle == NoteParagraphStyle.QUOTE || listStyle != NoteListStyle.NONE) View.VISIBLE else View.GONE
            val params = prefixView.layoutParams as LinearLayout.LayoutParams
            if (paragraphStyle == NoteParagraphStyle.QUOTE) {
                prefixView.text = ""
                prefixView.background = roundedDrawable(colors.accentColor, 1.dp)
                prefixView.setPadding(0, 0, 0, 0)
                params.width = 3.dp
                params.height = LinearLayout.LayoutParams.MATCH_PARENT
                params.setMargins(14.dp, 0, 15.dp, 0)
            } else {
                prefixView.background = null
                prefixView.setPadding(0, 2.dp, 8.dp, 0)
                params.width = 32.dp
                params.height = LinearLayout.LayoutParams.WRAP_CONTENT
                params.setMargins(0, 0, 0, 0)
                prefixView.text = when (listStyle) {
                    NoteListStyle.BULLET -> "•"
                    NoteListStyle.ORDERED -> "${orderedIndexProvider()}."
                    else -> ""
                }
            }
            prefixView.layoutParams = params
        }

        override fun applyColors(colors: NativeNoteEditorColors) {
            this.colors = colors
            textView.setTextColor(colors.textColor)
            prefixView.setTextColor(colors.accentColor)
            checkBox.buttonTintList = ColorStateList.valueOf(colors.checkboxTint)
        }

        override fun dispose() = Unit

        private fun applyTextAppearance(paragraph: NoteParagraph) {
            val paragraphStyle = paragraph.effectiveParagraphStyle()
            val textSize = paragraphStyle.textSizeSp()
            textView.textSize = textSize
            textView.typeface = when {
                paragraphStyle == NoteParagraphStyle.CODE -> Typeface.MONOSPACE
                paragraphStyle.isHeadingStyle() -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
            textView.background = when (paragraphStyle) {
                NoteParagraphStyle.CODE -> roundedDrawable(0x11_000000, 8.dp)
                else -> null
            }
            if (paragraphStyle == NoteParagraphStyle.CODE) {
                textView.setSingleLine(false)
                textView.setHorizontallyScrolling(true)
            } else {
                textView.setSingleLine(false)
                textView.setHorizontallyScrolling(false)
            }
            textView.setPadding(if (paragraphStyle == NoteParagraphStyle.CODE || paragraphStyle == NoteParagraphStyle.QUOTE) 8.dp else 0, 2.dp, 0, 2.dp)
            textView.minHeight = (textSize + 12).toInt().dp
        }
    }

    class TextLine(
        context: Context,
        private val orderedIndexProvider: () -> Int,
        private val onTextChanged: (String, Int, Int) -> Unit,
        private val onFocus: () -> Unit,
        private val onEnter: (Int, Int) -> Boolean,
        private val onBackspaceAtStart: () -> Boolean,
        private val onMultilinePaste: (String, Int, Int) -> Boolean,
        private val onCommittedMultiline: (Int, Int) -> Boolean,
        private val onCheckedChanged: (Boolean) -> Unit
    ) : LineViewHolder() {
        override val container = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = 29.dp }
        private val checkBox = CheckBox(context).apply { minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0; setPadding(0, 0, 8.dp, 0) }
        private val prefixView = TextView(context).apply {
            textSize = 17f
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, 2.dp, 8.dp, 0)
        }
        val editText = NoteLineEditText(context).apply {
            background = null
            includeFontPadding = false
            minHeight = 29.dp
            setPadding(0, 2.dp, 0, 2.dp)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textSize = 17f
            setSingleLine(false)
            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            movementMethod = ArrowKeyMovementMethod.getInstance()
        }
        private var internalUpdate = false
        private var watcher: TextWatcher? = null
        private var colors = NativeNoteEditorColors(0xff111111.toInt(), 0xff999999.toInt(), 0xff777777.toInt(), 0xff3f6db5.toInt(), 0xff999999.toInt(), 0xffeef2f8.toInt())

        init {
            container.addView(checkBox, LinearLayout.LayoutParams(32.dp, 32.dp))
            container.addView(prefixView, LinearLayout.LayoutParams(32.dp, LinearLayout.LayoutParams.WRAP_CONTENT))
            container.addView(editText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            checkBox.setOnClickListener { onCheckedChanged(checkBox.isChecked) }
            editText.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) onFocus() }
            editText.setEditorCallbacks(
                onEnter = { start, end -> onEnter(start, end) },
                onBackspaceAtStart = { onBackspaceAtStart() },
                onMultilinePaste = { text, start, end -> onMultilinePaste(text, start, end) },
                onCommittedMultiline = { start, end -> onCommittedMultiline(start, end) }
            )
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_ENTER -> onEnter(editText.selectionStart, editText.selectionEnd)
                    KeyEvent.KEYCODE_DEL -> if ((editText.selectionStart == 0 && editText.selectionEnd == 0) || editText.text.isEmpty()) onBackspaceAtStart() else false
                    else -> false
                }
            }
            watcher = editText.doAfterTextChanged { if (!internalUpdate) onTextChanged(editText.text.toString(), editText.selectionStart, editText.selectionEnd) }
        }

        override fun bind(paragraph: NoteParagraph) {
            checkBox.visibility = if (paragraph.type == NoteParagraphType.TODO) View.VISIBLE else View.GONE
            checkBox.isChecked = paragraph.checked
            bindPrefix(paragraph)
            setTextAndSpans(paragraph)
        }

        private fun bindPrefix(paragraph: NoteParagraph) {
            val paragraphStyle = paragraph.effectiveParagraphStyle()
            val listStyle = paragraph.effectiveListStyle()
            prefixView.visibility = if (paragraphStyle == NoteParagraphStyle.QUOTE || listStyle != NoteListStyle.NONE) View.VISIBLE else View.GONE
            val params = prefixView.layoutParams as LinearLayout.LayoutParams
            if (paragraphStyle == NoteParagraphStyle.QUOTE) {
                prefixView.text = ""
                prefixView.background = roundedDrawable(colors.accentColor, 1.dp)
                prefixView.setPadding(0, 0, 0, 0)
                params.width = 3.dp
                params.height = LinearLayout.LayoutParams.MATCH_PARENT
                params.setMargins(14.dp, 0, 15.dp, 0)
            } else {
                prefixView.background = null
                prefixView.setPadding(0, 2.dp, 8.dp, 0)
                params.width = 32.dp
                params.height = LinearLayout.LayoutParams.WRAP_CONTENT
                params.setMargins(0, 0, 0, 0)
                prefixView.text = when (listStyle) {
                    NoteListStyle.BULLET -> "•"
                    NoteListStyle.ORDERED -> "${orderedIndexProvider()}."
                    else -> ""
                }
            }
            prefixView.layoutParams = params
        }

        fun setTextAndSpans(paragraph: NoteParagraph) {
            internalUpdate = true
            editText.text = buildEditable(paragraph)
            editText.setSelection(editText.text.length)
            internalUpdate = false
            applyTextAppearance(paragraph)
        }

        override fun applyColors(colors: NativeNoteEditorColors) {
            this.colors = colors
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
            val paragraphStyle = paragraph.effectiveParagraphStyle()
            val textSize = paragraphStyle.textSizeSp()
            editText.textSize = textSize
            editText.typeface = when {
                paragraphStyle == NoteParagraphStyle.CODE -> Typeface.MONOSPACE
                paragraphStyle.isHeadingStyle() -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
            editText.inputType = if (paragraphStyle == NoteParagraphStyle.CODE) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            editText.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            editText.background = when (paragraphStyle) {
                NoteParagraphStyle.CODE -> roundedDrawable(0x11_000000, 8.dp)
                else -> null
            }
            if (paragraphStyle == NoteParagraphStyle.CODE) {
                editText.setSingleLine(false)
                editText.setHorizontallyScrolling(true)
            } else {
                editText.setSingleLine(false)
                editText.setHorizontallyScrolling(false)
            }
            editText.setPadding(if (paragraphStyle == NoteParagraphStyle.CODE || paragraphStyle == NoteParagraphStyle.QUOTE) 8.dp else 0, 2.dp, 0, 2.dp)
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
        private val onDelete: () -> Unit,
        private val onOpen: () -> Unit
    ) : LineViewHolder() {
        override val container = FrameLayout(context).apply { setPadding(0, 8.dp, 0, 8.dp); isFocusable = true; isFocusableInTouchMode = true }
        private var selected = false
        private var colors = NativeNoteEditorColors(0, 0, 0, 0, 0, 0)
        private var paragraph: NoteParagraph? = null
        private var overlayView: View? = null

        init {
            container.setOnClickListener { handleClick() }
            container.setOnLongClickListener { handleLongClick() }
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
            when (data.type) {
                NoteParagraphType.IMAGE -> renderImage(data)
                NoteParagraphType.FILE -> renderFile(data)
                NoteParagraphType.DIVIDER -> renderDivider()
                else -> renderFile(data)
            }
            renderSelection()
        }

        private fun renderDivider() {
            val line = View(context).apply {
                background = roundedDrawable(0x33666666, 1.dp)
            }
            val contentFrame = FrameLayout(context).apply { setPadding(0, 14.dp, 0, 14.dp) }
            contentFrame.addView(line, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1.dp, Gravity.CENTER))
            addOverlay(contentFrame, 2.dp)
            container.addView(contentFrame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 30.dp))
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
                setOnClickListener { handleClick() }
                setOnLongClickListener { handleLongClick() }
                runCatching { setImageBitmap(BitmapFactory.decodeFile(imageFile.absolutePath)) }
            }
            contentFrame.setOnClickListener { handleClick() }
            contentFrame.setOnLongClickListener { handleLongClick() }
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
                setOnClickListener { handleClick() }
                setOnLongClickListener { handleLongClick() }
            }
            contentFrame.setOnClickListener { handleClick() }
            contentFrame.setOnLongClickListener { handleLongClick() }
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
                setOnClickListener { handleClick() }
                setOnLongClickListener { handleLongClick() }
            }
            contentFrame.addView(overlayView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        private fun renderSelection() {
            overlayView?.visibility = if (selected) View.VISIBLE else View.GONE
        }

        private fun handleClick() {
            if (paragraph?.type == NoteParagraphType.DIVIDER) onSelected() else onOpen()
        }

        private fun handleLongClick(): Boolean {
            onSelected()
            return true
        }
    }

    class TableLine(
        private val context: Context,
        private val onSelected: () -> Unit,
        private val onDelete: () -> Unit,
        private val onActivateCell: (row: Int, column: Int) -> Unit,
        private val onTableChanged: (NoteTableData) -> Unit
    ) : LineViewHolder() {
        private val panelPadding = 24.dp
        private val rowHeaderWidth = panelPadding
        private val columnHeaderHeight = panelPadding
        override val container = FrameLayout(context).apply {
            setPadding(0, 8.dp, 0, 8.dp)
            isFocusable = true
            isFocusableInTouchMode = true
            clipChildren = false
            clipToPadding = false
        }
        private val tablePanel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, panelPadding, panelPadding)
            clipChildren = false
            clipToPadding = false
        }
        private val columnHeaderSpacer = View(context)
        private val rowHeaderColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val rowHeaderRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val scroll = InterceptingHorizontalScrollView(context)
        private val tableContentColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val columnHeaderRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        private val tableShell = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(1.dp, 1.dp, 1.dp, 1.dp)
        }
        private val gridContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val selectionOverlay = FrameLayout(context)
        private var colors = NativeNoteEditorColors(0, 0, 0, 0, 0, 0)
        private var paragraph: NoteParagraph? = null
        private var selected = false
        private var selectedCell: Pair<Int, Int>? = null
        private var editingCell: Pair<Int, Int>? = null
        private var selection: TableSelectionRef? = null
        private val cellBounds = mutableMapOf<Pair<Int, Int>, android.graphics.Rect>()
        private val rowViews = mutableListOf<LinearLayout>()
        private var rowHeaderHeights = emptyList<Int>()
        var activeEditText: EditText? = null
            private set

        init {
            scroll.isFillViewport = false
            scroll.clipToPadding = false
            scroll.clipChildren = false
            tableShell.addView(gridContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            tableShell.addView(selectionOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            tableContentColumn.addView(columnHeaderRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, columnHeaderHeight))
            tableContentColumn.addView(tableShell, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            scroll.addView(tableContentColumn, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            rowHeaderColumn.addView(columnHeaderSpacer, LinearLayout.LayoutParams(rowHeaderWidth, 0))
            rowHeaderColumn.addView(rowHeaderRows, LinearLayout.LayoutParams(rowHeaderWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
            tablePanel.addView(rowHeaderColumn, LinearLayout.LayoutParams(rowHeaderWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
            tablePanel.addView(scroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(tablePanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            container.setOnClickListener { onSelected() }
            container.setOnLongClickListener { onSelected(); true }
            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) { onDelete(); true } else false
            }
        }

        override fun bind(paragraph: NoteParagraph) {
            this.paragraph = paragraph.copy(table = paragraph.table?.normalized())
            render()
        }

        override fun applyColors(colors: NativeNoteEditorColors) {
            this.colors = colors
            renderSelection()
            render()
        }

        override fun dispose() {
            activeEditText?.clearFocus()
            activeEditText = null
        }

        fun setSelected(selected: Boolean) {
            val previousHeadersVisible = headersVisible()
            this.selected = selected
            if (!selected) {
                selection = null
                selectedCell = null
                editingCell = null
            } else if (selectedCell == null) {
                paragraph?.table?.normalized()?.let { table ->
                    selectedCell = 0 to 0
                }
            }
            if (previousHeadersVisible != headersVisible()) {
                render()
            } else {
                renderSelection()
                container.post { renderMarkers() }
            }
        }

        fun setActiveCell(row: Int, column: Int) {
            selectedCell = row to column
            editingCell = row to column
            selection = null
            render()
        }

        fun clearActiveCell() {
            val previousHeadersVisible = headersVisible()
            selectedCell = null
            editingCell = null
            if (previousHeadersVisible != headersVisible()) render() else {
                renderSelection()
                renderMarkers()
            }
        }

        private fun render() {
            val table = paragraph?.table?.normalized() ?: return
            updateHeaderVisibility()
            gridContainer.removeAllViews()
            cellBounds.clear()
            rowViews.clear()
            activeEditText = null
            if (rowHeaderHeights.size != table.rowCount) rowHeaderHeights = emptyList()
            renderHeaders(table)
            repeat(table.rowCount) { row ->
                val rowLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                rowViews += rowLayout
                repeat(table.columnCount) { column ->
                    val cellText = table.cell(row, column)
                    val cellView = if (editingCell == row to column) {
                        buildEditableCell(table, row, column, cellText)
                    } else {
                        buildReadOnlyCell(table, row, column, cellText)
                    }
                    rowLayout.addView(
                        cellView,
                        LinearLayout.LayoutParams(cellWidth(table.columnCount), LinearLayout.LayoutParams.WRAP_CONTENT)
                    )
                    if (column < table.columnCount - 1) {
                        rowLayout.addView(
                            View(context).apply { setBackgroundColor(0x33000000) },
                        LinearLayout.LayoutParams(1.dp, LinearLayout.LayoutParams.MATCH_PARENT)
                        )
                    }
                }
                gridContainer.addView(
                    rowLayout,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                )
                if (row < table.rowCount - 1) {
                    gridContainer.addView(
                        View(context).apply { setBackgroundColor(0x33000000) },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp)
                    )
                }
            }
            renderSelection()
            scheduleRowHeaderSync()
        }

        private fun buildReadOnlyCell(table: NoteTableData, row: Int, column: Int, text: String): TextView {
            val isHeader = row < table.headerRowCount
            return TextView(context).apply {
                setTextColor(colors.textColor)
                textSize = 15f
                typeface = if (isHeader) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                minHeight = 42.dp
                setPadding(12.dp, 10.dp, 12.dp, 10.dp)
                background = tableCellBackground(isHeader, false)
                this.text = text
                gravity = Gravity.TOP or Gravity.START
                setOnClickListener {
                    if (selectedCell == row to column && editingCell != row to column) {
                        onActivateCell(row, column)
                    } else {
                        val previousSelectedCell = selectedCell
                        onSelected()
                        selectedCell = row to column
                        selection = null
                        editingCell = null
                        renderSelection()
                        if (previousSelectedCell == null) render() else renderMarkers()
                    }
                }
                setOnLongClickListener { onSelected(); true }
            }
        }

        private fun buildEditableCell(table: NoteTableData, row: Int, column: Int, text: String): EditText {
            return TableCellEditText(context).apply {
                background = tableCellBackground(row < table.headerRowCount, true)
                setTextColor(colors.textColor)
                textSize = 15f
                minHeight = 42.dp
                setPadding(12.dp, 10.dp, 12.dp, 10.dp)
                setText(text)
                setSelection(text.length)
                gravity = Gravity.TOP or Gravity.START
                imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_ENTER_ACTION
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                doAfterTextChanged { editable ->
                    val current = paragraph?.table?.normalized() ?: return@doAfterTextChanged
                    val updated = current.withCell(row, column, editable?.toString().orEmpty())
                    paragraph = paragraph?.copy(table = updated)
                    onTableChanged(updated)
                }
                onImeNext = {
                    val nextColumn = (column + 1) % table.columnCount
                    val nextRow = if (nextColumn == 0) (row + 1).coerceAtMost(table.rowCount - 1) else row
                    onActivateCell(nextRow, nextColumn)
                }
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL && selectionStart == 0 && selectionEnd == 0) {
                        when {
                            row == 0 && column == 0 -> {
                                onSelected()
                                true
                            }
                            column > 0 -> {
                                onActivateCell(row, column - 1)
                                true
                            }
                            row > 0 -> {
                                onActivateCell(row - 1, table.columnCount - 1)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                requestFocus()
                post {
                    requestFocus()
                    setSelection(text.length)
                    context.inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
                activeEditText = this
            }
        }

        private fun renderSelection() {
            tableShell.background = tableBackgroundDrawable(colors.accentColor, 0x33000000)
        }

        private fun renderMarkers() {
            selectionOverlay.removeAllViews()
            val table = paragraph?.table?.normalized() ?: return
            val selectedCell = selectedCell ?: return
            if (tableShell.width == 0 || tableShell.height == 0) return
            updateCellBounds(table)
            renderHeaders(table)
            when (selection?.axis) {
                TableSelectionAxis.ROW -> {
                    val index = selection!!.index.coerceIn(0, table.rowCount - 1)
                    selectionOverlay.addView(buildRowSelectionLine(), selectionLineTopRowParams(index))
                    selectionOverlay.addView(buildRowSelectionLine(), selectionLineBottomRowParams(index))
                    selectionOverlay.addView(buildColumnSelectionLine(), selectionLineLeftRowParams(index))
                    selectionOverlay.addView(buildColumnSelectionLine(), selectionLineRightRowParams(index))
                }
                TableSelectionAxis.COLUMN -> {
                    val index = selection!!.index.coerceIn(0, table.columnCount - 1)
                    selectionOverlay.addView(buildColumnSelectionLine(), selectionLineLeftColumnParams(index, table.columnCount))
                    selectionOverlay.addView(buildColumnSelectionLine(), selectionLineRightColumnParams(index, table.columnCount))
                    selectionOverlay.addView(buildRowSelectionLine(), selectionLineTopColumnParams(index, table.columnCount))
                    selectionOverlay.addView(buildRowSelectionLine(), selectionLineBottomColumnParams(index, table.columnCount))
                }
                null -> drawSelectedCellOutline()
            }
        }

        private fun renderHeaders(table: NoteTableData) {
            updateHeaderVisibility()
            columnHeaderRow.removeAllViews()
            rowHeaderRows.removeAllViews()
            if (!headersVisible()) return
            val currentRow = selectedCell?.first?.coerceIn(0, table.rowCount - 1)
            val currentColumn = selectedCell?.second?.coerceIn(0, table.columnCount - 1)
            repeat(table.columnCount) { column ->
                val active = selection?.axis == TableSelectionAxis.COLUMN && selection?.index == column
                val marker = if (column == currentColumn) buildAxisMarker(TableSelectionAxis.COLUMN, column, active) else View(context)
                columnHeaderRow.addView(marker, LinearLayout.LayoutParams(cellWidth(table.columnCount), LinearLayout.LayoutParams.MATCH_PARENT))
                if (column < table.columnCount - 1) {
                    columnHeaderRow.addView(View(context), LinearLayout.LayoutParams(1.dp, LinearLayout.LayoutParams.MATCH_PARENT))
                }
            }
            repeat(table.rowCount) { row ->
                val active = selection?.axis == TableSelectionAxis.ROW && selection?.index == row
                val marker = if (row == currentRow) buildAxisMarker(TableSelectionAxis.ROW, row, active) else View(context)
                rowHeaderRows.addView(marker, LinearLayout.LayoutParams(rowHeaderWidth, rowHeight(row)))
                if (row < table.rowCount - 1) {
                    rowHeaderRows.addView(View(context), LinearLayout.LayoutParams(rowHeaderWidth, 1.dp))
                }
            }
        }

        private fun scheduleRowHeaderSync() {
            tableShell.post {
                val table = paragraph?.table?.normalized() ?: return@post
                updateCellBounds(table)
                val heights = rowViews.map { row -> row.height.takeIf { it > 0 } ?: 42.dp }
                if (heights != rowHeaderHeights) {
                    rowHeaderHeights = heights
                    renderHeaders(table)
                }
                renderMarkers()
            }
        }

        private fun updateCellBounds(table: NoteTableData) {
            cellBounds.clear()
            repeat(table.rowCount) { row ->
                val rowLayout = rowViews.getOrNull(row) ?: return@repeat
                repeat(table.columnCount) { column ->
                    val cellView = rowLayout.getChildAt(column * 2) ?: return@repeat
                    val left = gridLeftInOverlay() + rowLayout.left + cellView.left
                    val top = gridTopInOverlay() + rowLayout.top + cellView.top
                    cellBounds[row to column] = android.graphics.Rect(left, top, left + cellView.width, top + cellView.height)
                }
            }
        }

        private fun updateHeaderVisibility() {
            val visible = headersVisible()
            rowHeaderColumn.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            columnHeaderRow.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            val spacerParams = columnHeaderSpacer.layoutParams as LinearLayout.LayoutParams
            spacerParams.height = columnHeaderHeight
            columnHeaderSpacer.layoutParams = spacerParams
            val columnParams = columnHeaderRow.layoutParams as LinearLayout.LayoutParams
            columnParams.height = columnHeaderHeight
            columnHeaderRow.layoutParams = columnParams
        }

        private fun headersVisible(): Boolean = selected || selectedCell != null || editingCell != null || selection != null

        private fun drawSelectedCellOutline() {
            if (selection != null) return
            val selectedCell = selectedCell ?: return
            val rect = cellBounds[selectedCell] ?: return
            val left = rect.left
            val top = rect.top
            val width = rect.width()
            val height = rect.height()
            val color = colors.accentColor
            selectionOverlay.addView(View(context).apply { background = roundedDrawable(color, 0) }, FrameLayout.LayoutParams(width, 2.dp).apply {
                leftMargin = left
                topMargin = top
            })
            selectionOverlay.addView(View(context).apply { background = roundedDrawable(color, 0) }, FrameLayout.LayoutParams(width, 2.dp).apply {
                leftMargin = left
                topMargin = top + height - 2.dp
            })
            selectionOverlay.addView(View(context).apply { background = roundedDrawable(color, 0) }, FrameLayout.LayoutParams(2.dp, height).apply {
                leftMargin = left
                topMargin = top
            })
            selectionOverlay.addView(View(context).apply { background = roundedDrawable(color, 0) }, FrameLayout.LayoutParams(2.dp, height).apply {
                leftMargin = left + width - 2.dp
                topMargin = top
            })
        }

        private fun buildAxisMarker(axis: TableSelectionAxis, index: Int, active: Boolean): View {
            val marker = TextView(context).apply {
                text = when (axis) {
                    TableSelectionAxis.ROW -> "⋮"
                    TableSelectionAxis.COLUMN -> "⋯"
                }
                textSize = if (active) 17f else 16f
                gravity = Gravity.CENTER
                alpha = if (active) 1f else 0.82f
                setTextColor(colors.accentColor)
                background = roundedDrawable(AndroidColor.TRANSPARENT, 0)
                setPadding(2.dp, 2.dp, 2.dp, 2.dp)
                setOnClickListener {
                    if (selection?.axis == axis && selection?.index == index) {
                        showAxisMenu(this, axis, index)
                    } else {
                        val table = paragraph?.table?.normalized() ?: return@setOnClickListener
                        selection = TableSelectionRef(axis, index)
                        selectedCell = when (axis) {
                            TableSelectionAxis.ROW -> index.coerceIn(0, table.rowCount - 1) to (selectedCell?.second ?: 0).coerceIn(0, table.columnCount - 1)
                            TableSelectionAxis.COLUMN -> (selectedCell?.first ?: 0).coerceIn(0, table.rowCount - 1) to index.coerceIn(0, table.columnCount - 1)
                        }
                        editingCell = null
                        activeEditText?.clearFocus()
                        activeEditText = null
                        renderSelection()
                        renderMarkers()
                    }
                }
            }
            return marker
        }

        private fun showAxisMenu(anchor: View, axis: TableSelectionAxis, index: Int) {
            val table = paragraph?.table?.normalized() ?: return
            val popup = PopupMenu(ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light), anchor)
            if (axis == TableSelectionAxis.ROW) {
                popup.menu.add(0, 1, 0, "上方插入行")
                popup.menu.add(0, 2, 1, "下方插入行")
                popup.menu.add(0, 3, 2, "删除行")
                popup.menu.add(0, 4, 3, "向上移动")
                popup.menu.add(0, 5, 4, "向下移动")
            } else {
                popup.menu.add(0, 6, 0, "左侧插入列")
                popup.menu.add(0, 7, 1, "右侧插入列")
                popup.menu.add(0, 8, 2, "删除列")
            }
            popup.setOnMenuItemClickListener { item ->
                val updated = when (item.itemId) {
                    1 -> table.insertRow(index)
                    2 -> table.insertRow(index + 1)
                    3 -> if (table.rowCount <= 1 && table.columnCount <= 1) {
                        onDelete()
                        return@setOnMenuItemClickListener true
                    } else table.removeRow(index)
                    4 -> table.moveRow(index, (index - 1).coerceAtLeast(0))
                    5 -> table.moveRow(index, (index + 1).coerceAtMost(table.rowCount - 1))
                    6 -> table.insertColumn(index)
                    7 -> table.insertColumn(index + 1)
                    8 -> if (table.rowCount <= 1 && table.columnCount <= 1) {
                        onDelete()
                        return@setOnMenuItemClickListener true
                    } else table.removeColumn(index)
                    else -> null
                }
                if (updated != null) {
                    paragraph = paragraph?.copy(table = updated)
                    onTableChanged(updated)
                    selection = TableSelectionRef(axis, index.coerceIn(0, if (axis == TableSelectionAxis.ROW) updated.rowCount - 1 else updated.columnCount - 1))
                    selectedCell = when (axis) {
                        TableSelectionAxis.ROW -> {
                            val activeColumn = selectedCell?.second?.coerceIn(0, updated.columnCount - 1) ?: 0
                            selection!!.index to activeColumn
                        }
                        TableSelectionAxis.COLUMN -> {
                            val activeRow = selectedCell?.first?.coerceIn(0, updated.rowCount - 1) ?: 0
                            activeRow to selection!!.index
                        }
                    }
                    editingCell = null
                    render()
                }
                true
            }
            popup.show()
        }

        private fun buildRowSelectionLine(): View {
            return View(context).apply { background = roundedDrawable(colors.accentColor, 999.dp) }
        }

        private fun buildColumnSelectionLine(): View {
            return View(context).apply { background = roundedDrawable(colors.accentColor, 999.dp) }
        }

        private fun selectionLineTopRowParams(row: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 2.dp).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = gridLeftInOverlay()
                topMargin = gridTopInOverlay() + rowTop(row)
                width = gridWidth()
            }
        }

        private fun selectionLineBottomRowParams(row: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 2.dp).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = gridLeftInOverlay()
                topMargin = gridTopInOverlay() + rowBottom(row) - 2.dp
                width = gridWidth()
            }
        }

        private fun selectionLineLeftRowParams(row: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(2.dp, rowBottom(row) - rowTop(row)).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = gridLeftInOverlay()
                topMargin = gridTopInOverlay() + rowTop(row)
            }
        }

        private fun selectionLineRightRowParams(row: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(2.dp, rowBottom(row) - rowTop(row)).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = gridLeftInOverlay() + gridWidth() - 2.dp
                topMargin = gridTopInOverlay() + rowTop(row)
            }
        }

        private fun selectionLineLeftColumnParams(column: Int, columnCount: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(2.dp, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = gridLeftInOverlay() + cellLeft(column, columnCount)
                topMargin = gridTopInOverlay()
                height = gridHeight()
            }
        }

        private fun selectionLineRightColumnParams(column: Int, columnCount: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(2.dp, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = gridLeftInOverlay() + cellLeft(column, columnCount) + cellWidth(columnCount) - 2.dp
                topMargin = gridTopInOverlay()
                height = gridHeight()
            }
        }

        private fun selectionLineTopColumnParams(column: Int, columnCount: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(cellWidth(columnCount), 2.dp).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = gridLeftInOverlay() + cellLeft(column, columnCount)
                topMargin = gridTopInOverlay()
            }
        }

        private fun selectionLineBottomColumnParams(column: Int, columnCount: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(cellWidth(columnCount), 2.dp).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = gridLeftInOverlay() + cellLeft(column, columnCount)
                topMargin = gridTopInOverlay() + gridHeight() - 2.dp
            }
        }

        private fun tableCellBackground(isHeader: Boolean, active: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                setColor(AndroidColor.TRANSPARENT)
            }
        }

        private fun rowTop(row: Int): Int = rowViews.getOrNull(row)?.top ?: (row * (42.dp + 1.dp))

        private fun rowBottom(row: Int): Int = rowViews.getOrNull(row)?.bottom ?: (rowTop(row) + 42.dp)

        private fun rowCenter(row: Int): Int = (rowTop(row) + rowBottom(row)) / 2

        private fun rowHeight(row: Int): Int = rowHeaderHeights.getOrNull(row)
            ?: rowViews.getOrNull(row)?.height?.takeIf { it > 0 }
            ?: 42.dp

        private fun gridLeftInOverlay(): Int = gridContainer.left - selectionOverlay.left

        private fun gridTopInOverlay(): Int = gridContainer.top - selectionOverlay.top

        private fun gridWidth(): Int = gridContainer.width.takeIf { it > 0 } ?: tableShell.width

        private fun gridHeight(): Int = gridContainer.height.takeIf { it > 0 } ?: tableShell.height

        private fun cellLeft(column: Int, columnCount: Int): Int = column * (cellWidth(columnCount) + 1.dp)

        private fun cellWidth(columnCount: Int): Int {
            val columns = columnCount.coerceAtLeast(1)
            val gridLines = (columns - 1).coerceAtLeast(0).dp
            val shellPadding = 2.dp
            return ((tableViewportWidth() - gridLines - shellPadding) / columns).coerceAtLeast(108.dp)
        }

        private fun tableViewportWidth(): Int {
            val fallbackPanelWidth = context.resources.displayMetrics.widthPixels - 56.dp
            val measuredScrollWidth = scroll.width.takeIf { it > 0 }
            val panelWidth = tablePanel.width.takeIf { it > 0 }
                ?: container.width.takeIf { it > 0 }
                ?: fallbackPanelWidth
            return (measuredScrollWidth ?: (panelWidth - rowHeaderWidth - panelPadding)).coerceAtLeast(180.dp)
        }
    }
}

private fun NoteParagraph.isAttachmentLine(): Boolean = type == NoteParagraphType.IMAGE || type == NoteParagraphType.FILE

private fun NoteParagraph.isBlockLine(): Boolean = type == NoteParagraphType.IMAGE || type == NoteParagraphType.FILE || type == NoteParagraphType.DIVIDER || type == NoteParagraphType.TABLE

private fun String.containsLineBreak(): Boolean = contains('\n') || contains('\r')

private fun normalizeLineBreaks(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n')

private fun android.content.ClipData.toPlainText(context: Context): String {
    val builder = StringBuilder()
    for (index in 0 until itemCount) {
        val text = getItemAt(index).coerceToText(context)?.toString().orEmpty()
        if (text.isEmpty()) continue
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(text)
    }
    return builder.toString()
}

private fun roundedDrawable(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius.toFloat()
}

private fun roundedStrokeDrawable(strokeColor: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
    setColor(AndroidColor.TRANSPARENT)
    cornerRadius = radius.toFloat()
    setStroke(2.dp, strokeColor)
}

private fun tableBackgroundDrawable(@Suppress("UNUSED_PARAMETER") strokeColor: Int, borderColor: Int = 0x33000000): GradientDrawable = GradientDrawable().apply {
    setColor(AndroidColor.TRANSPARENT)
    cornerRadius = 0f
    setStroke(1.dp, borderColor)
}

private class InterceptingHorizontalScrollView(context: Context) : HorizontalScrollView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var disallowing = false

    init {
        isHorizontalScrollBarEnabled = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                disallowing = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(event.x - downX)
                val dy = kotlin.math.abs(event.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    val shouldDisallow = dx >= dy
                    if (shouldDisallow != disallowing) {
                        disallowing = shouldDisallow
                        parent?.requestDisallowInterceptTouchEvent(shouldDisallow)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                disallowing = false
            }
        }
        return super.onTouchEvent(event)
    }
}

private class TableCellEditText(context: Context) : EditText(context) {
    var onImeNext: (() -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        val base = super.onCreateInputConnection(outAttrs)
        return object : InputConnectionWrapper(base, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text?.toString() == "\n") {
                    val start = selectionStart.coerceAtLeast(0)
                    val end = selectionEnd.coerceAtLeast(0)
                    val editable = editableText
                    editable.replace(minOf(start, end), maxOf(start, end), "\n")
                    setSelection(minOf(start, end) + 1)
                    return true
                }
                return super.commitText(text, newCursorPosition)
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                return if (actionCode == EditorInfo.IME_ACTION_NEXT) {
                    post { onImeNext?.invoke() }
                    true
                } else {
                    super.performEditorAction(actionCode)
                }
            }
        }
    }
}

private fun buildReadOnlyText(paragraph: NoteParagraph): CharSequence {
    val builder = SpannableStringBuilder(paragraph.text.ifEmpty { " " })
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
