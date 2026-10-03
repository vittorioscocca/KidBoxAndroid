package it.vittorioscocca.kidbox.ui.screens.news

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.HousePaymentDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Il riassunto da cui partono le offerte su misura: bollette (Casa → scadenze
 * e pagamenti, più il testo letto delle bollette caricate) e lista della
 * spesa. Si costruisce sul telefono, l'unico posto in cui i dati sono in
 * chiaro, e parte solo quando l'utente tocca «Cerca offerte»: il server lo usa
 * per le ricerche e non lo salva.
 *
 * Escono tipo di bolletta, fornitore, importo, fine del contratto e le sole
 * righe con consumi e prezzi. Non escono mai nomi, indirizzi, codici cliente,
 * POD/PDR, IBAN, email. Stesse regole di `NewsBriefBuilder.swift`.
 */
data class NewsBrief(
    val bills: List<Bill>,
    val grocery: List<String>,
) {
    data class Bill(
        val type: String,
        val supplier: String?,
        val amountEur: Double?,
        val periodMonths: Int?,
        val contractEnd: String?,
        val excerpt: String?,
    )

    val isEmpty: Boolean get() = bills.isEmpty() && grocery.isEmpty()

    fun toMap(): Map<String, Any> = mapOf(
        "bills" to bills.map { b ->
            buildMap<String, Any> {
                put("type", b.type)
                b.supplier?.let { put("supplier", it) }
                b.amountEur?.let { put("amountEur", it) }
                b.periodMonths?.let { put("periodMonths", it) }
                b.contractEnd?.let { put("contractEnd", it) }
                b.excerpt?.let { put("excerpt", it) }
            }
        },
        "grocery" to grocery,
    )
}

@Singleton
class NewsBriefBuilder @Inject constructor(
    private val housePaymentDao: HousePaymentDao,
    private val documentDao: KBDocumentDao,
    private val groceryItemDao: KBGroceryItemDao,
) {
    suspend fun build(familyId: String): NewsBrief {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return NewsBrief(bills(familyId, uid), grocery(familyId))
    }

    private suspend fun bills(familyId: String, uid: String?): List<NewsBrief.Bill> {
        val payments = housePaymentDao.observeByFamily(familyId).first().filter { !it.isDeleted }
        val documents = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy, uid) }
            .sortedByDescending { it.updatedAtEpochMillis }

        val out = mutableListOf<NewsBrief.Bill>()
        val usedDocs = mutableSetOf<String>()
        for (p in payments) {
            if (!p.typeRaw.equals("bolletta", ignoreCase = true)) continue
            val type = p.subtypeRaw?.lowercase()?.takeIf { it in BILL_TYPES } ?: continue
            val attached = documents.firstOrNull { (it.notes ?: "").contains("housePayment:${p.id}") && !it.extractedText.isNullOrBlank() }
            attached?.let { usedDocs += it.id }
            out += NewsBrief.Bill(
                type = type,
                supplier = p.fornitore?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
                amountEur = p.importo?.takeIf { it > 0 },
                periodMonths = if (p.giornoDiScadenzaMensile != null) 1 else null,
                contractEnd = p.dataScadenzaContratto?.let { NewsDates.key(Date(it)) },
                excerpt = excerpt(attached?.extractedText),
            )
        }
        // Bollette caricate in Documenti senza una scadenza in Casa: si
        // riconoscono dal testo letto, al massimo una per tipo.
        val yearAgo = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        for (doc in documents) {
            if (out.size >= 6) break
            if (doc.updatedAtEpochMillis < yearAgo || doc.id in usedDocs) continue
            val text = doc.extractedText ?: continue
            if (text.length <= 80) continue
            val type = classify(text) ?: continue
            if (out.any { it.type == type }) continue
            out += NewsBrief.Bill(type, supplier(text), null, null, null, excerpt(text))
        }
        return out.take(6)
    }

    private suspend fun grocery(familyId: String): List<String> {
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val names = mutableListOf<String>()
        groceryItemDao.observeByFamilyId(familyId).first()
            .filter { !it.isDeleted && (!it.isPurchased || (it.purchasedAtEpochMillis ?: 0L) >= monthAgo) }
            .sortedByDescending { it.updatedAtEpochMillis }
            .forEach { item ->
                val name = item.name.trim().take(40)
                if (name.isNotEmpty() && names.none { it.equals(name, ignoreCase = true) } && names.size < 30) names += name
            }
        return names
    }

    private fun visible(scope: String?, membersJson: String?, createdBy: String?, uid: String?): Boolean =
        KBVisibilityScope.isVisible(
            KBVisibilityScope.normalized(scope ?: KBVisibilityScope.FAMILY),
            runCatching { JSONArray(membersJson ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList()),
            createdBy?.takeIf { it.isNotBlank() },
            uid,
        )

    companion object {
        /** Le bollette che hanno un mercato o un bonus da cercare. */
        private val BILL_TYPES = setOf("luce", "gas", "acqua", "internet", "telefono")

        private val KNOWN_SUPPLIERS = listOf(
            "Enel", "Servizio Elettrico Nazionale", "A2A", "Edison", "Hera", "Iren", "Eni Plenitude", "Plenitude", "Sorgenia",
            "Acea", "Engie", "Illumia", "Octopus", "Iberdrola", "E.ON", "Axpo", "Wekiwi", "NeN", "Pulsee", "Dolomiti Energia",
            "Estra", "Optima", "Enegan", "Alperia", "AGSM", "Tate", "Metamer",
            "EDF", "TotalEnergies", "Ekwateur", "Mint Énergie", "Vattenfall",
            "Endesa", "Naturgy", "Repsol", "Holaluz",
        )

        /** Luce, gas o acqua dal testo di una bolletta; null se non lo è. */
        fun classify(text: String): String? {
            val t = text.lowercase()
            val isBill = listOf("bolletta", "fattura", "facture", "factura", "bill").any { it in t }
            if (!isBill) return null
            if ("kwh" in t && listOf("pod", "energia elettrica", "électricité", "electricidad", "luce").any { it in t }) return "luce"
            if ("smc" in t || ("pdr" in t && "gas" in t) || "gas naturale" in t || "gaz naturel" in t) return "gas"
            if (listOf("m3", "m³", " mc ").any { it in t } && listOf("acqua", "idric", "eau", "agua").any { it in t }) return "acqua"
            return null
        }

        private fun supplier(text: String): String? =
            KNOWN_SUPPLIERS.firstOrNull { text.contains(it, ignoreCase = true) }

        private val KEEP = listOf(
            "kwh", "smc", "m3", "m³", "€/", "eur/", "prezzo", "quota fissa", "quota energia", "consum", "potenza",
            "offerta", "tariffa", "f1", "f2", "f3", "spesa per", "totale", "periodo", "prix", "consommation", "abonnement",
            "precio", "consumo", "término",
        )
        private val DROP = listOf(
            "intestat", "cliente", "codice", "pod", "pdr", "iban", "indirizzo", "via ", "piazza", "c.f.", "codice fiscale",
            "titulaire", "adresse", "titular", "dirección", "@", "sig.", "sig.ra", "nome",
        )

        /** Le sole righe con consumi e prezzi, ripulite dai dati personali. */
        fun excerpt(text: String?): String? {
            if (text.isNullOrBlank()) return null
            val lines = mutableListOf<String>()
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.length < 6 || line.length > 160) continue
                val low = line.lowercase()
                if (KEEP.none { it in low } || DROP.any { it in low }) continue
                lines += mask(line)
                if (lines.joinToString("; ").length > 560) break
            }
            return lines.joinToString("; ").takeIf { it.isNotEmpty() }?.take(600)
        }

        /** Cifre lunghe e sigle alfanumeriche lunghe diventano «…»; i consumi restano. */
        fun mask(line: String): String = line
            .replace(Regex("""\b\d{7,}\b"""), "…")
            .replace(Regex("""\b(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{10,}\b"""), "…")
            .replace(Regex("""[\w.+-]+@[\w-]+\.[\w.]+"""), "…")
    }
}
