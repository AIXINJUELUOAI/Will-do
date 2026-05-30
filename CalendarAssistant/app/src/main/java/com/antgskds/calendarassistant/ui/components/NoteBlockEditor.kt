package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import com.antgskds.calendarassistant.calendar.models.EventAttachment
import com.antgskds.calendarassistant.calendar.models.isImage
import com.antgskds.calendarassistant.core.attachment.EventAttachmentManager

private val RegexHeading = Regex("^#{1,6}\\s+.*")
private val RegexQuote = Regex("^\\s*>.*")
private val RegexTaskList = Regex("^\\s*[-+*]\\s+\\[(?: |x|X)](\\s+.*)?$")
private val RegexBulletList = Regex("^\\s*[-+*]\\s+(.*)?$")
private val RegexOrderedList = Regex("^\\s*\\d+\\.\\s+(.*)?$")
private val RegexHr = Regex("^\\s*([-*_]\\s*){3,}$")
private val AbandonedMarkerRegex = Regex("^([-+*>]|\\d+\\.|#{1,6})\\s*$")
private val TaskLineParseRegex = Regex("^\\s*[-+*]\\s+\\[( |x|X)](?:\\s+(.*))?$")

private data class EditorDraft(
    val value: TextFieldValue,
    val preferredSelection: Int = value.selection.end
)

class BlockNoteEditorController {
    internal var commitActiveBlock: (() -> String)? = null
    internal var applyMarkdownToolAction: ((NoteMarkdownTool) -> String)? = null
    internal var insertMarkdownBlockAction: ((String) -> String)? = null

    fun commit(): String? = commitActiveBlock?.invoke()
    fun applyMarkdownTool(tool: NoteMarkdownTool): String? = applyMarkdownToolAction?.invoke(tool)
    fun insertMarkdownBlock(markdown: String): String? = insertMarkdownBlockAction?.invoke(markdown)
}

enum class NoteMarkdownTool {
    H1,
    H2,
    TASK,
    BULLET,
    IMAGE,
    FILE,
    QUOTE,
    DIVIDER,
    CODE,
    ORDERED
}

private sealed class NoteBlock(
    open val id: Int,
    open val raw: String,
    open val start: Int,
    open val end: Int
) {
    data class Heading(val level: Int, override val id: Int, override val raw: String, override val start: Int, override val end: Int) : NoteBlock(id, raw, start, end)
    data class Paragraph(override val id: Int, override val raw: String, override val start: Int, override val end: Int) : NoteBlock(id, raw, start, end)
    data class Quote(override val id: Int, override val raw: String, override val start: Int, override val end: Int) : NoteBlock(id, raw, start, end)
    data class TaskList(override val id: Int, override val raw: String, override val start: Int, override val end: Int) : NoteBlock(id, raw, start, end)
    data class BulletList(override val id: Int, override val raw: String, override val start: Int, override val end: Int) : NoteBlock(id, raw, start, end)
    data class OrderedList(override val id: Int, override val raw: String, override val start: Int, override val end: Int) : NoteBlock(id, raw, start, end)
    data class Table(override val id: Int, override val raw: String, override val start: Int, override val end: Int) : NoteBlock(id, raw, start, end)
    data class HorizontalRule(override val id: Int, override val raw: String, override val start: Int, override val end: Int) : NoteBlock(id, raw, start, end)
}

@Composable
fun BlockNoteEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    controller: BlockNoteEditorController? = null,
    modifier: Modifier = Modifier,
    textColor: Color,
    onActiveEditChanged: ((Boolean) -> Unit)? = null,
    attachments: List<EventAttachment> = emptyList(),
    onOpenAttachment: ((EventAttachment) -> Unit)? = null
) {
    var workingMarkdown by remember { mutableStateOf(markdown) }
    var activeBlockId by remember { mutableStateOf<Int?>(null) }
    var activeDraft by remember { mutableStateOf("") }
    var activeDraftSelection by remember { mutableStateOf<Int?>(null) }
    var isCreatingNewBlock by remember { mutableStateOf(false) }

    val blocks = remember(workingMarkdown) { parseNoteBlocks(workingMarkdown) }
    val attachmentsById = remember(attachments) { attachments.mapNotNull { attachment -> attachment.id?.let { it to attachment } }.toMap() }

    LaunchedEffect(markdown) {
        if (activeBlockId == null && !isCreatingNewBlock && markdown != workingMarkdown) {
            workingMarkdown = markdown
        }
    }

    LaunchedEffect(activeBlockId, isCreatingNewBlock) {
        onActiveEditChanged?.invoke(activeBlockId != null || isCreatingNewBlock)
    }

    fun isEffectivelyEmpty(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isEmpty() || trimmed.matches(AbandonedMarkerRegex)
    }

    fun normalizeMarkdown(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trimEnd()
    }

    fun commitActive(): String {
        if (isCreatingNewBlock) {
            val next = if (isEffectivelyEmpty(activeDraft)) {
                normalizeMarkdown(workingMarkdown)
            } else {
                val base = workingMarkdown.trimEnd()
                normalizeMarkdown(if (base.isBlank()) activeDraft.trimEnd() else "$base\n\n${activeDraft.trimEnd()}")
            }
            workingMarkdown = next
            onMarkdownChange(next)
            activeDraft = ""
            activeDraftSelection = null
            activeBlockId = null
            isCreatingNewBlock = false
            return next
        }

        val id = activeBlockId ?: return workingMarkdown
        val currentBlocks = parseNoteBlocks(workingMarkdown)
        val block = currentBlocks.firstOrNull { it.id == id } ?: run {
            activeDraft = ""
            activeDraftSelection = null
            activeBlockId = null
            return workingMarkdown
        }

        val replacement = if (isEffectivelyEmpty(activeDraft)) "" else activeDraft.trimEnd()
        val updated = buildString {
            append(workingMarkdown.substring(0, block.start))
            append(replacement)
            append(workingMarkdown.substring(block.end))
        }
        val normalized = normalizeMarkdown(updated)
        workingMarkdown = normalized
        onMarkdownChange(normalized)
        activeDraft = ""
        activeDraftSelection = null
        activeBlockId = null
        return normalized
    }

    fun applyMarkdownTool(tool: NoteMarkdownTool): String {
        val prefix = when (tool) {
            NoteMarkdownTool.H1 -> "# "
            NoteMarkdownTool.H2 -> "## "
            NoteMarkdownTool.TASK -> "- [ ] "
            NoteMarkdownTool.BULLET -> "- "
            NoteMarkdownTool.ORDERED -> "1. "
            NoteMarkdownTool.QUOTE -> "> "
            NoteMarkdownTool.DIVIDER -> "---"
            NoteMarkdownTool.CODE -> "```\n\n```"
            NoteMarkdownTool.IMAGE,
            NoteMarkdownTool.FILE -> ""
        }
        if (prefix.isBlank()) return activeDraft
        if (activeBlockId == null && !isCreatingNewBlock) {
            commitActive()
            activeBlockId = null
            activeDraft = ""
            activeDraftSelection = null
            isCreatingNewBlock = true
        }
        val formatted = applyMarkdownPrefix(activeDraft, prefix, tool)
        activeDraft = formatted.value.text
        activeDraftSelection = formatted.preferredSelection
        return activeDraft
    }

    fun insertMarkdownBlock(markdownBlock: String): String {
        val block = markdownBlock.trim()
        if (block.isBlank()) return workingMarkdown
        if (activeBlockId != null || isCreatingNewBlock) {
            activeDraft = if (activeDraft.isBlank()) block else "${activeDraft.trimEnd()}\n\n$block"
            activeDraftSelection = activeDraft.length
            return commitActive()
        }
        val base = workingMarkdown.trimEnd()
        val next = normalizeMarkdown(if (base.isBlank()) block else "$base\n\n$block")
        workingMarkdown = next
        onMarkdownChange(next)
        return next
    }

    SideEffect {
        controller?.commitActiveBlock = { commitActive() }
        controller?.applyMarkdownToolAction = { tool -> applyMarkdownTool(tool) }
        controller?.insertMarkdownBlockAction = { markdownBlock -> insertMarkdownBlock(markdownBlock) }
    }

    fun beginEdit(block: NoteBlock) {
        if (activeBlockId == block.id && !isCreatingNewBlock) return
        val targetRaw = block.raw
        val targetIndex = blocks.indexOfFirst { it.id == block.id }
        commitActive()
        val refreshedBlocks = parseNoteBlocks(workingMarkdown)
        val located = refreshedBlocks.getOrNull(targetIndex)?.takeIf { it.raw == targetRaw }
            ?: refreshedBlocks.firstOrNull { it.raw == targetRaw }
        if (located != null) {
            activeBlockId = located.id
            activeDraft = located.raw
            activeDraftSelection = located.raw.length
            isCreatingNewBlock = false
        }
    }

    fun beginNewBlock() {
        commitActive()
        activeBlockId = null
        activeDraft = ""
        activeDraftSelection = null
        isCreatingNewBlock = true
    }

    val baseTextSize = MaterialTheme.typography.bodyLarge.fontSize.value + 1f
    val lineSpacing = 8f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (activeBlockId != null || isCreatingNewBlock) {
                    commitActive()
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        item(key = "title") {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (title.isBlank()) {
                            Text(
                                text = "无标题",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 30.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                )
                            )
                        }
                        inner()
                    }
                }
            )
        }

        item(key = "title-space") {
            Spacer(modifier = Modifier.height(2.dp))
        }

        items(blocks, key = { it.id }) { block ->
            val isEditing = !isCreatingNewBlock && activeBlockId == block.id
            if (isEditing) {
                        BlockEditorField(
                            value = activeDraft,
                            preferredSelection = activeDraftSelection,
                            onValueChange = {
                                activeDraft = it
                                activeDraftSelection = null
                            },
                            textColor = textColor,
                            onDone = { commitActive() }
                        )
            } else {
                RenderedBlock(
                    block = block,
                    attachmentsById = attachmentsById,
                    textColor = textColor,
                    baseTextSize = baseTextSize,
                    lineSpacing = lineSpacing,
                    onClick = { beginEdit(block) },
                    onOpenAttachment = onOpenAttachment,
                    onUpdateRaw = { newRaw ->
                        val updated = buildString {
                            append(workingMarkdown.substring(0, block.start))
                            append(newRaw.trimEnd())
                            append(workingMarkdown.substring(block.end))
                        }
                        val normalized = normalizeMarkdown(updated)
                        workingMarkdown = normalized
                        onMarkdownChange(normalized)
                    }
                )
            }
        }

            item(key = "new-block") {
                if (isCreatingNewBlock) {
                    BlockEditorField(
                        value = activeDraft,
                        preferredSelection = activeDraftSelection,
                        onValueChange = {
                            activeDraft = it
                            activeDraftSelection = null
                        },
                        textColor = textColor,
                        onDone = { commitActive() }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                beginNewBlock()
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderedBlock(
    block: NoteBlock,
    attachmentsById: Map<Long, EventAttachment>,
    textColor: Color,
    baseTextSize: Float,
    lineSpacing: Float,
    onClick: () -> Unit,
    onOpenAttachment: ((EventAttachment) -> Unit)?,
    onUpdateRaw: (String) -> Unit
) {
    val attachmentToken = parseAttachmentToken(block.raw)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = if (attachmentToken == null) onClick else {
                    {
                        attachmentsById[attachmentToken.id]?.let { attachment -> onOpenAttachment?.invoke(attachment) }
                        Unit
                    }
                }
            )
            .padding(vertical = 2.dp)
    ) {
        when (block) {
            is NoteBlock.Heading -> {
                val headingStyle = when (block.level) {
                    1 -> MaterialTheme.typography.headlineMedium
                    2 -> MaterialTheme.typography.headlineSmall
                    3 -> MaterialTheme.typography.titleLarge
                    4 -> MaterialTheme.typography.titleMedium
                    5 -> MaterialTheme.typography.titleSmall
                    else -> MaterialTheme.typography.bodyLarge
                }
                Text(
                    text = block.raw.replace(Regex("^#{1,6}\\s+"), ""),
                    style = headingStyle.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            is NoteBlock.Quote -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .padding(top = 2.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.raw.lines().forEach { line ->
                            val text = line.replaceFirst(Regex("^\\s*>\\s?"), "")
                            if (text.isNotBlank()) {
                                MarkdownText(
                                    markdown = text,
                                    textColor = textColor.copy(alpha = 0.88f),
                                    linkColor = MaterialTheme.colorScheme.primary,
                                    enableLinkClicks = true,
                                    textSizeSp = baseTextSize,
                                    lineSpacingExtraPx = lineSpacing
                                )
                            }
                        }
                    }
                }
            }

            is NoteBlock.TaskList -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.raw.lines().forEachIndexed { lineIndex, line ->
                        val match = TaskLineParseRegex.find(line)
                        if (match != null) {
                            val isChecked = match.groupValues[1].equals("x", ignoreCase = true)
                            val content = match.groupValues.getOrNull(2).orEmpty()
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            val newChar = if (checked) "x" else " "
                                            val newLine = line.replaceFirst(Regex("\\[( |x|X)]"), "[$newChar]")
                                            val lines = block.raw.lines().toMutableList()
                                            lines[lineIndex] = newLine
                                            onUpdateRaw(lines.joinToString("\n"))
                                        },
                                        modifier = Modifier.padding(top = 2.dp, end = 12.dp),
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            uncheckedColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 1.dp)
                                ) {
                                    MarkdownText(
                                        markdown = content,
                                        textColor = if (isChecked) textColor.copy(alpha = 0.52f) else textColor,
                                        linkColor = MaterialTheme.colorScheme.primary,
                                        enableLinkClicks = true,
                                        textSizeSp = baseTextSize,
                                        lineSpacingExtraPx = lineSpacing
                                    )
                                }
                            }
                        }
                    }
                }
            }

            is NoteBlock.BulletList -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.raw.lines().forEach { line ->
                        val text = line.replaceFirst(Regex("^\\s*[-+*]\\s+"), "")
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "•",
                                fontSize = baseTextSize.sp,
                                color = textColor,
                                modifier = Modifier.padding(end = 12.dp, start = 6.dp)
                            )
                            MarkdownText(
                                markdown = text,
                                textColor = textColor,
                                linkColor = MaterialTheme.colorScheme.primary,
                                enableLinkClicks = true,
                                textSizeSp = baseTextSize,
                                lineSpacingExtraPx = lineSpacing,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            is NoteBlock.OrderedList -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.raw.lines().forEach { line ->
                        val match = Regex("^\\s*(\\d+\\.)\\s+(.*)").find(line)
                        val num = match?.groupValues?.get(1) ?: "1."
                        val text = match?.groupValues?.get(2) ?: line.trim()
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = num,
                                fontSize = baseTextSize.sp,
                                color = textColor,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            MarkdownText(
                                markdown = text,
                                textColor = textColor,
                                linkColor = MaterialTheme.colorScheme.primary,
                                enableLinkClicks = true,
                                textSizeSp = baseTextSize,
                                lineSpacingExtraPx = lineSpacing,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            is NoteBlock.HorizontalRule -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.5.dp
                    )
                }
            }

            else -> {
                if (attachmentToken != null) {
                    AttachmentMarkdownBlock(
                        token = attachmentToken,
                        attachment = attachmentsById[attachmentToken.id],
                        onClick = {
                            attachmentsById[attachmentToken.id]?.let { attachment -> onOpenAttachment?.invoke(attachment) }
                        }
                    )
                } else {
                    MarkdownText(
                        markdown = block.raw,
                        modifier = Modifier.fillMaxWidth(),
                        textColor = textColor,
                        linkColor = MaterialTheme.colorScheme.primary,
                        enableLinkClicks = true,
                        textSizeSp = baseTextSize,
                        lineSpacingExtraPx = lineSpacing
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentMarkdownBlock(
    token: AttachmentMarkdownToken,
    attachment: EventAttachment?,
    onClick: () -> Unit
) {
    val isImage = attachment?.isImage == true || token.isImage
    val bitmap = remember(attachment?.localPath) {
        attachment?.localPath?.takeIf { isImage }?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = attachment != null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isImage && bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = token.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isImage) Icons.Default.BrokenImage else Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attachment?.displayName?.ifBlank { token.label } ?: token.label.ifBlank { "附件 ${token.id}" },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (attachment == null) "附件未找到" else EventAttachmentManager.formatSize(attachment.sizeBytes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private data class AttachmentMarkdownToken(
    val id: Long,
    val label: String,
    val isImage: Boolean
)

private val AttachmentMarkdownRegex = Regex("^(!?)\\[([^]]*)]\\(willdo-attachment://(\\d+)\\)\\s*$")

private fun parseAttachmentToken(raw: String): AttachmentMarkdownToken? {
    val match = AttachmentMarkdownRegex.matchEntire(raw.trim()) ?: return null
    val id = match.groupValues[3].toLongOrNull() ?: return null
    return AttachmentMarkdownToken(
        id = id,
        label = match.groupValues[2],
        isImage = match.groupValues[1] == "!"
    )
}

@Composable
private fun BlockEditorField(
    value: String,
    preferredSelection: Int?,
    onValueChange: (String) -> Unit,
    textColor: Color,
    onDone: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange((preferredSelection ?: value.length).coerceIn(0, value.length)))) }

    LaunchedEffect(value, preferredSelection) {
        if (value != fieldValue.text || preferredSelection != null) {
            val selection = (preferredSelection ?: value.length).coerceIn(0, value.length)
            fieldValue = TextFieldValue(value, TextRange(selection))
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos {}
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { next ->
            fieldValue = next
            onValueChange(next.text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
            fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value + 1f).sp,
            lineHeight = 26.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (fieldValue.text.isBlank()) {
                    Text(
                        text = "开始输入...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value + 1f).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
                inner()
            }
        }
    )
}

private data class LineInfo(val text: String, val start: Int, val end: Int)

private fun parseNoteBlocks(markdown: String): List<NoteBlock> {
    if (markdown.isBlank()) return emptyList()
    val normalized = markdown.replace("\r\n", "\n")
    val lines = mutableListOf<LineInfo>()
    var cursor = 0
    while (cursor <= normalized.length) {
        val next = normalized.indexOf('\n', cursor)
        if (next == -1) {
            lines += LineInfo(normalized.substring(cursor), cursor, normalized.length)
            break
        }
        lines += LineInfo(normalized.substring(cursor, next), cursor, next)
        cursor = next + 1
        if (cursor == normalized.length) {
            lines += LineInfo("", cursor, cursor)
            break
        }
    }

    val blocks = mutableListOf<NoteBlock>()
    var index = 0
    while (index < lines.size) {
        if (lines[index].text.isBlank()) {
            index++
            continue
        }
        val firstLine = lines[index]
        when {
            firstLine.text.matches(RegexHeading) -> {
                blocks += createBlock(firstLine.start, firstLine.end, normalized)
                index++
            }
            firstLine.text.matches(RegexQuote) -> {
                val start = index
                while (index < lines.size && lines[index].text.matches(RegexQuote)) index++
                blocks += createBlock(lines[start].start, lines[index - 1].end, normalized)
            }
            firstLine.text.matches(RegexTaskList) -> {
                val start = index
                while (index < lines.size && lines[index].text.matches(RegexTaskList)) index++
                blocks += createBlock(lines[start].start, lines[index - 1].end, normalized)
            }
            firstLine.text.matches(RegexBulletList) -> {
                val start = index
                while (index < lines.size && lines[index].text.matches(RegexBulletList)) index++
                blocks += createBlock(lines[start].start, lines[index - 1].end, normalized)
            }
            firstLine.text.matches(RegexOrderedList) -> {
                val start = index
                while (index < lines.size && lines[index].text.matches(RegexOrderedList)) index++
                blocks += createBlock(lines[start].start, lines[index - 1].end, normalized)
            }
            isTableStart(lines, index) -> {
                val start = index
                index += 2
                while (index < lines.size && looksLikeTableRow(lines[index].text)) index++
                blocks += createBlock(lines[start].start, lines[index - 1].end, normalized)
            }
            firstLine.text.matches(RegexHr) -> {
                blocks += createBlock(firstLine.start, firstLine.end, normalized)
                index++
            }
            else -> {
                val start = index
                while (index < lines.size && lines[index].text.isNotBlank() && !startsStructuredBlock(lines, index)) index++
                blocks += createBlock(lines[start].start, lines[index - 1].end, normalized)
            }
        }
    }
    return blocks
}

private fun createBlock(start: Int, end: Int, source: String): NoteBlock {
    val raw = source.substring(start, end)
    return classifyBlock(raw, start, end)
}

private fun classifyBlock(raw: String, start: Int = 0, end: Int = raw.length): NoteBlock {
    val trimmed = raw.trimEnd()
    val lines = trimmed.split('\n')
    val first = lines.firstOrNull().orEmpty()
    if (lines.size == 1 && first.matches(RegexHeading)) {
        val level = first.takeWhile { it == '#' }.length.coerceIn(1, 6)
        return NoteBlock.Heading(level, start, trimmed, start, end)
    }
    return when {
        trimmed.isBlank() -> NoteBlock.Paragraph(start, "", start, end)
        lines.all { it.matches(RegexQuote) } -> NoteBlock.Quote(start, trimmed, start, end)
        lines.all { it.matches(RegexTaskList) } -> NoteBlock.TaskList(start, trimmed, start, end)
        lines.all { it.matches(RegexBulletList) } -> NoteBlock.BulletList(start, trimmed, start, end)
        lines.all { it.matches(RegexOrderedList) } -> NoteBlock.OrderedList(start, trimmed, start, end)
        lines.size >= 2 && isTableStart(lines.mapIndexed { idx, t -> LineInfo(t, idx, idx) }, 0) -> NoteBlock.Table(start, trimmed, start, end)
        trimmed.matches(RegexHr) -> NoteBlock.HorizontalRule(start, trimmed, start, end)
        else -> NoteBlock.Paragraph(start, trimmed, start, end)
    }
}

private fun startsStructuredBlock(lines: List<LineInfo>, index: Int): Boolean {
    val line = lines[index].text
    return line.matches(RegexHeading) ||
        line.matches(RegexQuote) ||
        line.matches(RegexTaskList) ||
        line.matches(RegexBulletList) ||
        line.matches(RegexOrderedList) ||
        line.matches(RegexHr) ||
        isTableStart(lines, index)
}

private fun isTableStart(lines: List<LineInfo>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    return looksLikeTableRow(lines[index].text) && looksLikeTableDivider(lines[index + 1].text)
}

private fun looksLikeTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.contains('|') && trimmed.count { it == '|' } >= 2
}

private fun looksLikeTableDivider(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.isNotBlank() && trimmed.all { it == '|' || it == ':' || it == '-' || it.isWhitespace() }
}

private fun applyMarkdownPrefix(current: String, prefix: String, tool: NoteMarkdownTool): EditorDraft {
    val trimmed = current.trimStart()
    fun draft(text: String, selection: Int = prefix.length.coerceAtMost(text.length)) =
        EditorDraft(TextFieldValue(text, TextRange(selection.coerceIn(0, text.length))), selection.coerceIn(0, text.length))

    return when (tool) {
        NoteMarkdownTool.H1,
        NoteMarkdownTool.H2 -> {
            val content = trimmed.replace(Regex("^#{1,6}\\s*"), "")
            draft("$prefix$content")
        }
        NoteMarkdownTool.TASK -> {
            val content = trimmed
                .replace(Regex("^[-+*]\\s+\\[(?: |x|X)]\\s*"), "")
                .replace(Regex("^[-+*]\\s+"), "")
                .replace(Regex("^\\d+\\.\\s+"), "")
            draft("$prefix$content")
        }
        NoteMarkdownTool.BULLET -> {
            val content = trimmed
                .replace(Regex("^[-+*]\\s+\\[(?: |x|X)]\\s*"), "")
                .replace(Regex("^[-+*]\\s+"), "")
                .replace(Regex("^\\d+\\.\\s+"), "")
            draft("$prefix$content")
        }
        NoteMarkdownTool.ORDERED -> {
            val content = trimmed
                .replace(Regex("^[-+*]\\s+\\[(?: |x|X)]\\s*"), "")
                .replace(Regex("^[-+*]\\s+"), "")
                .replace(Regex("^\\d+\\.\\s+"), "")
            draft("$prefix$content")
        }
        NoteMarkdownTool.QUOTE -> {
            val content = trimmed.replace(Regex("^>\\s*"), "")
            draft("$prefix$content")
        }
        NoteMarkdownTool.DIVIDER,
        NoteMarkdownTool.CODE -> {
            val base = current.trimEnd()
            val text = if (base.isBlank()) prefix else "$base\n\n$prefix"
            draft(text, text.length)
        }
        NoteMarkdownTool.IMAGE,
        NoteMarkdownTool.FILE -> draft(current, current.length)
    }
}
