package com.example.mimochat.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 轻量 Markdown 渲染：标题、粗体、斜体、行内/块级代码、列表。 */
@Composable
internal fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rendered = remember(text, colors.onSurface, colors.surfaceVariant) {
        markdownAnnotatedString(text, colors.onSurface, colors.surfaceVariant)
    }
    Text(
        text = rendered,
        modifier = modifier,
        fontSize = 14.sp,
        lineHeight = 23.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun markdownAnnotatedString(text: String, textColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        val lines = text.split('\n')
        var inCode = false
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
            } else if (inCode) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = textColor)) { append(line) }
            } else {
                val heading = Regex("^#{1,6}\\s+").find(line)
                if (heading != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) { append(line.removeRange(heading.range)) }
                } else if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                    append("• ")
                    appendInlineMarkdown(line.trimStart().drop(2), textColor, codeBackground)
                } else {
                    appendInlineMarkdown(line, textColor, codeBackground)
                }
            }
            if (index < lines.lastIndex) append('\n')
        }
    }

private fun AnnotatedString.Builder.appendInlineMarkdown(value: String, textColor: Color, codeBackground: Color) {
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith("**", index) || value.startsWith("__", index) -> {
                val marker = value.substring(index, index + 2)
                val end = value.indexOf(marker, index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) { append(value.substring(index + 2, end)) }
                    index = end + 2
                } else { append(value[index]); index++ }
            }
            value[index] == '`' -> {
                val end = value.indexOf('`', index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = textColor)) { append(value.substring(index + 1, end)) }
                    index = end + 1
                } else { append(value[index]); index++ }
            }
            value[index] == '*' || value[index] == '_' -> {
                val marker = value[index]
                val end = value.indexOf(marker, index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) { append(value.substring(index + 1, end)) }
                    index = end + 1
                } else { append(value[index]); index++ }
            }
            else -> { append(value[index]); index++ }
        }
    }
}
