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
import java.util.Date
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

    private val DATE_PATTERNS = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN),
        SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ITALIAN),
        SimpleDateFormat("yyyy-MM-dd", Locale.ITALIAN),
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ITALIAN),
        SimpleDateFormat("dd-MM-yyyy", Locale.ITALIAN),
        SimpleDateFormat("d MMM yyyy HH:mm", Locale.ITALIAN),
        SimpleDateFormat("d MMM yyyy", Locale.ITALIAN),
        SimpleDateFormat("EEE d MMM yyyy HH:mm", Locale.ITALIAN),
    )

    private val DATE_REGEX = Pattern.compile(
        """(?:\d{1,2}[/\-]\d{1,2}[/\-]\d{4}(?:\s+\d{1,2}:\d{2})?)|(?:\d{4}[/\-]\d{1,2}[/\-]\d{1,2}(?:\s+\d{1,2}:\d{2})?)|(?:\d{1,2}\s+\w+\s+\d{4}(?:\s+\d{1,2}:\d{2})?)""",
        Pattern.CASE_INSENSITIVE,
    )

    private val BOOKING_CODE_REGEX = Pattern.compile("""(?<!\w)[A-Z0-9]{5,8}(?!\w)""")

    suspend fun parse(context: Context, pdfBytes: ByteArray, fileName: String?): WalletParsedData {
        PDFBoxResourceLoader.init(context)

        val text = extractText(pdfBytes)
        val barcodeResult = extractBarcode(context, pdfBytes)
        val thumbnail = renderThumbnail(context, pdfBytes)

        val lowerText = text.lowercase(Locale.ITALIAN)
        val emitterKind = detectEmitterAndKind(lowerText)
        val emitter = emitterKind.first
        val kind = emitterKind.second

        val eventDate = extractBestFutureDate(text)
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

    private fun extractBestFutureDate(text: String): Long? {
        val now = System.currentTimeMillis()
        val matcher = DATE_REGEX.matcher(text)
        val candidates = mutableListOf<Long>()
        while (matcher.find()) {
            val raw = matcher.group() ?: continue
            for (fmt in DATE_PATTERNS) {
                runCatching {
                    val parsed = fmt.parse(raw)
                    if (parsed != null && parsed.time > now) candidates.add(parsed.time)
                }
            }
        }
        return candidates.minOrNull()
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
