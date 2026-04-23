package it.vittorioscocca.kidbox.ui.screens.notes

import androidx.core.text.HtmlCompat
import it.vittorioscocca.kidbox.data.remote.notes.sanitizeCrossPlatformHtml

internal fun String.htmlToPlainText(
    trimEdges: Boolean = true,
): String {
    var value = replace('\u00A0', ' ')
    if (value.isBlank()) return ""

    if (
        value.contains('<') ||
        value.contains("&lt;") ||
        value.contains("&gt;") ||
        value.contains("&amp;")
    ) {
        // Rimuove <head>/<style>/class=... (HTML "pesante" di iOS) prima del parse,
        // altrimenti il CSS finisce come testo nell'anteprima.
        value = value.sanitizeCrossPlatformHtml()
        value = HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        if (value.contains('<') && value.contains('>')) {
            value = HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }
    }

    value = value
        .replace(Regex("<[^>]+>"), " ")
        .replace('\u00A0', ' ')
        .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")

    return if (trimEdges) value.trim() else value
}
