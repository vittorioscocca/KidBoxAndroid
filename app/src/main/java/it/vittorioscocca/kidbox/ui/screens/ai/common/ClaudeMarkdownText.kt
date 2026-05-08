package it.vittorioscocca.kidbox.ui.screens.ai.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class ListBlock(val items: List<String>, val ordered: Boolean) : MarkdownBlock()
    data class CodeBlock(val text: String) : MarkdownBlock()
}

@Composable
fun ClaudeMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = parseBlocks(text)

    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        Text(
                            text = inlineMarkdown(block.text),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (block.level == 2) 22.sp else 18.sp,
                            lineHeight = if (block.level == 2) 30.sp else 26.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Divider(modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = inlineMarkdown(block.text),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    is MarkdownBlock.ListBlock -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            block.items.forEachIndexed { index, item ->
                                Row {
                                    Text(
                                        if (block.ordered) "${index + 1}." else "•",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    Text(
                                        text = inlineMarkdown(item),
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    is MarkdownBlock.CodeBlock -> {
                        Text(
                            text = block.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private fun parseBlocks(raw: String): List<MarkdownBlock> {
    val lines = raw.replace("\r\n", "\n").split('\n')
    val out = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            i++
            val code = mutableListOf<String>()
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.add(lines[i]); i++
            }
            out.add(MarkdownBlock.CodeBlock(code.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
            val level = if (trimmed.startsWith("### ")) 3 else 2
            out.add(MarkdownBlock.Heading(level, trimmed.drop(level + 1)))
            i++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.startsWith("- ") || t.startsWith("* ")) {
                    items.add(t.drop(2)); i++
                } else break
            }
            out.add(MarkdownBlock.ListBlock(items, ordered = false))
            continue
        }

        if (trimmed.matches(Regex("""^\d+\.\s+.*$"""))) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.matches(Regex("""^\d+\.\s+.*$"""))) {
                    items.add(t.replaceFirst(Regex("""^\d+\.\s+"""), "")); i++
                } else break
            }
            out.add(MarkdownBlock.ListBlock(items, ordered = true))
            continue
        }

        if (trimmed.isBlank()) {
            i++
            continue
        }

        val paragraph = mutableListOf(line)
        i++
        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.isBlank() || t.startsWith("## ") || t.startsWith("### ") || t.startsWith("```") ||
                t.startsWith("- ") || t.startsWith("* ") || t.matches(Regex("""^\d+\.\s+.*$"""))
            ) break
            paragraph.add(lines[i]); i++
        }
        out.add(MarkdownBlock.Paragraph(paragraph.joinToString("\n").trim()))
    }

    return out
}

private fun inlineMarkdown(input: String): AnnotatedString {
    val regex = Regex("""(`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*)""")
    val out = buildAnnotatedString {
        var cursor = 0
        regex.findAll(input).forEach { m ->
            if (m.range.first > cursor) append(input.substring(cursor, m.range.first))
            val token = m.value
            when {
                token.startsWith("**") && token.endsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removePrefix("**").removeSuffix("**"))
                }
                token.startsWith("*") && token.endsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removePrefix("*").removeSuffix("*"))
                }
                token.startsWith("`") && token.endsWith("`") -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                ) {
                    append(token.removePrefix("`").removeSuffix("`"))
                }
                else -> append(token)
            }
            cursor = m.range.last + 1
        }
        if (cursor < input.length) append(input.substring(cursor))
    }
    return out
}
