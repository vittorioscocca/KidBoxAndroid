package it.vittorioscocca.kidbox.ui.screens.news

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Le notizie salvate di chi è collegato, dalla più recente. */
data class NewsSavedState(
    val items: List<NewsSavedItem> = emptyList(),
    /** Falso finché non arriva la prima risposta (cache o server). */
    val loaded: Boolean = false,
) {
    val ids: Set<String> = items.mapTo(HashSet()) { it.id }
}

/**
 * Le notizie salvate col segnalibro (richiesta dell'utente del 04/10/2026): di
 * chi le salva, non della famiglia, su `users/{uid}/savedNews/{id}` con id =
 * SHA-256 dell'URL ([newsSavedId]). Un ascolto per sessione che segue
 * l'account: cambiando utente o uscendo smette di ascoltare e svuota, senza
 * bisogno di un reset all'uscita. Gemello di `NewsSavedStore.swift`;
 * `deleteAccount` cancella i documenti con l'account.
 */
@Singleton
class NewsSavedStore @Inject constructor() {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val _state = MutableStateFlow(NewsSavedState())
    val state: StateFlow<NewsSavedState> = _state.asStateFlow()

    private var uid: String? = null
    private var listener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    private fun collection(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("savedNews")

    /** Apre l'ascolto la prima volta che serve; le volte dopo non fa niente. */
    fun start() {
        if (authListener != null) return
        val l = FirebaseAuth.AuthStateListener { bind(it.currentUser?.uid) }
        authListener = l
        auth.addAuthStateListener(l)
    }

    private fun bind(newUid: String?) {
        if (newUid == uid) return
        listener?.remove()
        listener = null
        uid = newUid
        _state.value = NewsSavedState()
        newUid ?: return
        listener = collection(newUid)
            .orderBy("savedAtMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (uid != newUid) return@addSnapshotListener
                if (error != null) {
                    KBLog.app.warning("NewsSaved listen failed: ${error.message}", "NewsSavedStore")
                    _state.update { it.copy(loaded = true) }
                    return@addSnapshotListener
                }
                snap ?: return@addSnapshotListener
                // Dal risultato completo, non dal delta (vedi /porting-android, trappola 1).
                val items = snap.documents.mapNotNull { NewsSavedFormat.decode(it.id, it.data) }
                _state.value = NewsSavedState(items, loaded = true)
            }
    }

    /** Il segnalibro: salva la notizia, o la toglie se c'era già. Vero se l'ha salvata. */
    fun toggle(item: NewsItem, placeName: String?): Boolean {
        uid ?: return false
        val id = newsSavedId(item.url)
        if (id in _state.value.ids) {
            remove(setOf(id))
            return false
        }
        save(NewsSavedItem(id, item, placeName?.takeIf { it.isNotBlank() }, System.currentTimeMillis()))
        return true
    }

    /** «Annulla» dopo un'eliminazione: torna com'era, con la sua data. */
    fun restore(saved: NewsSavedItem) = save(saved)

    private fun save(saved: NewsSavedItem) {
        val uid = uid ?: return
        collection(uid).document(saved.id).set(NewsSavedFormat.encode(saved))
            .addOnFailureListener { KBLog.app.warning("NewsSaved save failed: ${it.message}", "NewsSavedStore") }
    }

    fun remove(ids: Set<String>) {
        val uid = uid ?: return
        if (ids.isEmpty()) return
        _state.update { s -> s.copy(items = s.items.filterNot { it.id in ids }) }
        // Un batch tiene al massimo 500 scritture.
        ids.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(collection(uid).document(it)) }
            batch.commit()
                .addOnFailureListener { KBLog.app.warning("NewsSaved delete failed: ${it.message}", "NewsSavedStore") }
        }
    }

    fun removeAll() = remove(_state.value.ids)
}
