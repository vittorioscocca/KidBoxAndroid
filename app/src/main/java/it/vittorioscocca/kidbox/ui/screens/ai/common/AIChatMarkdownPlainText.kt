package it.vittorioscocca.kidbox.ui.screens.ai.common

/**
 * Converte il markdown delle risposte AI in testo piano per gli appunti.
 */
object AIChatMarkdownPlainText {

    fun forClipboard(raw: String): String {
        val normalized = raw.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return ""
        val blocks = parseBlocks(normalized)
        if (blocks.isEmpty()) return stripInlineMarkdown(normalized)
        return blocks
            .mapNotNull { block -> plainText(block).takeIf { it.isNotBlank() } }
            .joinToString("\n\n")
            .trim()
    }

    private sealed class Block {
        data class Heading(val level: Int, val text: String) : Block()
        data class Paragraph(val text: String) : Block()
        data class ListBlock(val items: List<String>, val ordered: Boolean) : Block()
        data class CodeBlock(val text: String) : Block()
    }

    private fun plainText(block: Block): String = when (block) {
        is Block.Heading -> stripInlineMarkdown(block.text)
        is Block.Paragraph -> stripInlineMarkdown(block.text)
        is Block.ListBlock -> block.items.mapIndexed { index, item ->
            val prefix = if (block.ordered) "${index + 1}. " else "• "
            prefix + stripInlineMarkdown(item)
        }.joinToString("\n")
        is Block.CodeBlock -> block.text.trim()
    }

    private fun parseBlocks(raw: String): List<Block> {
        val lines = raw.replace("\r\n", "\n").split('\n')
        val out = mutableListOf<Block>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                i++
                val code = mutableListOf<String>()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.add(lines[i])
                    i++
                }
                out.add(Block.CodeBlock(code.joinToString("\n")))
                i++
                continue
            }

            if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                val level = if (trimmed.startsWith("### ")) 3 else 2
                out.add(Block.Heading(level, trimmed.drop(level + 1)))
                i++
                continue
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith("- ") || t.startsWith("* ")) {
                        items.add(t.drop(2))
                        i++
                    } else break
                }
                out.add(Block.ListBlock(items, ordered = false))
                continue
            }

            if (trimmed.matches(Regex("""^\d+\.\s+.*$"""))) {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.matches(Regex("""^\d+\.\s+.*$"""))) {
                        items.add(t.replaceFirst(Regex("""^\d+\.\s+"""), ""))
                        i++
                    } else break
                }
                out.add(Block.ListBlock(items, ordered = true))
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
                paragraph.add(lines[i])
                i++
            }
            out.add(Block.Paragraph(paragraph.joinToString("\n").trim()))
        }
        return out
    }

    private fun stripInlineMarkdown(input: String): String {
        var result = input
        val patterns = listOf(
            Regex("""\[([^\]]+)\]\([^)]*\)""") to "$1",
            Regex("""\*\*([^*]+)\*\*""") to "$1",
            Regex("""__([^_]+)__""") to "$1",
            Regex("""(?<!\*)\*([^*]+)\*(?!\*)""") to "$1",
            Regex("""`([^`]+)`""") to "$1",
        )
        patterns.forEach { (regex, replacement) ->
            result = regex.replace(result, replacement)
        }
        return result
            .lines()
            .joinToString("\n") { line ->
                val t = line.trim()
                when {
                    t.startsWith("### ") -> t.drop(4)
                    t.startsWith("## ") -> t.drop(3)
                    t.startsWith("# ") -> t.drop(2)
                    else -> line
                }
            }
            .trim()
    }
}
