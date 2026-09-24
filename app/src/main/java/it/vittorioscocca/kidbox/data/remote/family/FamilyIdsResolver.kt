package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.tasks.await

/**
 * Elenca le famiglie di un utente mettendo insieme le due fonti che le
 * conoscono.
 *
 * `users/{uid}/memberships` è **una copia**, comoda da leggere; la verità è il
 * documento `families/{familyId}/members/{uid}`, che è anche ciò su cui si
 * basano le regole Firestore. Finché le due fonti coincidono la differenza non
 * si vede — quando divergono, la famiglia sparisce dall'app pur esistendo:
 * è successo il 23/09/2026 su iOS, con l'indice incompleto e nessun modo di
 * accorgersene.
 *
 * Il fallback sul collection group esisteva già nei tre punti in cui serviva,
 * ma scattava **solo a elenco completamente vuoto**: un indice incompleto —
 * cioè esattamente il caso reale, una famiglia su due — non lo attivava. E non
 * avrebbe funzionato comunque: mancavano sia la regola di collection group sia
 * l'esenzione di indice su `members.uid` (entrambe aggiunte il 24/09/2026), e
 * la query falliva in silenzio dentro un `runCatching`.
 *
 * Qui le due fonti si leggono sempre e si uniscono. Costa una query in più per
 * bootstrap, che restituisce un documento per famiglia: il prezzo di non
 * perdere una famiglia.
 *
 * Dal 24/09/2026 il trigger `syncMembershipIndex` tiene l'indice allineato ai
 * documenti membro, quindi le due fonti dovrebbero restare uguali da sole:
 * questa unione è la rete sotto, per i casi storici e per i client vecchi che
 * cancellano l'indice da soli.
 */
object FamilyIdsResolver {

    private const val TAG = "FamilyIdsResolver"

    /**
     * Gli id delle famiglie di [uid], senza duplicati: prima quelle note
     * all'indice (l'ordine conta per chi sceglie "la prima"), poi le altre.
     *
     * Se una delle due letture fallisce si prosegue con l'altra: mezza risposta
     * è meglio di nessuna, e un errore qui non deve impedire l'avvio.
     *
     * @param source `null` per la lettura normale (cache compresa), oppure
     *   [Source.SERVER] quando la cache non è attendibile — per esempio subito
     *   dopo il login.
     */
    suspend fun resolve(uid: String, source: Source? = null): List<String> {
        if (uid.isBlank()) return emptyList()
        val db = FirebaseFirestore.getInstance()
        val ids = mutableListOf<String>()

        runCatching {
            val query = db.collection("users").document(uid).collection("memberships")
            (if (source != null) query.get(source) else query.get()).await().documents
        }.onSuccess { docs ->
            for (doc in docs) {
                if (doc.id.isNotBlank()) ids.add(doc.id)
                (doc.data?.get("familyId") as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { ids.add(it) }
            }
        }.onFailure {
            KBLog.data.error("memberships non leggibili: ${it.message}", TAG)
        }

        runCatching {
            val query = db.collectionGroup("members").whereEqualTo("uid", uid)
            (if (source != null) query.get(source) else query.get()).await().documents
        }.onSuccess { docs ->
            val daiMembri = docs
                .filter { it.data?.get("isDeleted") as? Boolean != true }
                .mapNotNull { it.reference.parent.parent?.id }
            val nuove = daiMembri.filter { it !in ids }
            if (nuove.isNotEmpty()) {
                // Non è normale: significa che l'indice ha perso delle righe.
                KBLog.data.warning(
                    "famiglie trovate solo dai documenti membro (indice incompleto): ${nuove.size}",
                    TAG,
                )
            }
            ids.addAll(daiMembri)
        }.onFailure {
            KBLog.data.error("collectionGroup(members) non leggibile: ${it.message}", TAG)
        }

        return ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}
