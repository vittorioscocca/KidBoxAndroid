package it.vittorioscocca.kidbox.data.wallet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class WalletParsedData(
    val suggestedTitle: String,
    val kind: WalletTicketKind,
    val emitter: String?,
    val eventDate: Long?,
    val location: String?,
    val bookingCode: String?,
    val barcodeText: String?,
    val barcodeFormat: String?,
    val notes: String?,
    val thumbnailBase64: String?,
)

object WalletPdfParser {
    private val EMITTER_FLIGHT = setOf("ryanair", "easyjet", "wizz", "volotea", "ita airways", "alitalia", "lufthansa", "air france", "british airways", "vueling")
    private val EMITTER_TRAIN = setOf("trenitalia", "italo", "frecciarossa", "frecciargento", "frecciabianca", "ntv", "railjet")
    private val EMITTER_FERRY = setOf("moby", "grimaldi", "tirrenia", "gnv", "medmar", "caremar", "snav", "alilauro", "gestour", "navigazione")
    private val EMITTER_BUS = setOf("flixbus", "itabus", "marino bus", "baltour", "autobus")
    private val EMITTER_CINEMA = setOf("uci cinema", "multisala", "the space", "odeon", "cinema")
    private val EMITTER_CONCERT = setOf("ticketmaster", "ticketone", "live nation", "eventim", "dice.fm")
    private val EMITTER_PARKING = setOf("apcoa", "gest", "saba", "interparking", "parkvia", "parcheggio")
    private val EMITTER_MUSEUM = setOf("museo", "galleria", "uffizi", "colosseo", "vaticani", "borghese")

    private val BOOKING_CODE_REGEX = Pattern.compile("""(?<!\w)[A-Z0-9]{5,8}(?!\w)""")

    private const val DAY_MS = 24L * 60 * 60 * 1000

    suspend fun parse(context: Context, pdfBytes: ByteArray, fileName: String?): WalletParsedData {
        PDFBoxResourceLoader.init(context)

        val text = extractText(pdfBytes)
        val barcodeResult = extractBarcode(context, pdfBytes)
        val thumbnail = renderThumbnail(context, pdfBytes)

        val lowerText = text.lowercase(Locale.ITALIAN)
        val emitterKind = detectEmitterAndKind(lowerText)
        val emitter = emitterKind.first
        val kind = emitterKind.second

        val eventDate = extractWalletEventMillis(normalizePdfPlainText(text))
        val location = extractLocation(text)
        val bookingCode = extractBookingCode(text, barcodeResult?.first)
        val notes = extractNotes(text)

        val suggestedTitle = when {
            !fileName.isNullOrBlank() -> fileName.removeSuffix(".pdf").trim()
            !emitter.isNullOrBlank() -> "${kind.displayName} · $emitter"
            else -> kind.displayName
        }

        return WalletParsedData(
            suggestedTitle = suggestedTitle,
            kind = kind,
            emitter = emitter,
            eventDate = eventDate,
            location = location,
            bookingCode = bookingCode,
            barcodeText = barcodeResult?.first,
            barcodeFormat = barcodeResult?.second,
            notes = notes,
            thumbnailBase64 = thumbnail,
        )
    }

    private fun extractText(pdfBytes: ByteArray): String {
        return runCatching {
            PDDocument.load(pdfBytes).use { doc ->
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = minOf(8, doc.numberOfPages)
                stripper.sortByPosition = true
                stripper.getText(doc)
            }
        }.getOrElse { "" }
    }

    private suspend fun extractBarcode(context: Context, pdfBytes: ByteArray): Pair<String, String>? {
        val tmpFile = File(context.cacheDir, "wallet_scan_${System.currentTimeMillis()}.pdf")
        return try {
            tmpFile.writeBytes(pdfBytes)
            val fd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            var result: Pair<String, String>? = null
            for (pageIndex in 0 until minOf(3, renderer.pageCount)) {
                val page = renderer.openPage(pageIndex)
                val bitmap = Bitmap.createBitmap(1800, (1800 * page.height / page.width.coerceAtLeast(1)), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                val scanned = scanBarcode(bitmap)
                bitmap.recycle()
                if (scanned != null) {
                    result = scanned
                    break
                }
            }
            renderer.close()
            fd.close()
            result
        } catch (_: Exception) {
            null
        } finally {
            tmpFile.delete()
        }
    }

    private suspend fun scanBarcode(bitmap: Bitmap): Pair<String, String>? =
        suspendCancellableCoroutine { cont ->
            val scanner = BarcodeScanning.getClient()
            val image = InputImage.fromBitmap(bitmap, 0)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                    if (first != null) {
                        val fmt = barcodeFormatName(first.format)
                        cont.resume(Pair(first.rawValue!!, fmt))
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }

    private fun barcodeFormatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        Barcode.FORMAT_PDF417 -> "PDF_417"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        else -> "UNKNOWN"
    }

    private fun renderThumbnail(context: Context, pdfBytes: ByteArray): String? {
        val tmpFile = File(context.cacheDir, "wallet_thumb_${System.currentTimeMillis()}.pdf")
        return try {
            tmpFile.writeBytes(pdfBytes)
            val fd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            if (renderer.pageCount == 0) {
                renderer.close()
                fd.close()
                return null
            }
            val page = renderer.openPage(0)
            val w = 400
            val h = (w * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            fd.close()
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
            bitmap.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        } finally {
            tmpFile.delete()
        }
    }

    private fun detectEmitterAndKind(lowerText: String): Pair<String?, WalletTicketKind> {
        for (kw in EMITTER_FLIGHT) {
            if (lowerText.contains(kw)) return Pair(kw.replaceFirstChar { it.uppercase() }, WalletTicketKind.FLIGHT)
        }
        for (kw in EMITTER_TRAIN) {
            if (lowerText.contains(kw)) return Pair(kw.replaceFirstChar { it.uppercase() }, WalletTicketKind.TRAIN)
        }
        for (kw in EMITTER_FERRY) {
            if (lowerText.contains(kw)) return Pair(kw.replaceFirstChar { it.uppercase() }, WalletTicketKind.FERRY)
        }
        for (kw in EMITTER_BUS) {
            if (lowerText.contains(kw)) return Pair(kw.replaceFirstChar { it.uppercase() }, WalletTicketKind.BUS)
        }
        for (kw in EMITTER_CINEMA) {
            if (lowerText.contains(kw)) return Pair(null, WalletTicketKind.CINEMA)
        }
        for (kw in EMITTER_CONCERT) {
            if (lowerText.contains(kw)) return Pair(null, WalletTicketKind.CONCERT)
        }
        for (kw in EMITTER_PARKING) {
            if (lowerText.contains(kw)) return Pair(null, WalletTicketKind.PARKING)
        }
        for (kw in EMITTER_MUSEUM) {
            if (lowerText.contains(kw)) return Pair(null, WalletTicketKind.MUSEUM)
        }
        return Pair(null, WalletTicketKind.OTHER)
    }

    private fun normalizePdfPlainText(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00a0', ' ')
            .replace(",", " ")

    /** Allineato a iOS `WalletPDFParser`: più estrattori + raffinamento orario dopo mezzanotte. */
    private fun extractWalletEventMillis(normalized: String): Long? {
        val candidates = ArrayList<Long>()
        candidates.addAll(extractInlineDates(normalized))
        candidates.addAll(extractAdjacentDateAndTime(normalized))
        candidates.addAll(extractLabeledDateTimeLines(normalized))
        candidates.addAll(extractItalianLongMonthDates(normalized))
        candidates.addAll(extractDepartureContextDates(normalized))
        val nowSlack = System.currentTimeMillis() - DAY_MS
        val best = candidates.filter { it >= nowSlack }.minOrNull()
            ?: candidates.minOrNull()
        return best?.let { refineMidnightWithNearbyTime(normalized, it) }
    }

    private fun parsingLocales(): Array<Locale> = arrayOf(Locale.getDefault(), Locale.ITALY, Locale.US)

    private fun parseCombinedDateTime(trimmed: String): Long? {
        val t = trimmed.trim()
        if (t.isEmpty()) return null
        val isoFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
        )
        for (pat in isoFormats) {
            val sdf = SimpleDateFormat(pat, Locale.US).apply { isLenient = true }
            runCatching { sdf.parse(t) }.getOrNull()?.time?.let { return it }
        }
        val normalized = t.replace('-', '/').replace('.', '/')
        val hasTime = Pattern.compile("""\d{1,2}:\d{2}(:\d{2})?""").matcher(normalized).find()
        val timeFormats = listOf(
            "d/M/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss",
            "d/M/yyyy HH:mm", "d/M/yy HH:mm",
            "dd/MM/yyyy HH:mm", "dd/MM/yy HH:mm",
            "d/M/yyyy HH.mm", "dd/MM/yyyy HH.mm",
            "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd HH.mm",
        )
        val dateOnlyFormats = listOf(
            "d/M/yyyy", "d/M/yy", "dd/MM/yyyy", "dd/MM/yy",
            "yyyy/MM/dd",
        )
        val formats = if (hasTime) timeFormats else dateOnlyFormats
        for (pat in formats) {
            for (loc in parsingLocales()) {
                val sdf = SimpleDateFormat(pat, loc).apply { isLenient = true }
                runCatching { sdf.parse(normalized) }.getOrNull()?.time?.let { return it }
            }
        }
        return null
    }

    private fun parseDate(datePart: String, timePart: String?): Long? {
        val calPart = datePart.replace('-', '/').replace('.', '/')
        val t = timePart?.replace('.', ':')
        return if (t.isNullOrBlank()) {
            parseCombinedDateTime(calPart)
        } else {
            parseCombinedDateTime("$calPart $t")
        }
    }

    private val INLINE_DATE_PATTERNS = listOf(
        Pattern.compile("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})(?:\s+(?:alle\s+)?(?:ore\s*)?(\d{1,2}[:.]\d{2}(?::\d{2})?))?""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(\d{4}-\d{2}-\d{2})[T\s]+(\d{1,2}:\d{2}(?::\d{2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(\d{4}-\d{2}-\d{2})(?:\s+(?:alle\s+)?(?:ore\s*)?(\d{1,2}[:.]\d{2}(?::\d{2})?))?""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(\d{1,2}\.\d{1,2}\.\d{2,4})(?:\s+(?:alle\s+)?(?:ore\s*)?(\d{1,2}[:.]\d{2}(?::\d{2})?))?""", Pattern.CASE_INSENSITIVE),
    )

    private fun extractInlineDates(text: String): List<Long> {
        val out = ArrayList<Long>()
        for (rx in INLINE_DATE_PATTERNS) {
            val m = rx.matcher(text)
            while (m.find()) {
                val datePart = m.group(1) ?: continue
                val timePart = m.group(2)?.takeIf { it.isNotBlank() }
                parseDate(datePart, timePart)?.let { out.add(it) }
            }
        }
        return out
    }

    private val DATE_TOKEN = Pattern.compile("""\b(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4})\b""")
    private val INLINE_AFTER_DATE_TIME = Pattern.compile("""(?i)(?:alle\s+)?(?:ore\s*)?(\d{1,2})[:.](\d{2})\b""")
    private val TIME_LINE = Pattern.compile("""(?i)^(?:ore\s*)?(\d{1,2})[:.](\d{2})\s*$""")
    private val LABELED_TIME_LINE = Pattern.compile("""(?i)^(?:ora|orario|time)\s*:\s*(\d{1,2})[:.](\d{2})\s*$""")

    private fun extractAdjacentDateAndTime(text: String): List<Long> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Long>()
        for (i in lines.indices) {
            val line = lines[i]
            val m = DATE_TOKEN.matcher(line)
            while (m.find()) {
                val datePart = m.group(1) ?: continue
                val tail = if (m.end() < line.length) line.substring(m.end()).trim() else ""
                val combined: String = run {
                    val im = INLINE_AFTER_DATE_TIME.matcher(tail)
                    if (im.find()) return@run "$datePart ${im.group(1)}:${im.group(2)}"
                    if (tail.isEmpty() || tail.length <= 24) {
                        val next = lines.getOrNull(i + 1).orEmpty()
                        var tm = TIME_LINE.matcher(next)
                        if (tm.matches()) return@run "$datePart ${tm.group(1)}:${tm.group(2)}"
                        tm = LABELED_TIME_LINE.matcher(next)
                        if (tm.matches()) return@run "$datePart ${tm.group(1)}:${tm.group(2)}"
                    }
                    datePart
                }
                parseCombinedDateTime(combined)?.let { out.add(it) }
            }
        }
        return out
    }

    private val LABELED_DATE_RX = Pattern.compile(
        """(?i)(?:data|date|partenza|departure|viaggio|travel)\s*[:\s.-]+\s*(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4})""",
    )
    private val LABELED_TIME_RX = Pattern.compile("""(?i)(?:ora|orario|time|hour)\s*[:\s.-]+\s*(\d{1,2})[:.](\d{2})""")

    private fun extractLabeledDateTimeLines(text: String): List<Long> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Long>()
        for (i in lines.indices) {
            val line = lines[i]
            val dm = LABELED_DATE_RX.matcher(line)
            if (!dm.find()) continue
            val datePart = dm.group(1) ?: continue
            var timePart: String? = null
            val tm0 = LABELED_TIME_RX.matcher(line)
            if (tm0.find()) {
                timePart = "${tm0.group(1)}:${tm0.group(2)}"
            } else if (i + 1 < lines.size) {
                val tm1 = LABELED_TIME_RX.matcher(lines[i + 1])
                if (tm1.find()) timePart = "${tm1.group(1)}:${tm1.group(2)}"
            }
            parseDate(datePart, timePart)?.let { out.add(it) }
        }
        return out
    }

    private val IT_MONTH_RX = Pattern.compile(
        """(?i)(\d{1,2})\s+(gennaio|febbraio|feb\.?|marzo|mar\.?|aprile|apr\.?|maggio|mag\.?|giugno|giu\.?|luglio|lug\.?|agosto|ago\.?|settembre|set\.?|ottobre|ott\.?|novembre|nov\.?|dicembre|dic\.?|gen\.?)\s+(\d{2,4})(?:\s*(?:alle\s*)?(?:ore\s*)?(\d{1,2})[:.](\d{2}))?""",
    )

    private val IT_MONTH_MAP = mapOf(
        "gennaio" to 1, "gen" to 1, "gen." to 1,
        "febbraio" to 2, "feb" to 2, "feb." to 2,
        "marzo" to 3, "mar" to 3, "mar." to 3,
        "aprile" to 4, "apr" to 4, "apr." to 4,
        "maggio" to 5, "mag" to 5, "mag." to 5,
        "giugno" to 6, "giu" to 6, "giu." to 6,
        "luglio" to 7, "lug" to 7, "lug." to 7,
        "agosto" to 8, "ago" to 8, "ago." to 8,
        "settembre" to 9, "set" to 9, "set." to 9,
        "ottobre" to 10, "ott" to 10, "ott." to 10,
        "novembre" to 11, "nov" to 11, "nov." to 11,
        "dicembre" to 12, "dic" to 12, "dic." to 12,
    )

    private fun extractItalianLongMonthDates(text: String): List<Long> {
        val out = ArrayList<Long>()
        val m = IT_MONTH_RX.matcher(text)
        while (m.find()) {
            val d = m.group(1)?.toIntOrNull() ?: continue
            val monName = m.group(2)?.lowercase(Locale.ITALIAN) ?: continue
            val mon = IT_MONTH_MAP[monName] ?: continue
            var y = m.group(3)?.toIntOrNull() ?: continue
            if (y < 100) y += 2000
            var timePart: String? = null
            if (m.groupCount() >= 5 && m.group(4) != null && m.group(5) != null) {
                timePart = "${m.group(4)}:${m.group(5)}"
            }
            val datePart = "$d/$mon/$y"
            parseDate(datePart, timePart)?.let { out.add(it) }
        }
        return out
    }

    private val DEPARTURE_KW = listOf("partenza", "imbarco", "boarding", "departure", "sailing", "navigazione")
    private val DEP_DATE_RX = Pattern.compile("""\b(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4})\b""")
    private val DEP_TIME_RX = Pattern.compile("""(?i)(?:alle\s*)?(?:ore\s*)?(\d{1,2})[:.](\d{2})\b""")

    private fun extractDepartureContextDates(text: String): List<Long> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Long>()
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.ITALIAN)
            if (!DEPARTURE_KW.any { lower.contains(it) }) continue
            val chunk = buildString {
                append(lines[i])
                if (i + 1 < lines.size) append(' ').append(lines[i + 1])
                if (i + 2 < lines.size) append(' ').append(lines[i + 2])
            }
            val dm = DEP_DATE_RX.matcher(chunk)
            if (!dm.find()) continue
            val datePart = dm.group(1) ?: continue
            val tail = if (dm.end() < chunk.length) chunk.substring(dm.end()) else ""
            val tm = DEP_TIME_RX.matcher(tail)
            val timePart = if (tm.find()) "${tm.group(1)}:${tm.group(2)}" else null
            parseDate(datePart, timePart)?.let { out.add(it) }
        }
        return out
    }

    private val TIME_WINDOW_RX = Pattern.compile("""(?:^|[^\d])([01]?\d|2[0-3])[:.]([0-5]\d)(?!\d)""")

    private fun bestPlausibleTimeAfterDate(window: String): Pair<Int, Int>? {
        val m = TIME_WINDOW_RX.matcher(window)
        var lastGood: Pair<Int, Int>? = null
        while (m.find()) {
            val h = m.group(1)?.toIntOrNull() ?: continue
            val min = m.group(2)?.toIntOrNull() ?: continue
            if (h != 0 || min != 0) lastGood = h to min
        }
        return lastGood
    }

    private fun refineMidnightWithNearbyTime(fullText: String, dateMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dateMs
        if (cal.get(Calendar.HOUR_OF_DAY) != 0 || cal.get(Calendar.MINUTE) != 0) return dateMs
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        if (day <= 0 || month <= 0 || year <= 0) return dateMs
        val needles = listOf(
            String.format(Locale.US, "%02d/%02d/%04d", day, month, year),
            String.format(Locale.US, "%d/%d/%04d", day, month, year),
            String.format(Locale.US, "%d/%02d/%04d", day, month, year),
            String.format(Locale.US, "%02d/%d/%04d", day, month, year),
            String.format(Locale.US, "%02d-%02d-%04d", day, month, year),
            String.format(Locale.US, "%d-%d-%04d", day, month, year),
            String.format(Locale.US, "%02d.%02d.%04d", day, month, year),
        )
        for (needle in needles) {
            val idx = fullText.indexOf(needle)
            if (idx < 0) continue
            val start = idx + needle.length
            val end = minOf(fullText.length, start + 900)
            if (start >= end) continue
            val window = fullText.substring(start, end)
            val hm = bestPlausibleTimeAfterDate(window) ?: continue
            cal.set(Calendar.HOUR_OF_DAY, hm.first)
            cal.set(Calendar.MINUTE, hm.second)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        return dateMs
    }

    private fun extractLocation(text: String): String? {
        val lines = text.lines()
        for (line in lines) {
            val lower = line.trim().lowercase(Locale.ITALIAN)
            if (lower.startsWith("da ") || lower.startsWith("a ") ||
                lower.contains("stazione") || lower.contains("gate") ||
                lower.contains("partenza") || lower.contains("arrivo")
            ) {
                val trimmed = line.trim()
                if (trimmed.length in 5..120) return trimmed
            }
        }
        return null
    }

    private fun extractBookingCode(text: String, barcodeText: String?): String? {
        if (!barcodeText.isNullOrBlank() && barcodeText.length in 5..8 &&
            barcodeText.all { it.isLetterOrDigit() } && barcodeText == barcodeText.uppercase()
        ) {
            return barcodeText
        }
        val matcher = BOOKING_CODE_REGEX.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group() ?: continue
            if (candidate.any { it.isLetter() } && candidate.any { it.isDigit() }) {
                return candidate
            }
        }
        return null
    }

    private fun extractNotes(text: String): String? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 8 }
        val interesting = lines.filter { line ->
            val lower = line.lowercase(Locale.ITALIAN)
            lower.contains("via") || lower.contains("p.iva") || lower.contains("iva") ||
                lower.contains("tel") || lower.contains("email") || lower.contains("info")
        }
        if (interesting.isEmpty()) return null
        return interesting.take(5).joinToString("\n")
    }
}
