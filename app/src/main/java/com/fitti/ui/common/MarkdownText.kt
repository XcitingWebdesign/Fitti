package com.fitti.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private sealed class MarkdownBlock {
    data class H2(val text: String) : MarkdownBlock()
    data class H3(val text: String) : MarkdownBlock()
    data class ListItem(val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val pendingLines = mutableListOf<String>()

    fun flushParagraph() {
        if (pendingLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(pendingLines.joinToString(" ")))
            pendingLines.clear()
        }
    }

    for (line in markdown.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed.matches(Regex("^-{3,}$")) -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Divider)
            }
            trimmed.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.H3(trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.H2(trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("- ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.ListItem(trimmed.removePrefix("- ")))
            }
            else -> pendingLines.add(trimmed)
        }
    }
    flushParagraph()
    return blocks
}

private fun parseBoldSpans(text: String): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val start = remaining.indexOf("**")
            if (start == -1) {
                append(remaining)
                break
            }
            val end = remaining.indexOf("**", start + 2)
            if (end == -1) {
                append(remaining)
                break
            }
            append(remaining.substring(0, start))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(remaining.substring(start + 2, end))
            }
            remaining = remaining.substring(end + 2)
        }
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.H2 -> {
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    Text(
                        text = parseBoldSpans(block.text),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MarkdownBlock.H3 -> {
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        text = parseBoldSpans(block.text),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = "\u2022",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = parseBoldSpans(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Text(
                        text = parseBoldSpans(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownBlock.Divider -> {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

fun stripMarkdown(text: String): String {
    return text.lines()
        .filter { !it.trim().matches(Regex("^-{3,}$")) }
        .joinToString("\n") { line ->
            line.trim()
                .removePrefix("### ")
                .removePrefix("## ")
                .removePrefix("# ")
                .removePrefix("- ")
                .replace("**", "")
        }
}
