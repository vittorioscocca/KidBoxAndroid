package it.vittorioscocca.kidbox.data.crypto

import android.content.Context
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KidBoxApplicationHolder

/**
 * La master key della famiglia non è disponibile su questo dispositivo.
 *
 * Prima i tre punti di crypto (documenti, chat, password) lanciavano
 * `IllegalStateException("Family key missing…")`, indistinguibile da un guasto
 * qualsiasi: la UI mostrava il messaggio tecnico o un generico "errore", e
 * l'utente non aveva modo di capire che cosa fare.
 *
 * Con un tipo dedicato le schermate possono riconoscere il caso e mostrare il
 * testo unico di [familyKeyMissingMessage], che dice come recuperare la chiave.
 * Gemello di `FamilyKeyMissing` su iOS.
 */
class MissingFamilyKeyException(
    val familyId: String,
) : IllegalStateException(localizedShortMessage(familyId))

/**
 * Messaggio dell'eccezione, già localizzato.
 *
 * È il gemello della conformità `LocalizedError` su iOS: mettendo il testo
 * definitivo dentro `message`, ogni schermata che mostra già
 * `error.localizedMessage` — Documenti, Wallet, Chat, Foto — si allinea da sola,
 * senza dover iniettare un Context in ogni ViewModel.
 *
 * Il context arriva da [KidBoxApplicationHolder], già usato dal crash handler.
 * Se per qualsiasi motivo non fosse ancora popolato si ripiega sul testo
 * tecnico: meglio un messaggio in inglese che un crash dentro un costruttore
 * di eccezione.
 */
private fun localizedShortMessage(familyId: String): String =
    KidBoxApplicationHolder.applicationContext
        ?.let { runCatching { it.getString(R.string.family_key_missing_short) }.getOrNull() }
        ?: "Family key missing for familyId=$familyId"

/**
 * `true` se [this] — o una qualsiasi delle sue cause — è una chiave mancante.
 *
 * Si guarda anche la catena delle cause perché l'eccezione risale spesso
 * incapsulata da repository e coroutine.
 */
fun Throwable.isMissingFamilyKey(): Boolean {
    var e: Throwable? = this
    var hops = 0
    while (e != null && hops < 8) {
        if (e is MissingFamilyKeyException) return true
        e = e.cause
        hops++
    }
    return false
}

/** Messaggio esteso, per dialog e stati vuoti. */
fun Context.familyKeyMissingMessage(): String = getString(R.string.family_key_missing_message)

/** Messaggio su una riga, per barre d'errore e celle. */
fun Context.familyKeyMissingShort(): String = getString(R.string.family_key_missing_short)

/**
 * Messaggio da mostrare per [error]: il testo della chiave mancante quando è
 * quel caso, altrimenti [fallback] (di norma `error.localizedMessage`).
 */
fun Context.messageForCryptoError(error: Throwable, fallback: String?): String =
    if (error.isMissingFamilyKey()) {
        familyKeyMissingShort()
    } else {
        fallback ?: getString(R.string.family_key_missing_short)
    }
