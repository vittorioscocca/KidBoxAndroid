package it.vittorioscocca.kidbox.data.wallet

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import it.vittorioscocca.kidbox.domain.model.PatenteCategory
import it.vittorioscocca.kidbox.util.KBLog
import java.time.LocalDate
import kotlinx.coroutines.tasks.await

/** Risultato dell'estrazione automatica da un documento d'identità scansionato. Porting 1:1 di `WalletDocumentExtraction` (iOS). */
data class WalletDocumentExtraction(
    val codiceFiscale: String? = null,
    val holderName: String? = null,
    val birthInfo: String? = null,
    val documentNumber: String? = null,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    /** Solo patente: categorie possedute con rilascio (col.10) e scadenza (col.11) dalla tabella sul retro. */
    val patenteCategories: List<PatenteCategory> = emptyList(),
    val rawText: String = "",
)

private data class OcrLine(val text: String, val rect: Rect)
private data class PatenteFrontFields(
    val surname: String? = null,
    val name: String? = null,
    val birthInfo: String? = null,
    val number: String? = null,
    val categoryCodes: List<String> = emptyList(),
)

private const val TAG = "WalletDocumentExtractor"

/**
 * Estrazione automatica dei dati principali da una scansione di documento
 * d'identità (Tessera Sanitaria, CIE, Carta d'identità, Patente, Passaporto,
 * Codice Fiscale). Porting 1:1 di `WalletDocumentExtractor.swift` — stesso
 * pattern Codice Fiscale, stesse euristiche patente (campi numerati fronte +
 * tabella per bounding-box sul retro), stesse regole "niente CF per la patente".
 *
 * ML Kit (Text Recognition + Barcode Scanning) è già in dipendenza nel
 * progetto; le [Rect] dei bounding box sono in pixel (non normalizzate come
 * su iOS Vision), quindi l'associazione riga→categoria usa una soglia
 * relativa all'altezza media delle righe invece di un valore fisso.
 */
object WalletDocumentExtractor {

    private val CF_REGEX = Regex("[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPRT][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]")
    private val EXPIRY_LABEL = Regex("(scadenza|validit[aà]|expir|valid\\s*until|date\\s*of\\s*expiry)", RegexOption.IGNORE_CASE)
    private val ISSUE_LABEL = Regex("(rilasci|emission|emess|date\\s*of\\s*issue|issued)", RegexOption.IGNORE_CASE)
    private val FULL_DATE_REGEX = Regex("\\b(\\d{1,2})[/\\-. ](\\d{1,2})[/\\-. ](\\d{2,4})\\b")
    private val MONTH_YEAR_REGEX = Regex("\\b(\\d{1,2})[/\\-. ](\\d{4})\\b")

    private val PATENTE_CATEGORIES = listOf(
        "AM", "A1", "A2", "A", "B1", "B", "C1", "C", "D1", "D", "BE", "C1E", "CE", "D1E", "DE",
    )
    private val PATENTE_CATEGORY_SET = PATENTE_CATEGORIES.toSet()

    /** Analizza le pagine scansionate (fronte/retro). `kind` abilita le euristiche patente. */
    suspend fun extract(bitmaps: List<Bitmap>, kind: DocumentKind = DocumentKind.ALTRO): WalletDocumentExtraction {
        var codiceFiscale: String? = null
        val fullText = StringBuilder()
        var patenteCategories: List<PatenteCategory> = emptyList()

        for (bitmap in bitmaps) {
            if (codiceFiscale == null) {
                codiceFiscale = detectCodiceFiscaleBarcode(bitmap)
            }
            val lines = recognizeLines(bitmap)
            val text = lines.joinToString("\n") { it.text }
            if (text.isNotEmpty()) {
                if (fullText.isNotEmpty()) fullText.append('\n')
                fullText.append(text)
            }
            if (kind == DocumentKind.PATENTE) {
                val cats = parsePatenteTable(lines)
                if (cats.isNotEmpty()) patenteCategories = cats
            }
        }

        return buildExtraction(fullText.toString(), codiceFiscale, kind, patenteCategories)
    }

    /** Cerca un Codice Fiscale in un testo qualsiasi (es. `extractedText` già salvato). */
    fun codiceFiscale(text: String): String? = CF_REGEX.find(text.uppercase())?.value

    private fun buildExtraction(
        fullText: String,
        barcodeCF: String?,
        kind: DocumentKind,
        patenteCategoriesFromTable: List<PatenteCategory>,
    ): WalletDocumentExtraction {
        var cf = barcodeCF ?: CF_REGEX.find(fullText.uppercase())?.value

        val dates = detectDates(fullText)
        var issue = dateNearLabel(ISSUE_LABEL, dates, fullText) ?: monthYearNearLabel(ISSUE_LABEL, fullText)
        var expiry = dateNearLabel(EXPIRY_LABEL, dates, fullText)
            ?: monthYearNearLabel(EXPIRY_LABEL, fullText)
            ?: plausibleExpiry(dates)

        var holder = extractHolderName(fullText)
        var docNumber = extractDocumentNumber(fullText)
        var birthInfo: String? = null
        var categories = patenteCategoriesFromTable

        // Patente: niente CF, niente date da 4a/4b/4c (sono per-categoria dal retro).
        if (kind == DocumentKind.PATENTE) {
            val p = parsePatenteFront(fullText)
            val name = listOfNotNull(p.name, p.surname).filter { it.isNotEmpty() }.joinToString(" ")
            if (name.isNotEmpty()) holder = name
            p.number?.let { docNumber = it }
            birthInfo = p.birthInfo
            issue = null
            expiry = null
            cf = null
            if (categories.isEmpty() && p.categoryCodes.isNotEmpty()) {
                categories = p.categoryCodes.map { PatenteCategory(code = it) }
            }
        }

        return WalletDocumentExtraction(
            codiceFiscale = cf,
            holderName = holder,
            birthInfo = birthInfo,
            documentNumber = docNumber,
            issueDate = issue,
            expiryDate = expiry,
            patenteCategories = categories,
            rawText = fullText,
        )
    }

    // region Barcode (Codice Fiscale)

    private suspend fun detectCodiceFiscaleBarcode(bitmap: Bitmap): String? {
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_39).build()
        val scanner = BarcodeScanning.getClient(options)
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = scanner.process(image).await()
            barcodes.firstNotNullOfOrNull { b ->
                val payload = b.rawValue?.trim()?.uppercase()
                if (!payload.isNullOrEmpty() && CF_REGEX.matches(payload)) payload else null
            }
        }.onFailure { KBLog.data.warning("barcode detect failed: ${it.message}", TAG) }.getOrNull()
    }

    // endregion

    // region OCR

    private suspend fun recognizeLines(bitmap: Bitmap): List<OcrLine> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            result.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val rect = line.boundingBox ?: return@mapNotNull null
                    OcrLine(line.text, rect)
                }
            }
        }.onFailure { KBLog.data.warning("OCR failed: ${it.message}", TAG) }.getOrElse { emptyList() }
    }

    // endregion

    // region Date (rilascio / scadenza)

    private data class LocatedDate(val date: LocalDate, val lineIndex: Int)

    private fun detectDates(text: String): List<LocatedDate> {
        val lines = text.split("\n")
        val out = mutableListOf<LocatedDate>()
        lines.forEachIndexed { idx, line ->
            FULL_DATE_REGEX.findAll(line).forEach { m ->
                parseFullDate(m).let { out.add(LocatedDate(it, idx)) }
            }
        }
        return out
    }

    private fun parseFullDate(m: MatchResult): LocalDate {
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        var year = m.groupValues[3].toInt()
        if (year < 100) year += if (year <= 49) 2000 else 1900
        return runCatching { LocalDate.of(year, month, day) }.getOrDefault(LocalDate.MIN)
    }

    /** Cerca una data sulla stessa riga o su quella successiva a un'etichetta. */
    private fun dateNearLabel(label: Regex, dates: List<LocatedDate>, text: String): LocalDate? {
        val lines = text.split("\n")
        lines.forEachIndexed { idx, line ->
            if (!label.containsMatchIn(line)) return@forEachIndexed
            val d = dates.firstOrNull { it.lineIndex == idx || it.lineIndex == idx + 1 }
            if (d != null && d.date != LocalDate.MIN) return d.date
        }
        return null
    }

    /** Fallback per la scadenza senza etichetta: la data più avanti nel tempo ma plausibile. */
    private fun plausibleExpiry(dates: List<LocatedDate>): LocalDate? {
        val now = LocalDate.now()
        val lower = now.minusYears(1)
        val upper = now.plusYears(20)
        return dates.map { it.date }
            .filter { it != LocalDate.MIN && !it.isBefore(lower) && !it.isAfter(upper) }
            .maxOrNull()
    }

    /** Fallback "mm/aaaa" accanto a un'etichetta, interpretato come primo giorno del mese. */
    private fun monthYearNearLabel(label: Regex, text: String): LocalDate? {
        val lines = text.split("\n")
        lines.forEachIndexed { idx, line ->
            if (!label.containsMatchIn(line)) return@forEachIndexed
            val candidates = listOfNotNull(line, lines.getOrNull(idx + 1))
            for (candidate in candidates) {
                val m = MONTH_YEAR_REGEX.find(candidate) ?: continue
                val month = m.groupValues[1].toIntOrNull() ?: continue
                val year = m.groupValues[2].toIntOrNull() ?: continue
                if (month !in 1..12) continue
                return runCatching { LocalDate.of(year, month, 1) }.getOrNull()
            }
        }
        return null
    }

    // endregion

    // region Patente: campi numerati fronte (1,2,3,5,9) — non 4a/4b/4c

    private fun parsePatenteFront(text: String): PatenteFrontFields {
        var surname: String? = null
        var name: String? = null
        var birthInfo: String? = null
        var number: String? = null
        var categoryCodes: List<String> = emptyList()

        for (rawLine in text.split("\n")) {
            val line = rawLine.trim()
            fieldValue(line, "^1\\s*[.)]?\\s+")?.let { surname = cleanNameToken(it) ?: surname }
                ?: fieldValue(line, "^2\\s*[.)]?\\s+")?.let { name = cleanNameToken(it) ?: name }
                ?: fieldValue(line, "^3\\s*[.)]?\\s+")?.let { if (it.length >= 4) birthInfo = it }
                ?: fieldValue(line, "^5\\s*[.)]?\\s*")?.let { v ->
                    val m = Regex("[A-Z0-9]{6,12}").find(v.uppercase())?.value
                    if (m != null && m.any { c -> c.isDigit() } && m.any { c -> c.isLetter() }) number = m
                }
                ?: fieldValue(line, "^9\\s*[.)]?\\s*")?.let { categoryCodes = parseFrontCategoryCodes(it) }
        }
        return PatenteFrontFields(surname, name, birthInfo, number, categoryCodes)
    }

    private fun fieldValue(line: String, prefixPattern: String): String? {
        val regex = Regex(prefixPattern)
        val match = regex.find(line) ?: return null
        val value = line.substring(match.range.last + 1).trim()
        return value.ifEmpty { null }
    }

    private fun parseFrontCategoryCodes(text: String): List<String> =
        text.uppercase().split(Regex("[ ,;/\t]+")).map { it.trim() }.filter { PATENTE_CATEGORY_SET.contains(it) }

    // endregion

    // region Patente: tabella retro (col.9 categoria, col.10 rilascio, col.11 scadenza)

    /**
     * Associa per vicinanza verticale (bounding box in pixel) la categoria
     * (riga con un token che combacia con un codice noto) alle date che
     * compaiono sulla stessa riga: la prima (più a sinistra) è il rilascio
     * (colonna 10), la seconda la scadenza (colonna 11). Solo le categorie con
     * almeno una data sono restituite (= effettivamente possedute).
     */
    private fun parsePatenteTable(lines: List<OcrLine>): List<PatenteCategory> {
        if (lines.isEmpty()) return emptyList()

        data class Anchor(val code: String, val centerY: Float)
        data class DateTok(val date: LocalDate, val centerY: Float, val x: Int)

        val anchors = mutableListOf<Anchor>()
        val dateToks = mutableListOf<DateTok>()

        for (line in lines) {
            val centerY = line.rect.exactCenterY()
            val tokens = line.text.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
            tokens.firstOrNull { PATENTE_CATEGORY_SET.contains(it) }?.let { code ->
                anchors.add(Anchor(code, centerY))
            }
            FULL_DATE_REGEX.findAll(line.text).forEachIndexed { i, m ->
                val date = parseFullDate(m)
                if (date != LocalDate.MIN) dateToks.add(DateTok(date, centerY, line.rect.left + i))
            }
        }
        if (anchors.isEmpty() || dateToks.isEmpty()) return emptyList()

        // Soglia relativa: 0.8x l'altezza media delle righe (in pixel, adattivo a qualsiasi risoluzione).
        val avgLineHeight = lines.map { it.rect.height() }.average().toFloat().coerceAtLeast(1f)
        val threshold = avgLineHeight * 0.8f

        val perCode = mutableMapOf<String, MutableList<Pair<Int, LocalDate>>>()
        for (tok in dateToks) {
            val nearest = anchors.minByOrNull { kotlin.math.abs(it.centerY - tok.centerY) } ?: continue
            if (kotlin.math.abs(nearest.centerY - tok.centerY) > threshold) continue
            perCode.getOrPut(nearest.code) { mutableListOf() }.add(tok.x to tok.date)
        }

        return PATENTE_CATEGORIES.mapNotNull { code ->
            val dates = perCode[code]?.sortedBy { it.first } ?: return@mapNotNull null
            if (dates.isEmpty()) return@mapNotNull null
            PatenteCategory(
                code = code,
                issueDate = dates.getOrNull(0)?.second,
                expiryDate = dates.getOrNull(1)?.second,
            )
        }
    }

    // endregion

    // region Nome / cognome titolare (documenti diversi dalla patente)

    private fun extractHolderName(text: String): String? {
        val surname = valueForLabel(text, "(?i)^\\s*(cognome|surname)\\b", null)
        val given = valueForLabel(text, "(?i)^\\s*(nome|given\\s*names?)\\b", "(?i)cognome|surname")
        val parts = listOfNotNull(given, surname).filter { it.isNotEmpty() }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun valueForLabel(text: String, labelPattern: String, excludingPattern: String?): String? {
        val label = Regex(labelPattern)
        val excluding = excludingPattern?.let { Regex(it) }
        val lines = text.split("\n")
        lines.forEachIndexed { idx, line ->
            if (excluding != null && excluding.containsMatchIn(line)) return@forEachIndexed
            if (!label.containsMatchIn(line)) return@forEachIndexed
            val stripped = line.replace(
                Regex("(?i)^\\s*(cognome|surname|nome|given\\s*names?)[\\s/]*(cognome|surname|nome|given\\s*names?)?[:\\s/]*"),
                "",
            ).trim()
            cleanNameToken(stripped)?.let { return it }
            lines.getOrNull(idx + 1)?.let { cleanNameToken(it) }?.let { return it }
        }
        return null
    }

    private fun cleanNameToken(raw: String): String? {
        val t = raw.trim()
        if (t.length < 2 || t.length > 40) return null
        if (!Regex("^[A-Za-zÀ-ÿ' .]+$").matches(t)) return null
        return t
    }

    // endregion

    // region Numero documento (non patente)

    private fun extractDocumentNumber(text: String): String? {
        val label = Regex("(?i)(numero|document|carta|passaport|n[°.]?\\s*documento)")
        val token = Regex("\\b([A-Z0-9]{5,15})\\b")
        val lines = text.split("\n")
        lines.forEachIndexed { idx, line ->
            if (!label.containsMatchIn(line)) return@forEachIndexed
            val candidates = listOfNotNull(line, lines.getOrNull(idx + 1))
            for (candidate in candidates) {
                val m = token.find(candidate.uppercase())?.value ?: continue
                if (m.any { it.isDigit() } && !Regex("^\\d{1,2}[/\\-.]\\d{1,2}").containsMatchIn(m)) {
                    return m
                }
            }
        }
        return null
    }

    // endregion
}
