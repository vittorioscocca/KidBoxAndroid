package it.vittorioscocca.kidbox.util

import java.util.Locale

/**
 * Se la voce Alexa vada mostrata nelle impostazioni.
 *
 * La skill esiste **solo in `it-IT`**: il nome di invocazione è italiano e il
 * backend risponde in italiano. Mostrarne le istruzioni a chi usa l'app in
 * un'altra lingua significa offrire una cosa che non può installare.
 *
 * Il segnale è la lingua **effettiva dell'app** ([KBLocale.current]), quindi
 * chi sceglie «Italiano» da Impostazioni > Lingua la tiene anche con il
 * telefono in un'altra lingua.
 *
 * L'eccezione `en` + regione `IT` non è un capriccio: replica quello che iOS fa
 * già in `AppLanguage.resolvedLanguageCode`. È il caso dell'italiano che tiene
 * il telefono in inglese e ha un Echo italiano — nascondergli Alexa sarebbe
 * l'errore peggiore fra i due possibili, perché gli toglie una cosa che
 * funziona invece di offrirne una che non funziona.
 *
 * ⚠️ Nascondendo la voce si nasconde anche lo scollegamento: chi ha già
 * collegato Alexa e poi cambia lingua deve rimettere l'italiano per gestirlo.
 */
object AlexaAvailability {

    /** Lingue in cui la skill esiste. Quando se ne aggiungeranno, si allarga. */
    private val SUPPORTED_LANGUAGES = setOf("it")

    fun isAvailable(locale: Locale = KBLocale.current()): Boolean {
        if (locale.language in SUPPORTED_LANGUAGES) return true
        return locale.language == "en" && locale.country.equals("IT", ignoreCase = true)
    }
}
