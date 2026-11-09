package it.vittorioscocca.kidbox.data.crypto

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.chat.crypto.ChatCryptoService
import it.vittorioscocca.kidbox.data.local.KBFeatureFlags
import it.vittorioscocca.kidbox.util.KBLog
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Cifratura delle sezioni (Garage, Animali, Spese, Salute; dal secondo giro
 * anche figli, to-do, liste e calendario) con la chiave di famiglia: specifica in `internal/cifratura-sezioni.md`.
 *
 * Un solo campo in più per documento: `secure` = base64(AES-GCM) di un JSON
 * `{"campo": "valore"}` con i campi della tabella, e `secureV: 1`. Stesso
 * formato e stessa chiave delle note di iOS e web.
 *
 * Parte pura, senza Firebase né chiave: le funzioni di cifratura arrivano da
 * fuori, così le regole si provano in un test JVM.
 */
object SectionFields {

    const val SECURE = "secure"
    const val SECURE_V = "secureV"
    const val VERSION = 1

    /** Collezione degli eventi: se la famiglia esporta il calendario (link iCal) resta in chiaro. */
    const val CALENDAR_EVENTS = "calendarEvents"

    /**
     * Segnaposto puro per «campo da cancellare»: lato Android diventa
     * `FieldValue.delete()`, nei test resta questo oggetto.
     */
    object Deleted

    /** La tabella della specifica: identica su iOS, Android e web. */
    val byCollection: Map<String, List<String>> = mapOf(
        "vehicles" to listOf("name", "licensePlate", "brand", "model", "color", "vin", "notes"),
        "vehicleEvents" to listOf("title", "garageName", "notes"),
        "pets" to listOf("name", "breed", "color", "chipCode", "notes"),
        "petEvents" to listOf("title", "vetName", "notes"),
        "expenses" to listOf("title", "notes"),
        "medicalVisits" to listOf(
            "doctorName", "reason", "diagnosis", "recommendations", "notes", "nextVisitReason",
            "travelDetailsData", "travelDetailsJson", "asNeededDrugsData", "asNeededDrugsJson",
            "prescribedExamsData", "prescribedExamsJson",
        ),
        "medicalExams" to listOf("name", "preparation", "location", "resultText", "notes"),
        "treatments" to listOf("drugName", "activeIngredient", "notes"),
        "vaccines" to listOf(
            "commercialName", "lotNumber", "administeredBy", "notes", "name", "doctorName", "location",
        ),
        "pediatricProfiles" to listOf(
            "bloodGroup", "allergies", "medicalNotes", "doctorName", "doctorPhone",
            "doctorEmail", "doctorAddress", "doctorWebsite", "doctorOfficeHoursJSON",
            "emergencyContactsJSON",
        ),
        "children" to listOf("name"),
        "todos" to listOf("title", "notes"),
        "todoLists" to listOf("name"),
        CALENDAR_EVENTS to listOf("title", "notes", "location"),
    )

    /**
     * Campi segnati `*` nella specifica: opzionali, JSON o base64. Sigillati si
     * scrivono cancellati invece che `""` (un parser vecchio potrebbe non reggere
     * la stringa vuota). In lettura un campo assente vale vuoto.
     */
    val deletedOnSeal: Map<String, Set<String>> = mapOf(
        "medicalVisits" to setOf(
            "travelDetailsData", "travelDetailsJson", "asNeededDrugsData", "asNeededDrugsJson",
            "prescribedExamsData", "prescribedExamsJson",
        ),
    )

    fun fieldsOf(collection: String): List<String> =
        byCollection[collection] ?: error("Collezione senza campi cifrati: $collection")

    private fun isDeletedOnSeal(collection: String, field: String): Boolean =
        deletedOnSeal[collection]?.contains(field) == true

    /** Valore da scrivere al posto di un campo sigillato: `""` o cancellato. */
    private fun sealedValue(collection: String, field: String, deleteValue: Any): Any =
        if (isDeletedOnSeal(collection, field)) deleteValue else ""

    /** Il documento porta un `secure` (anche se poi non si apre). */
    fun isSealed(data: Map<String, Any?>): Boolean =
        (data[SECURE] as? String)?.isNotEmpty() == true

    /** Almeno un campo della tabella è ancora in chiaro e non vuoto. */
    fun hasPlain(collection: String, data: Map<String, Any?>): Boolean =
        fieldsOf(collection).any { (data[it] as? String)?.isNotEmpty() == true }

    /** La scrittura porta almeno un campo della tabella (anche nullo o da cancellare). */
    fun touchesSealedFields(collection: String, data: Map<String, Any?>): Boolean =
        fieldsOf(collection).any { it in data }

    /** Campi della tabella che la scrittura non porta affatto (chiave assente). */
    fun missingFields(collection: String, data: Map<String, Any?>): List<String> =
        fieldsOf(collection).filter { it !in data }

    /**
     * `secure` c'è ma non si è aperto (chiave diversa o mancante) e qualche campo
     * vive solo lì: non va sovrascritto. [resolve] restituisce la stessa mappa
     * quando non apre il blob.
     */
    fun isUnreadable(collection: String, raw: Map<String, Any?>, resolved: Map<String, Any?>): Boolean =
        isSealed(raw) && resolved === raw &&
            fieldsOf(collection).any { (raw[it] as? String).isNullOrEmpty() }

    /**
     * Scrittura parziale: i campi della tabella che [data] non porta si prendono
     * dal documento esistente già risolto ([existing]), così finiscono nel nuovo
     * `secure` invece di perdersi. I campi che [data] porta (anche nulli: è
     * l'utente che li ha svuotati) restano quelli di [data].
     */
    fun mergeMissing(
        collection: String,
        data: Map<String, Any?>,
        existing: Map<String, Any?>,
    ): Map<String, Any?> {
        val missing = missingFields(collection, data)
        if (missing.isEmpty()) return data
        val out = LinkedHashMap(data)
        for (field in missing) {
            val value = existing[field] as? String ?: continue
            if (value.isNotEmpty()) out[field] = value
        }
        return out
    }

    /**
     * Regola di lettura: il chiaro vince se è una stringa non vuota (una build
     * vecchia che modifica riscrive il chiaro, ed è la modifica più recente);
     * altrimenti il valore in `secure`. [decrypt] restituisce il JSON o null se
     * non si apre: in quel caso restano i valori in chiaro, niente scarto.
     */
    fun resolve(
        collection: String,
        data: Map<String, Any?>,
        decrypt: (String) -> String?,
    ): Map<String, Any?> {
        val blob = (data[SECURE] as? String)?.takeIf { it.isNotEmpty() } ?: return data
        val fields = fieldsOf(collection)
        // Tutto già in chiaro: non serve aprire il blob.
        if (fields.all { (data[it] as? String)?.isNotEmpty() == true }) return data
        val sealed = decrypt(blob)?.let { decodeJson(it) } ?: return data
        val out = HashMap(data)
        for (field in fields) {
            if ((data[field] as? String)?.isNotEmpty() == true) continue
            sealed[field]?.let { out[field] = it }
        }
        return out
    }

    /**
     * Mappa da scrivere a cifratura accesa: i campi della tabella vanno in
     * `secure` e restano `""` (non cancellati: le build vecchie scartano il
     * documento senza `name`/`title`), salvo quelli `*` che si cancellano
     * ([deleteValue]). [encrypt] null = chiave mancante: si restituisce null e
     * chi chiama scrive in chiaro. Chi chiama ha già unito i campi mancanti
     * ([mergeMissing]).
     */
    fun seal(
        collection: String,
        data: Map<String, Any?>,
        encrypt: (String) -> String?,
        deleteValue: Any = Deleted,
    ): Map<String, Any?>? {
        val blob = encrypt(encodeJson(collection, data)) ?: return null
        val out = LinkedHashMap(data)
        for (field in fieldsOf(collection)) out[field] = sealedValue(collection, field, deleteValue)
        out[SECURE] = blob
        out[SECURE_V] = VERSION
        return out
    }

    /**
     * Ricifratura di un documento vecchio, da scrivere in merge: solo `secure`
     * (valori già uniti con la regola di lettura), `secureV` e i campi a `""`
     * (o cancellati, per i `*`). Niente `updatedAt`/`updatedBy`: non è una
     * modifica dell'utente.
     */
    fun reencryptPayload(
        collection: String,
        resolved: Map<String, Any?>,
        encrypt: (String) -> String?,
        deleteValue: Any = Deleted,
    ): Map<String, Any>? {
        val blob = encrypt(encodeJson(collection, resolved)) ?: return null
        return buildMap {
            for (field in fieldsOf(collection)) put(field, sealedValue(collection, field, deleteValue))
            put(SECURE, blob)
            put(SECURE_V, VERSION)
        }
    }

    /**
     * Riapertura (calendario esportato col link iCal): i campi in chiaro
     * risolti e `secure`/`secureV` cancellati. Niente `updatedAt`.
     */
    fun reopenPayload(
        collection: String,
        resolved: Map<String, Any?>,
        deleteValue: Any = Deleted,
    ): Map<String, Any> = buildMap {
        for (field in fieldsOf(collection)) {
            val value = resolved[field] as? String ?: continue
            if (value.isNotEmpty()) put(field, value)
        }
        put(SECURE, deleteValue)
        put(SECURE_V, deleteValue)
    }

    /** Solo i campi della tabella con stringa non vuota; nulli e vuoti si omettono. */
    fun encodeJson(collection: String, data: Map<String, Any?>): String {
        val json = JSONObject()
        for (field in fieldsOf(collection)) {
            val value = data[field] as? String ?: continue
            if (value.isNotEmpty()) json.put(field, value)
        }
        return json.toString()
    }

    fun decodeJson(text: String): Map<String, String>? = runCatching {
        val json = JSONObject(text)
        buildMap {
            for (key in json.keys()) {
                val value = json.opt(key)
                if (value is String) put(key, value)
            }
        }
    }.getOrNull()
}

/**
 * Lato Android di [SectionFields]: chiave di famiglia, interruttore
 * [KBFeatureFlags.sectionsEncryptionEnabled], scritture parziali, ricifratura e
 * riapertura degli eventi delle famiglie che esportano il calendario.
 */
@Singleton
class SectionCrypto @Inject constructor(
    private val crypto: ChatCryptoService,
) {

    private val db get() = FirebaseFirestore.getInstance()

    /** Documenti già ricifrati o riaperti (o tentati) in questa sessione, per path. */
    private val reencrypted: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * `calendarExported` del documento famiglia, per familyId. Lo aggiorna il
     * listener della famiglia ([setCalendarExported]); in scrittura, se manca, si
     * legge il documento.
     */
    private val calendarExported: MutableMap<String, Boolean> = ConcurrentHashMap()

    /** Dal listener del documento famiglia (FamilySyncCenter). */
    fun setCalendarExported(familyId: String, exported: Boolean) {
        calendarExported[familyId] = exported
    }

    /** Regola di lettura; senza chiave restano i valori in chiaro. */
    fun read(collection: String, familyId: String, data: Map<String, Any?>): Map<String, Any?> =
        SectionFields.resolve(collection, data) { decrypt(it, familyId) }

    /**
     * Da chiamare su ogni scrittura (set in merge) di [ref]: interruttore spento,
     * chiave mancante o lettura fallita → la mappa resta com'è (chiaro, `secure`
     * non si tocca).
     *
     * Se la scrittura non porta tutti i campi della tabella, si legge il
     * documento esistente (risolto) e i mancanti si prendono da lì prima di
     * sigillare: altrimenti un campo che questo client non scrive (es. i dettagli
     * di viaggio delle visite, scritti da iOS) si perderebbe nel nuovo `secure`.
     * Una scrittura che non tocca nessun campo della tabella passa com'è.
     */
    suspend fun prepareWrite(
        collection: String,
        familyId: String,
        ref: DocumentReference,
        data: Map<String, Any?>,
    ): Map<String, Any?> {
        if (!KBFeatureFlags.sectionsEncryptionEnabled.value) return data
        if (!SectionFields.touchesSealedFields(collection, data)) return data
        if (collection == SectionFields.CALENDAR_EVENTS && isCalendarExported(familyId)) {
            // Calendario esportato: in chiaro. Una scrittura completa riapre anche
            // il documento (il chiaro scritto qui è già tutto).
            if (SectionFields.missingFields(collection, data).isNotEmpty()) return data
            return data + mapOf(
                SectionFields.SECURE to FieldValue.delete(),
                SectionFields.SECURE_V to FieldValue.delete(),
            )
        }
        var toSeal = data
        if (SectionFields.missingFields(collection, data).isNotEmpty()) {
            val raw = try {
                ref.get().await().data
            } catch (e: Exception) {
                KBLog.data.warning("lettura per scrittura parziale fallita $collection/${ref.id}: ${e.javaClass.simpleName}", TAG)
                return data
            }
            if (raw != null) {
                val existing = read(collection, familyId, raw)
                // `secure` illeggibile: sovrascriverlo perderebbe i campi che stanno solo lì.
                if (SectionFields.isUnreadable(collection, raw, existing)) return data
                toSeal = SectionFields.mergeMissing(collection, data, existing)
            }
        }
        return SectionFields.seal(collection, toSeal, { encrypt(it, familyId) }, FieldValue.delete())
            ?: data
    }

    /**
     * Legge e, a interruttore acceso, ricifra il documento se ha ancora campi in
     * chiaro (o lo riapre, per gli eventi di un calendario esportato): una volta
     * per documento per sessione. Restituisce i dati risolti.
     *
     * Non chiamarla su un documento rimosso (DocumentChange REMOVED): la
     * scrittura usa `update`, che su un documento assente fallisce senza
     * ricrearlo, ma è una scrittura inutile.
     */
    fun readAndReencrypt(
        collection: String,
        familyId: String,
        ref: DocumentReference,
        data: Map<String, Any?>,
    ): Map<String, Any?> {
        val resolved = read(collection, familyId, data)
        reencryptIfNeeded(collection, familyId, ref, data, resolved)
        return resolved
    }

    private suspend fun isCalendarExported(familyId: String): Boolean {
        calendarExported[familyId]?.let { return it }
        return try {
            val exported = db.collection("families").document(familyId).get().await()
                .getBoolean("calendarExported") == true
            calendarExported[familyId] = exported
            exported
        } catch (e: Exception) {
            // Non si sa: meglio in chiaro (il feed iCal deve poter leggere i titoli).
            KBLog.data.warning("calendarExported non letto familyId=$familyId: ${e.javaClass.simpleName}", TAG)
            true
        }
    }

    private fun reencryptIfNeeded(
        collection: String,
        familyId: String,
        ref: DocumentReference,
        raw: Map<String, Any?>,
        resolved: Map<String, Any?>,
    ) {
        if (!KBFeatureFlags.sectionsEncryptionEnabled.value) return
        if (collection == SectionFields.CALENDAR_EVENTS) {
            when (calendarExported[familyId]) {
                // Non ancora noto (il listener della famiglia non ha risposto): niente.
                null -> return
                true -> {
                    reopenIfNeeded(collection, ref, raw, resolved)
                    return
                }
                false -> Unit
            }
        }
        if (!SectionFields.hasPlain(collection, raw)) return
        // Un `secure` che non si apre (chiave diversa) non si sovrascrive: si
        // perderebbero i campi che stanno solo lì.
        if (SectionFields.isUnreadable(collection, raw, resolved)) return
        if (!reencrypted.add(ref.path)) return
        val payload = SectionFields.reencryptPayload(
            collection, resolved, { encrypt(it, familyId) }, FieldValue.delete(),
        )
        if (payload == null) {
            // Chiave mancante: si riprova quando arriva.
            reencrypted.remove(ref.path)
            return
        }
        // `update` e non `set` in merge: se il documento nel frattempo è stato
        // cancellato, non va ricreato con i soli campi cifrati.
        ref.update(payload)
            .addOnFailureListener { err ->
                KBLog.data.warning("ricifratura fallita $collection/${ref.id}: ${err.javaClass.simpleName}", TAG)
            }
    }

    /** Calendario esportato: il documento sigillato torna in chiaro. */
    private fun reopenIfNeeded(
        collection: String,
        ref: DocumentReference,
        raw: Map<String, Any?>,
        resolved: Map<String, Any?>,
    ) {
        if (!SectionFields.isSealed(raw)) return
        // Senza chiave non si sa cosa scrivere in chiaro: si lascia com'è.
        if (SectionFields.isUnreadable(collection, raw, resolved)) return
        if (!reencrypted.add(ref.path)) return
        ref.update(SectionFields.reopenPayload(collection, resolved, FieldValue.delete()))
            .addOnFailureListener { err ->
                KBLog.data.warning("riapertura fallita $collection/${ref.id}: ${err.javaClass.simpleName}", TAG)
            }
    }

    private fun encrypt(plain: String, familyId: String): String? =
        runCatching { crypto.encryptStringToBase64(plain, familyId) }.getOrNull()

    private fun decrypt(blob: String, familyId: String): String? =
        runCatching { crypto.decryptStringFromBase64(blob, familyId) }.getOrNull()

    private companion object {
        const val TAG = "SectionCrypto"
    }
}
