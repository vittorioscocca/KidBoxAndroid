package it.vittorioscocca.kidbox.data.crypto

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.chat.crypto.ChatCryptoService
import it.vittorioscocca.kidbox.data.local.KBFeatureFlags
import it.vittorioscocca.kidbox.util.KBLog
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Cifratura delle sezioni (Garage, Animali, Spese, Salute) con la chiave di
 * famiglia: specifica in `internal/cifratura-sezioni.md`.
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

    /** La tabella della specifica: identica su iOS, Android e web. */
    val byCollection: Map<String, List<String>> = mapOf(
        "vehicles" to listOf("name", "licensePlate", "brand", "model", "color", "vin", "notes"),
        "vehicleEvents" to listOf("title", "garageName", "notes"),
        "pets" to listOf("name", "breed", "color", "chipCode", "notes"),
        "petEvents" to listOf("title", "vetName", "notes"),
        "expenses" to listOf("title", "notes"),
        "medicalVisits" to listOf(
            "doctorName", "reason", "diagnosis", "recommendations", "notes", "nextVisitReason",
        ),
        "medicalExams" to listOf("name", "preparation", "location", "resultText", "notes"),
        "treatments" to listOf("drugName", "activeIngredient", "notes"),
        "vaccines" to listOf("commercialName", "lotNumber", "administeredBy", "notes"),
        "pediatricProfiles" to listOf(
            "bloodGroup", "allergies", "medicalNotes", "doctorName", "doctorPhone",
            "doctorEmail", "doctorAddress", "doctorWebsite", "doctorOfficeHoursJSON",
            "emergencyContactsJSON",
        ),
    )

    fun fieldsOf(collection: String): List<String> =
        byCollection[collection] ?: error("Collezione senza campi cifrati: $collection")

    /** Il documento porta un `secure` (anche se poi non si apre). */
    fun isSealed(data: Map<String, Any?>): Boolean =
        (data[SECURE] as? String)?.isNotEmpty() == true

    /** Almeno un campo della tabella è ancora in chiaro e non vuoto. */
    fun hasPlain(collection: String, data: Map<String, Any?>): Boolean =
        fieldsOf(collection).any { (data[it] as? String)?.isNotEmpty() == true }

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
     * documento senza `name`/`title`). [encrypt] null = chiave mancante: si
     * restituisce null e chi chiama scrive in chiaro.
     */
    fun seal(
        collection: String,
        data: Map<String, Any?>,
        encrypt: (String) -> String?,
    ): Map<String, Any?>? {
        val blob = encrypt(encodeJson(collection, data)) ?: return null
        val out = LinkedHashMap(data)
        for (field in fieldsOf(collection)) out[field] = ""
        out[SECURE] = blob
        out[SECURE_V] = VERSION
        return out
    }

    /**
     * Ricifratura di un documento vecchio, da scrivere in merge: solo `secure`
     * (valori già uniti con la regola di lettura), `secureV` e i campi a `""`.
     * Niente `updatedAt`/`updatedBy`: non è una modifica dell'utente.
     */
    fun reencryptPayload(
        collection: String,
        resolved: Map<String, Any?>,
        encrypt: (String) -> String?,
    ): Map<String, Any?>? {
        val blob = encrypt(encodeJson(collection, resolved)) ?: return null
        return buildMap {
            for (field in fieldsOf(collection)) put(field, "")
            put(SECURE, blob)
            put(SECURE_V, VERSION)
        }
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
 * [KBFeatureFlags.sectionsEncryptionEnabled] e ricifratura.
 */
@Singleton
class SectionCrypto @Inject constructor(
    private val crypto: ChatCryptoService,
) {

    /** Documenti già ricifrati (o tentati) in questa sessione, per path. */
    private val reencrypted: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Regola di lettura; senza chiave restano i valori in chiaro. */
    fun read(collection: String, familyId: String, data: Map<String, Any?>): Map<String, Any?> =
        SectionFields.resolve(collection, data) { decrypt(it, familyId) }

    /**
     * Da chiamare su ogni upsert: interruttore spento o chiave mancante → la
     * mappa resta com'è (chiaro, `secure` non si tocca).
     */
    fun prepareWrite(collection: String, familyId: String, data: Map<String, Any?>): Map<String, Any?> {
        if (!KBFeatureFlags.sectionsEncryptionEnabled.value) return data
        return SectionFields.seal(collection, data) { encrypt(it, familyId) } ?: data
    }

    /**
     * Legge e, a interruttore acceso, ricifra il documento se ha ancora campi in
     * chiaro: una volta per documento per sessione. Restituisce i dati risolti.
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

    private fun reencryptIfNeeded(
        collection: String,
        familyId: String,
        ref: DocumentReference,
        raw: Map<String, Any?>,
        resolved: Map<String, Any?>,
    ) {
        if (!KBFeatureFlags.sectionsEncryptionEnabled.value) return
        if (!SectionFields.hasPlain(collection, raw)) return
        // Un `secure` che non si apre (chiave diversa) non si sovrascrive: si
        // perderebbero i campi che stanno solo lì. `resolve` restituisce la
        // stessa mappa quando non ha aperto il blob.
        val unreadable = SectionFields.isSealed(raw) && resolved === raw &&
            SectionFields.fieldsOf(collection).any { (raw[it] as? String).isNullOrEmpty() }
        if (unreadable) return
        if (!reencrypted.add(ref.path)) return
        val payload = SectionFields.reencryptPayload(collection, resolved) { encrypt(it, familyId) }
        if (payload == null) {
            // Chiave mancante: si riprova quando arriva.
            reencrypted.remove(ref.path)
            return
        }
        ref.set(payload, SetOptions.merge())
            .addOnFailureListener { err ->
                KBLog.data.warning("ricifratura fallita $collection/${ref.id}: ${err.javaClass.simpleName}", TAG)
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
