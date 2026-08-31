package it.vittorioscocca.kidbox.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.tasks.await

/**
 * Famiglia attiva ricordata sull'account, non sul dispositivo.
 *
 * Il logout fa piazza pulita in locale — ed è giusto, altrimenti i dati di un
 * account resterebbero nella sessione del successivo. Ma così si perdeva anche
 * "stavo nella famiglia B": al rientro il bootstrap ripartiva dalla prima
 * famiglia restituita da Firestore, di solito la più vecchia.
 *
 * Il posto giusto è `users/{uid}.activeFamilyId`: è del conto, non del telefono,
 * sopravvive a logout e reinstallazioni e segue l'utente anche fra Android e iOS
 * (`KBActiveFamilyRemoteStore` è il gemello, stesso campo).
 *
 * Le rules lo consentono: `users/{uid}` è aggiornabile dal proprietario purché
 * non tocchi i campi del piano (`keepsPlanFields()`).
 */
object ActiveFamilyRemoteStore {

    private const val TAG = "ActiveFamily"
    private const val FIELD_ID = "activeFamilyId"
    private const val FIELD_UPDATED_AT = "activeFamilyUpdatedAt"

    /**
     * Scrive la famiglia attiva sull'account. Un errore di rete non deve
     * impedire lo switch: la prossima scrittura riallinea.
     */
    suspend fun save(familyId: String?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            FirebaseFirestore.getInstance().collection("users").document(uid).set(
                mapOf(
                    FIELD_ID to (familyId ?: FieldValue.delete()),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }.onFailure {
            KBLog.sync.debug("Salvataggio famiglia attiva fallito: ${it.message}", TAG)
        }
    }

    /**
     * Ultima famiglia aperta con questo account, da qualunque dispositivo.
     *
     * Server-first: dopo un logout la cache locale di Firestore può contenere il
     * documento vecchio, e ripartiremmo di nuovo dalla famiglia sbagliata.
     */
    suspend fun load(uid: String): String? {
        if (uid.isBlank()) return null
        return runCatching {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .get(Source.SERVER).await()
                .getString(FIELD_ID)?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrElse {
            KBLog.sync.debug("Lettura famiglia attiva fallita: ${it.message}", TAG)
            null
        }
    }
}
