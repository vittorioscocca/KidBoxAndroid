package it.vittorioscocca.kidbox.feature.passwords.io

import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope

object PasswordsTxtParser {
    fun parseText(text: String, currentUid: String): ImportPreview {
        val legacy = parseLegacy(text)
        if (legacy.records.isNotEmpty()) {
            return ImportPreview(
                total = legacy.records.size,
                conflicts = emptyList(),
                newGroups = emptyList(),
                errors = emptyList(),
                skippedOtherPrivate = 0,
                legacyAmbiguousRecordIndices = legacy.ambiguousRecordIndices,
                records = legacy.records,
            )
        }

        val lines = text.lines()
        val blocks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        lines.forEach { line ->
            if (line.trim() == "---") {
                if (current.isNotEmpty()) {
                    blocks += current
                    current = mutableListOf()
                }
            } else if (line.isNotBlank() && !line.startsWith("# KidBox")) {
                current += line
            }
        }
        if (current.isNotEmpty()) blocks += current

        val errors = mutableListOf<ParseError>()
        val records = mutableListOf<ParsedPasswordRecord>()
        var skipped = 0
        blocks.forEachIndexed { i, block ->
            val map = linkedMapOf<String, String>()
            block.forEach { row ->
                val idx = row.indexOf(':')
                if (idx <= 0) return@forEach
                val key = row.substring(0, idx).trim().lowercase()
                val value = unescape(row.substring(idx + 1).trim())
                map[key] = value
            }
            val title = map["title"].orEmpty().trim()
            val password = map["password"].orEmpty()
            if (title.isBlank()) {
                errors += ParseError(i + 1, "Blocco senza Title")
                return@forEachIndexed
            }
            if (password.isBlank()) {
                errors += ParseError(i + 1, "Blocco senza Password")
                return@forEachIndexed
            }
            val rawVisibility = map["visibility"].orEmpty().trim()
            val visibility = if (rawVisibility.isEmpty()) {
                KBVisibilityScope.ONLY_CREATOR
            } else {
                KBVisibilityScope.normalizedPassword(rawVisibility)
            }
            val createdBy = map["createdby"].orEmpty().ifBlank { currentUid }
            if (visibility == KBVisibilityScope.ONLY_CREATOR && createdBy != currentUid) {
                skipped += 1
                return@forEachIndexed
            }
            records += ParsedPasswordRecord(
                title = title,
                username = map["username"].orEmpty(),
                password = password,
                website = map["website"].orEmpty(),
                group = map["group"].orEmpty(),
                visibility = visibility,
                note = map["note"].orEmpty(),
                createdBy = createdBy,
            )
        }
        return ImportPreview(records.size, emptyList(), emptyList(), errors, skipped, emptyList(), records)
    }

    private fun parseLegacy(text: String): LegacyParse {
        val regex = Regex(
            pattern = """Account:\s(.*?)\sGroup:\s(.*?)\sWebSite:\s(.*?)\sUsername:\s(.*?)\sPassword:\s(.*?)\sNote:\s(.*?)(?=Account:\s|\z)""",
            options = setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return LegacyParse(emptyList(), emptyList())

        val validStarts = matches.map { it.range.first }.toSet()
        val tokenRegex = Regex("""Account:\s""")
        val ambiguous = mutableSetOf<Int>()
        tokenRegex.findAll(text).forEach { token ->
            if (token.range.first in validStarts) return@forEach
            val owner = matches.indexOfLast { it.range.first < token.range.first }
            ambiguous += (if (owner < 0) 1 else owner + 1)
        }

        matches.forEachIndexed { index, match ->
            if (Regex("""Account:\s""").containsMatchIn(match.groupValues[6])) {
                ambiguous += (index + 1)
            }
        }

        val records = matches.map { m ->
            ParsedPasswordRecord(
                title = m.groupValues[1],
                username = m.groupValues[4],
                password = m.groupValues[5],
                website = m.groupValues[3],
                group = m.groupValues[2],
                visibility = KBVisibilityScope.ONLY_CREATOR,
                note = m.groupValues[6],
                createdBy = "",
            )
        }
        return LegacyParse(records, ambiguous.toList().sorted())
    }

    private fun unescape(value: String): String = value.replace("\\\\", "\\u0000").replace("\\n", "\n").replace("\\u0000", "\\")
}

private data class LegacyParse(
    val records: List<ParsedPasswordRecord>,
    val ambiguousRecordIndices: List<Int>,
)
