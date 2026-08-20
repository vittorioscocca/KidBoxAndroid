package it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "LoyaltyCardColorExtractor"

/** Coppia di colori (hex `#RRGGBB`) per il gradiente della card. */
data class LoyaltyCardColors(
    val primaryHex: String,
    val secondaryHex: String,
)

/**
 * Estrae il colore dominante dal logo effettivamente scaricato di un brand, per
 * colorare la card con il colore reale del negozio invece che con quello curato
 * a mano nel catalogo.
 *
 * Implementato a mano su [Bitmap]: `androidx.palette` NON è una dipendenza del
 * progetto e non ha senso aggiungerla per una manciata di pixel.
 *
 * L'insidia di questa estrazione sono le favicon: sono in gran parte bianche o
 * trasparenti, quindi una media aritmetica dei pixel restituirebbe un grigio
 * slavato — il modo tipico di sbagliare questa cosa. Perciò i pixel quasi
 * bianchi, quasi neri, trasparenti e a bassissima saturazione vengono SCARTATI
 * prima di qualsiasi conteggio.
 *
 * Tutte le funzioni restituiscono `null` quando l'estrazione non è abbastanza
 * solida: il chiamante deve ricadere sui colori curati del catalogo, che sono
 * verificati e non vanno peggiorati da un'estrazione debole.
 */
object LoyaltyCardColorExtractor {

    /** Lato del bitmap campionato: sufficiente per il colore dominante, costo trascurabile. */
    private const val SAMPLE_SIZE = 40

    /** Ampiezza del bucket di quantizzazione per canale (8 livelli per canale). */
    private const val BUCKET = 32

    /** Sotto questa quota di pixel "utili" sul totale, il logo è troppo bianco/vuoto per fidarsi. */
    private const val MIN_USEFUL_PIXEL_RATIO = 0.02

    /** Saturazione minima del colore vincente: sotto è un grigio, non un colore di brand. */
    private const val MIN_SATURATION = 0.25f

    /** Luminosità relativa massima: sopra, il testo bianco sulla card non si leggerebbe. */
    private const val MAX_RELATIVE_LUMINANCE = 0.55

    /** Quanto è più scuro il secondario rispetto al primario (~37% di luminosità in meno). */
    private const val SECONDARY_DARKEN_FACTOR = 0.63f

    /**
     * Scarica il logo (via Coil, così sfrutta la cache: il logo è quasi certamente
     * già stato mostrato nel brand picker) ed estrae la coppia di colori.
     *
     * Non lancia mai e non blocca il salvataggio: oltre [timeoutMillis] — o se il
     * device è offline, o se il colore estratto non regge il testo bianco —
     * restituisce `null` e il chiamante prosegue con i colori del catalogo.
     */
    suspend fun colorsFromLogo(
        context: Context,
        logoURL: String?,
        timeoutMillis: Long = 2_500L,
    ): LoyaltyCardColors? {
        if (logoURL.isNullOrBlank()) return null
        return withTimeoutOrNull(timeoutMillis) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(logoURL)
                        // Un bitmap hardware non è leggibile pixel per pixel.
                        .allowHardware(false)
                        .size(128)
                        .build()
                    val result = Coil.imageLoader(context).execute(request)
                    val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@runCatching null
                    extract(bitmap)
                }.onFailure {
                    KBLog.data.warning("estrazione colore logo fallita: ${it.message}", TAG)
                }.getOrNull()
            }
        }
    }

    /**
     * Colore dominante di un bitmap già in memoria. `null` se il risultato non
     * supera la guardia di qualità (troppo desaturato o troppo chiaro).
     */
    fun extract(source: Bitmap): LoyaltyCardColors? = runCatching {
        val scaled = Bitmap.createScaledBitmap(source, SAMPLE_SIZE, SAMPLE_SIZE, true)
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        scaled.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        if (scaled !== source) scaled.recycle()

        // key del bucket -> [count, sumR, sumG, sumB]
        val buckets = HashMap<Int, IntArray>()
        var useful = 0
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a < 128) continue // trasparente
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (v >= 0.92f && s <= 0.20f) continue // quasi bianco
            if (v <= 0.12f) continue // quasi nero
            if (s < 0.18f) continue // grigio / bassissima saturazione

            useful++
            val key = (r / BUCKET shl 10) or (g / BUCKET shl 5) or (b / BUCKET)
            val acc = buckets.getOrPut(key) { IntArray(4) }
            acc[0]++
            acc[1] += r
            acc[2] += g
            acc[3] += b
        }

        if (useful < (pixels.size * MIN_USEFUL_PIXEL_RATIO)) return@runCatching null

        // Il più frequente, con un premio alla vividezza: fra due bucket di peso
        // simile vince quello più saturo — è quello che l'occhio riconosce come
        // "il colore" del brand.
        val best = buckets.values.maxByOrNull { acc ->
            val r = acc[1] / acc[0]
            val g = acc[2] / acc[0]
            val b = acc[3] / acc[0]
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            acc[0] * (0.6f + 0.8f * hsv[1])
        } ?: return@runCatching null

        val r = best[1] / best[0]
        val g = best[2] / best[0]
        val b = best[3] / best[0]

        // Guardia di qualità: la card ha testo bianco sopra il gradiente, quindi un
        // colore slavato o troppo chiaro è PEGGIO di quello curato del catalogo.
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        if (hsv[1] < MIN_SATURATION) return@runCatching null
        if (relativeLuminance(r, g, b) > MAX_RELATIVE_LUMINANCE) return@runCatching null

        val secondaryHsv = floatArrayOf(hsv[0], hsv[1], (hsv[2] * SECONDARY_DARKEN_FACTOR).coerceIn(0.05f, 1f))
        LoyaltyCardColors(
            primaryHex = hex(android.graphics.Color.rgb(r, g, b)),
            secondaryHex = hex(android.graphics.Color.HSVToColor(secondaryHsv)),
        )
    }.onFailure {
        KBLog.data.warning("estrazione colore fallita: ${it.message}", TAG)
    }.getOrNull()

    /** Luminanza relativa WCAG: è quella che dice se il bianco sopra si legge. */
    private fun relativeLuminance(r: Int, g: Int, b: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun hex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)
}
