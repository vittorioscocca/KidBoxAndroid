package it.vittorioscocca.kidbox.data.remote.notes

/**
 * Sanifica un frammento HTML proveniente da un altro client (tipicamente iOS)
 * in modo che `HtmlCompat.fromHtml` lo possa renderizzare senza mostrare CSS
 * come testo.
 *
 * `NSAttributedString.data(..., documentType: .html)` su iOS produce un
 * documento HTML completo con `<head><style>p.p1 { ... } span.s1 { ... }</style></head>`
 * e `class="pN"` sui paragrafi: il parser di `HtmlCompat` non comprende né il
 * blocco `<style>` né le classi CSS, e il testo del CSS finisce a schermo.
 *
 * Questa funzione:
 *   1. estrae il contenuto di `<body>...</body>` se presente;
 *   2. rimuove `<head>`, `<style>`, `<meta>`, `<link>`, `<title>`;
 *   3. rimuove gli attributi `class="..."` (e con apostrofi);
 *   4. rimuove `<!DOCTYPE ...>` e i tag `<html>...</html>` residui.
 *
 * Non tocca `<b>`, `<i>`, `<u>`, `<strike>`, `<p>`, `<br>`, `<ul>/<li>`,
 * `<span style="...">`, i link, ecc., che `HtmlCompat` gestisce nativamente.
 */
internal fun String.sanitizeCrossPlatformHtml(): String {
    if (isEmpty()) return this
    var s = this

    // 1) Estrai <body>...</body> se l'HTML è un documento completo.
    val bodyRegex = Regex("(?is)<body[^>]*>(.*?)</body>")
    bodyRegex.find(s)?.let { match ->
        s = match.groupValues[1]
    }

    // 2) Via ogni <head>, <style>, <meta>, <link>, <title>.
    s = s.replace(Regex("(?is)<head[^>]*>.*?</head>"), "")
    s = s.replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
    s = s.replace(Regex("(?is)<title[^>]*>.*?</title>"), "")
    s = s.replace(Regex("(?is)<meta[^>]*/?>"), "")
    s = s.replace(Regex("(?is)<link[^>]*/?>"), "")

    // 3) Via gli attributi class="..." / class='...' (entrambe le varianti).
    s = s.replace(Regex("\\s+class\\s*=\\s*\"[^\"]*\""), "")
    s = s.replace(Regex("\\s+class\\s*=\\s*'[^']*'"), "")

    // 4) Via <!DOCTYPE ...>, <html>, </html> residui.
    s = s.replace(Regex("(?i)<!doctype[^>]*>"), "")
    s = s.replace(Regex("(?i)</?html[^>]*>"), "")

    return s.trim()
}
