package it.vittorioscocca.kidbox.feature.passwords.io

data class Conflict(val title: String, val username: String)

data class ParseError(val row: Int, val message: String)

enum class MergeStrategy {
    SKIP_DUPLICATES,
    OVERWRITE_BY_TITLE_USERNAME,
    KEEP_BOTH,
}

data class ImportPreview(
    val total: Int,
    val conflicts: List<Conflict>,
    val newGroups: List<String>,
    val errors: List<ParseError>,
    val skippedOtherPrivate: Int = 0,
    val legacyAmbiguousRecordIndices: List<Int> = emptyList(),
    val records: List<ParsedPasswordRecord> = emptyList(),
)

data class ParsedPasswordRecord(
    val title: String,
    val username: String,
    val password: String,
    val website: String,
    val group: String,
    val visibility: String,
    val note: String,
    val createdBy: String,
)
