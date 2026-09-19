package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

data class InlineSummaryAction(
    val text: String,
    val icon: Painter,
    val iconDescription: String,
    val onClick: () -> Unit,
    val iconSize: TextUnit = 1.em,
)

/** 图标作为文字中的占位内容参与换行；链接只覆盖各自摘要，不撑开横向间距。 */
@Composable
fun InlineSummaryHeader(
    title: String,
    actions: List<InlineSummaryAction>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    markerColor: Color? = null,
    titleFontWeight: FontWeight? = null,
) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = titleFontWeight)) { append(title) }
        actions.forEachIndexed { index, action ->
            if (title.isNotEmpty() || index > 0) append(" · ")
            withLink(LinkAnnotation.Clickable(
                tag = "summary_$index",
                styles = TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.None)),
                linkInteractionListener = { action.onClick() },
            )) {
                appendInlineContent("icon_$index", action.iconDescription)
                append("\u00a0")
                append(action.text)
            }
        }
    }
    val inlineContent = actions.mapIndexed { index, action ->
        "icon_$index" to InlineTextContent(Placeholder(action.iconSize, action.iconSize, PlaceholderVerticalAlign.TextCenter)) {
            Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.fillMaxSize())
        }
    }.toMap()
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        if (markerColor != null) {
            val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
            Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                Box(Modifier.size(6.dp).background(markerColor, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(text, inlineContent = inlineContent, style = style, color = color, modifier = Modifier.weight(1f))
    }
}
