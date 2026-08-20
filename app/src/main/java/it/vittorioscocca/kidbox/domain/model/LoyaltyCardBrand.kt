package it.vittorioscocca.kidbox.domain.model

import android.content.Context
import it.vittorioscocca.kidbox.util.KBLog
import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray

/**
 * Singolo brand del catalogo carte fedeltà. Mirror di `LoyaltyCardBrand` (iOS).
 *
 * Non esiste un'API pubblica affidabile con loghi/colori dei negozi
 * (CardPlus/Stocard usano database proprietari): il catalogo è un JSON
 * bundle interno curato con nome + colori brand + `domain`/`logoURL` per
 * risolvere il logo ufficiale a runtime (nessun asset immagine impacchettato:
 * il logo viene caricato dall'URL, con fallback al wordmark testuale quando
 * manca o fallisce). Stesso file/contenuto di `LoyaltyCardCatalog.json` su iOS, per
 * coerenza cross-platform (stesso `brandId` produce la stessa card).
 */
data class LoyaltyCardBrand(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val defaultBarcodeFormat: String = "code128",
    val isPopular: Boolean = false,
    /** Dominio ufficiale del brand (es. "conad.it"), usato per risolvere il logo. `null` se ignoto. */
    val domain: String? = null,
    /** URL completo del logo del brand (favicon ad alta risoluzione). `null` → fallback wordmark testuale. */
    val logoURL: String? = null,
) {
    /** Prima lettera del nome, normalizzata (maiuscola, senza accenti) — per l'indice A-Z del picker. */
    val indexLetter: String
        get() {
            val stripped = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
            val first = stripped.firstOrNull { it.isLetter() } ?: return "#"
            return first.uppercaseChar().toString()
        }
}

/** Loader del catalogo `LoyaltyCardCatalog.json` dagli assets, con ricerca per nome/alias e raggruppamento alfabetico. */
object LoyaltyCardCatalog {

    @Volatile
    private var cached: List<LoyaltyCardBrand>? = null

    /** Catalogo caricato una sola volta (immutabile, ordinato per nome). Richiede il [Context] al primo accesso. */
    fun all(context: Context): List<LoyaltyCardBrand> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = load(context)
            cached = loaded
            return loaded
        }
    }

    private fun load(context: Context): List<LoyaltyCardBrand> {
        return runCatching {
            val json = context.assets.open("LoyaltyCardCatalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = JSONArray(json)
            val result = ArrayList<LoyaltyCardBrand>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val aliases = ArrayList<String>()
                o.optJSONArray("aliases")?.let { arr ->
                    for (j in 0 until arr.length()) aliases.add(arr.getString(j))
                }
                result.add(
                    LoyaltyCardBrand(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        aliases = aliases,
                        primaryColorHex = o.getString("primaryColorHex"),
                        secondaryColorHex = o.optString("secondaryColorHex", o.getString("primaryColorHex")),
                        defaultBarcodeFormat = o.optString("defaultBarcodeFormat", "code128"),
                        isPopular = o.optBoolean("isPopular", false),
                        domain = o.optString("domain", "").ifBlank { null },
                        logoURL = o.optString("logoURL", "").ifBlank { null },
                    ),
                )
            }
            result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }.getOrElse {
            KBLog.data.warning("LoyaltyCardCatalog: load failed err=${it.message}", "LoyaltyCardCatalog")
            emptyList()
        }
    }

    fun brand(context: Context, id: String): LoyaltyCardBrand? = all(context).firstOrNull { it.id == id }

    /** Brand marcati come "popolari" (sezione in alto del picker). */
    fun popular(context: Context): List<LoyaltyCardBrand> = all(context).filter { it.isPopular }

    /** Ricerca case/diacritic-insensitive su nome + alias. */
    fun search(context: Context, query: String): List<LoyaltyCardBrand> {
        val q = query.trim()
        val all = all(context)
        if (q.isEmpty()) return all
        val qLower = q.lowercase(Locale.getDefault())
        return all.filter { brand ->
            brand.name.lowercase(Locale.getDefault()).contains(qLower) ||
                brand.aliases.any { it.lowercase(Locale.getDefault()).contains(qLower) }
        }
    }

    /** Raggruppamento alfabetico per l'indice laterale A-Z del picker. */
    fun groupedByIndexLetter(brands: List<LoyaltyCardBrand>): List<Pair<String, List<LoyaltyCardBrand>>> {
        val grouped = brands.groupBy { it.indexLetter }
        return grouped.keys.sorted().map { letter ->
            letter to (grouped[letter] ?: emptyList()).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }
}
