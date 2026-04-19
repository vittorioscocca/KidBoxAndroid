package it.vittorioscocca.kidbox.data.remote.notes

import androidx.core.text.HtmlCompat

/** iOS salva il titolo come testo semplice; normalizza se arriva HTML. */
fun String.noteTitleForStorage(): String {
    val t = trim().replace('\u00A0', ' ')
    if (t.isBlank()) return ""
    if (!t.contains('<') && !t.contains("&lt;")) return t
    return HtmlCompat.fromHtml(t, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .trim()
}

/** Allinea i marker checklist vecchi (Android) a quelli iOS (RichTextFormatting.swift). */
fun String.normalizeChecklistGlyphsToIos(): String =
    this
        .replace("☐ ", "○ ")
        .replace("☑ ", "◉ ")
        .replace("\u2610 ", "\u25CB ")
        .replace("\u2611 ", "\u25C9 ")
