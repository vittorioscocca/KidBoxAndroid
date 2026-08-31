package it.vittorioscocca.kidbox.notifications

import android.content.Context
import android.content.Intent
import it.vittorioscocca.kidbox.R

/**
 * Testi delle notifiche locali risolti alla **consegna**, non alla programmazione.
 *
 * Gli alarm si armano al login e scattano giorni o settimane dopo. Finché lo
 * scheduler componeva la frase e la infilava negli extra, quella frase restava
 * nella lingua di quel momento: chi cambiava lingua nel frattempo riceveva il
 * promemoria in quella vecchia.
 *
 * Qui negli extra viaggiano il **nome della risorsa** e i suoi argomenti, e la
 * frase si compone dentro il receiver — che gira quando l'alarm scatta, con le
 * risorse della lingua di allora. A differenza di iOS non serve riscrivere la
 * coda a ogni cambio lingua: il momento della consegna è già codice nostro.
 *
 * Si passa il nome e non l'id numerico perché gli id cambiano a ogni build, e un
 * alarm armato prima di un aggiornamento sopravvive all'aggiornamento: con l'id
 * mostrerebbe la stringa sbagliata. La mappa è esplicita e non usa
 * `getIdentifier` così R8 vede le risorse come usate e non le rimuove.
 */
object KBNotificationText {

    const val EXTRA_TITLE_KEY = "kb.loc.titleKey"
    const val EXTRA_TITLE_ARGS = "kb.loc.titleArgs"
    const val EXTRA_BODY_KEY = "kb.loc.bodyKey"
    const val EXTRA_BODY_ARGS = "kb.loc.bodyArgs"

    /**
     * Un argomento che è a sua volta da tradurre si scrive `@nome_risorsa`.
     *
     * Serve ai testi composti da più pezzi tradotti (il tipo di documento dentro
     * il titolo, la fascia oraria dentro il corpo): passandoli già tradotti
     * resterebbero nella lingua vecchia mentre la cornice cambia.
     */
    private const val LOCALIZED_PREFIX = "@"

    private val KEYS: Map<String, Int> = mapOf(
        // Salute
        "visit_reminder_notification_title" to R.string.visit_reminder_notification_title,
        "visit_reminder_body_fallback" to R.string.visit_reminder_body_fallback,
        "visit_next_reminder_title" to R.string.visit_next_reminder_title,
        "exam_reminder_notification_title" to R.string.exam_reminder_notification_title,
        "exam_reminder_body_fallback" to R.string.exam_reminder_body_fallback,
        "exam_reminder_body_format" to R.string.exam_reminder_body_format,
        "exam_reminder_body_format_urgent" to R.string.exam_reminder_body_format_urgent,
        "vaccine_reminder_notification_title" to R.string.vaccine_reminder_notification_title,
        "vaccine_reminder_body_fallback" to R.string.vaccine_reminder_body_fallback,
        "vaccine_reminder_body_format" to R.string.vaccine_reminder_body_format,
        "treatment_reminder_notification_title" to R.string.treatment_reminder_notification_title,
        "treatment_reminder_body_fallback" to R.string.treatment_reminder_body_fallback,
        "treatment_reminder_body_format" to R.string.treatment_reminder_body_format,
        // Wallet
        "wallet_ticket_reminder_title_fallback" to R.string.wallet_ticket_reminder_title_fallback,
        "wallet_ticket_reminder_body_fallback" to R.string.wallet_ticket_reminder_body_fallback,
        "wallet_ticket_reminder_body_format" to R.string.wallet_ticket_reminder_body_format,
        "wallet_ticket_fallback" to R.string.wallet_ticket_fallback,
        "wallet_document_reminder_title" to R.string.wallet_document_reminder_title,
        "wallet_document_reminder_title_fallback" to R.string.wallet_document_reminder_title_fallback,
        "wallet_document_reminder_body_fallback" to R.string.wallet_document_reminder_body_fallback,
        "wallet_document_fallback" to R.string.wallet_document_fallback,
        // Password
        "password_expiry_reminder_title" to R.string.password_expiry_reminder_title,
        "password_expiry_body_tomorrow" to R.string.password_expiry_body_tomorrow,
        "password_expiry_body_7d" to R.string.password_expiry_body_7d,
        "password_expiry_body_30d" to R.string.password_expiry_body_30d,
        "password_entry_fallback" to R.string.password_entry_fallback,
        // Casa
        "house_payment_reminder_title" to R.string.house_payment_reminder_title,
        "house_payment_reminder_body" to R.string.house_payment_reminder_body,
        // Todo
        "todo_reminder_notification_title" to R.string.todo_reminder_notification_title,
        "todo_reminder_title_fallback" to R.string.todo_reminder_title_fallback,
        // Nudge
        "nudge_notification_title_fallback" to R.string.nudge_notification_title_fallback,
        "nudge_family_invite_title" to R.string.nudge_family_invite_title,
        "nudge_family_invite_body" to R.string.nudge_family_invite_body,
        "nudge_documents_title" to R.string.nudge_documents_title,
        "nudge_documents_body" to R.string.nudge_documents_body,
        "nudge_wallet_title" to R.string.nudge_wallet_title,
        "nudge_wallet_body" to R.string.nudge_wallet_body,
        "nudge_health_title" to R.string.nudge_health_title,
        "nudge_health_body" to R.string.nudge_health_body,
        "nudge_ai_title" to R.string.nudge_ai_title,
        "nudge_ai_body" to R.string.nudge_ai_body,
        "nudge_chat_title" to R.string.nudge_chat_title,
        "nudge_chat_body" to R.string.nudge_chat_body,
        "nudge_calendar_title" to R.string.nudge_calendar_title,
        "nudge_calendar_body" to R.string.nudge_calendar_body,
    )

    /** Nome della risorsa a partire dal suo id, per chi ha già l'id sottomano. */
    fun keyFor(resId: Int): String? = KEYS.entries.firstOrNull { it.value == resId }?.key

    /** Mette negli extra chiave e argomenti, invece della frase già composta. */
    fun put(
        extras: MutableMap<String, String>,
        titleKey: String? = null,
        titleArgs: List<String> = emptyList(),
        bodyKey: String? = null,
        bodyArgs: List<String> = emptyList(),
    ) {
        if (titleKey != null) {
            extras[EXTRA_TITLE_KEY] = titleKey
            extras[EXTRA_TITLE_ARGS] = encodeArgs(titleArgs)
        }
        if (bodyKey != null) {
            extras[EXTRA_BODY_KEY] = bodyKey
            extras[EXTRA_BODY_ARGS] = encodeArgs(bodyArgs)
        }
    }

    /** Marca un argomento che va tradotto anche lui. */
    fun localizedArg(key: String): String = LOCALIZED_PREFIX + key

    /**
     * Titolo tradotto ora, o `null` se l'alarm non porta una chiave.
     *
     * È `null` anche per gli alarm armati da una versione precedente, che negli
     * extra hanno ancora la frase già composta: i `PendingIntent` sopravvivono
     * agli aggiornamenti dell'app, quindi il receiver deve saperli ancora leggere.
     */
    fun title(context: Context, intent: Intent): String? =
        resolve(context, intent.getStringExtra(EXTRA_TITLE_KEY), intent.getStringExtra(EXTRA_TITLE_ARGS))

    /** Corpo tradotto ora, o `null` se l'alarm non porta una chiave. */
    fun body(context: Context, intent: Intent): String? =
        resolve(context, intent.getStringExtra(EXTRA_BODY_KEY), intent.getStringExtra(EXTRA_BODY_ARGS))

    /** Traduce una chiave con i suoi argomenti, senza passare da un Intent. */
    fun text(context: Context, key: String, args: List<String> = emptyList()): String =
        resolve(context, key, encodeArgs(args)) ?: key

    private fun resolve(context: Context, key: String?, encodedArgs: String?): String? {
        val resId = KEYS[key ?: return null] ?: return null
        val args = decodeArgs(encodedArgs).map { arg ->
            if (arg.startsWith(LOCALIZED_PREFIX)) {
                KEYS[arg.removePrefix(LOCALIZED_PREFIX)]?.let(context::getString) ?: arg
            } else {
                arg
            }
        }
        return runCatching {
            if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args.toTypedArray())
        }.getOrNull()
    }

    // Gli extra dell'alarm sono stringhe: gli argomenti viaggiano in una sola,
    // separati da un carattere di controllo che nei testi non compare mai.
    private const val SEPARATOR = "\u001F"

    private fun encodeArgs(args: List<String>): String = args.joinToString(SEPARATOR)

    private fun decodeArgs(encoded: String?): List<String> =
        if (encoded.isNullOrEmpty()) emptyList() else encoded.split(SEPARATOR)
}
