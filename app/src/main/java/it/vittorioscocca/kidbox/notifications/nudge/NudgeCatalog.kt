package it.vittorioscocca.kidbox.notifications.nudge

import it.vittorioscocca.kidbox.R

/**
 * Catalogo delle campagne di nudge — gemello di `NudgeCatalog.swift` su iOS.
 *
 * Il catalogo di DEFAULT vive qui, nel codice. Il documento Firestore
 * `config/nudges` è solo un OVERRIDE: se manca, non è leggibile o è malformato,
 * il motore continua a funzionare con il catalogo compilato invece di smettere
 * di mandare nulla. Non c'è nessun seed da fare per partire.
 *
 * Da remoto si cambiano testi, cadenze, ordine, numero di invii e il kill
 * switch. NON si cambiano le condizioni disponibili né le destinazioni: sono
 * insiemi chiusi, perché il client deve saperle valutare e aprire. Una
 * destinazione sconosciuta viene ignorata, non indovinata.
 */

/** Aree su cui si può misurare "non l'ha mai usata". */
enum class NudgeFeature { DOCUMENTS, WALLET, HEALTH, AI, CHAT, CALENDAR;
    companion object {
        fun from(raw: String?): NudgeFeature? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/** Dove porta il pulsante primario. */
enum class NudgeDestination { INVITE, DOCUMENTS, WALLET, HEALTH, AI, CHAT, CALENDAR;
    companion object {
        fun from(raw: String?): NudgeDestination? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/**
 * Condizioni valutate SOLO sul dispositivo, sui dati già presenti in locale.
 * Nessuna query di rete, nessun profilo per-utente lato server.
 */
data class NudgeRequirements(
    val familyMembersMax: Int? = null,
    val familyMembersMin: Int? = null,
    val featureUnused: NudgeFeature? = null,
)

data class NudgeCampaign(
    val id: String,
    val enabled: Boolean = true,
    /** Le campagne non partono in parallelo: una alla volta, in quest'ordine. */
    val order: Int,
    /** Giorni dall'installazione prima del primo invio possibile. */
    val firstDelayDays: Int,
    /** Distanza fra le ripetizioni della STESSA campagna. */
    val repeatEveryDays: Int = 7,
    /** Quante volte al massimo questa campagna può scattare, per sempre. */
    val maxFires: Int = 1,
    val requires: NudgeRequirements = NudgeRequirements(),
    /** Id risorsa stringa: è la via normale, così il testo è tradotto. */
    val titleRes: Int? = null,
    val bodyRes: Int? = null,
    /**
     * Testo remoto, usato se la risorsa locale non esiste (campagna nuova
     * spinta a un'app vecchia). Non è tradotto: meglio un testo in una lingua
     * sola che nessun testo.
     */
    val titleFallback: String? = null,
    val bodyFallback: String? = null,
    val destination: NudgeDestination? = null,
)

data class NudgeConfig(
    /** Kill switch globale: si spegne tutto da console, senza release. */
    val enabled: Boolean,
    /**
     * Distanza minima fra due nudge qualsiasi. È il freno principale contro il
     * fastidio: le singole campagne non possono aggirarlo.
     */
    val globalCooldownDays: Int,
    /** Tetto assoluto in 90 giorni, comunque vada l'aritmetica sopra. */
    val maxPerQuarter: Int,
    /** Fascia in cui non si notifica (ore locali). `start` incluso, `end` escluso. */
    val quietHoursStart: Int,
    val quietHoursEnd: Int,
    val campaigns: List<NudgeCampaign>,
) {
    companion object {

        /**
         * La sequenza decisa a prodotto:
         *
         * 1. **Invito in famiglia** — la priorità. Da solo, KidBox è un archivio
         *    personale: il valore esiste solo quando c'è più di una persona.
         *    Parte il giorno dopo l'installazione e insiste per due settimane,
         *    poi smette. Se la famiglia cresce, `familyMembersMax` smette di
         *    essere vera e la campagna si spegne da sola.
         *
         * 2..7. **Le feature**, una a settimana, ognuna una sola volta e solo se
         *    mai usata. Tutte richiedono `familyMembersMin = 2`: non ha senso
         *    spiegare la condivisione a chi non ha ancora nessuno con cui
         *    condividere.
         */
        val BUILT_IN = NudgeConfig(
            enabled = true,
            globalCooldownDays = 7,
            maxPerQuarter = 10,
            quietHoursStart = 21,
            quietHoursEnd = 9,
            campaigns = listOf(
                NudgeCampaign(
                    id = "family_invite",
                    order = 0,
                    firstDelayDays = 1,
                    repeatEveryDays = 7,
                    maxFires = 3,
                    requires = NudgeRequirements(familyMembersMax = 1),
                    titleRes = R.string.nudge_family_invite_title,
                    bodyRes = R.string.nudge_family_invite_body,
                    titleFallback = "KidBox funziona in famiglia",
                    bodyFallback = "Invita l'altro genitore o gli altri membri: documenti, " +
                        "wallet, salute, spese e calendario diventano condivisi e " +
                        "aggiornati per tutti, in tempo reale.",
                    destination = NudgeDestination.INVITE,
                ),
                NudgeCampaign(
                    id = "documents_share",
                    order = 1,
                    firstDelayDays = 7,
                    requires = NudgeRequirements(
                        familyMembersMin = 2,
                        featureUnused = NudgeFeature.DOCUMENTS,
                    ),
                    titleRes = R.string.nudge_documents_title,
                    bodyRes = R.string.nudge_documents_body,
                    titleFallback = "I documenti, sempre con te",
                    bodyFallback = "Carica un documento una volta sola: tutta la famiglia " +
                        "lo vede subito, senza doverlo chiedere a nessuno.",
                    destination = NudgeDestination.DOCUMENTS,
                ),
                NudgeCampaign(
                    id = "wallet_expiry",
                    order = 2,
                    firstDelayDays = 7,
                    requires = NudgeRequirements(
                        familyMembersMin = 2,
                        featureUnused = NudgeFeature.WALLET,
                    ),
                    titleRes = R.string.nudge_wallet_title,
                    bodyRes = R.string.nudge_wallet_body,
                    titleFallback = "Carte d'identità e biglietti nel Wallet",
                    bodyFallback = "Conserva documenti d'identità e di viaggio, e ricevi un " +
                        "promemoria una settimana prima che scadano.",
                    destination = NudgeDestination.WALLET,
                ),
                NudgeCampaign(
                    id = "health_records",
                    order = 3,
                    firstDelayDays = 7,
                    requires = NudgeRequirements(
                        familyMembersMin = 2,
                        featureUnused = NudgeFeature.HEALTH,
                    ),
                    titleRes = R.string.nudge_health_title,
                    bodyRes = R.string.nudge_health_body,
                    titleFallback = "La salute dei tuoi figli, in un posto solo",
                    bodyFallback = "Visite, accertamenti, cure e cartella clinica: lo storico " +
                        "completo, condiviso con chi se ne occupa insieme a te.",
                    destination = NudgeDestination.HEALTH,
                ),
                NudgeCampaign(
                    id = "ai_assistant",
                    order = 4,
                    firstDelayDays = 7,
                    requires = NudgeRequirements(
                        familyMembersMin = 2,
                        featureUnused = NudgeFeature.AI,
                    ),
                    titleRes = R.string.nudge_ai_title,
                    bodyRes = R.string.nudge_ai_body,
                    titleFallback = "Chiedi, invece di cercare",
                    bodyFallback = "L'assistente conosce quello che hai in KidBox: fai una " +
                        "domanda, e può anche creare promemoria ed eventi per te.",
                    destination = NudgeDestination.AI,
                ),
                NudgeCampaign(
                    id = "chat_family",
                    order = 5,
                    firstDelayDays = 7,
                    requires = NudgeRequirements(
                        familyMembersMin = 2,
                        featureUnused = NudgeFeature.CHAT,
                    ),
                    titleRes = R.string.nudge_chat_title,
                    bodyRes = R.string.nudge_chat_body,
                    titleFallback = "La chat di famiglia",
                    bodyFallback = "Un posto solo per parlarne, con accanto i documenti e le " +
                        "foto di cui state parlando.",
                    destination = NudgeDestination.CHAT,
                ),
                NudgeCampaign(
                    id = "calendar_shared",
                    order = 6,
                    firstDelayDays = 7,
                    requires = NudgeRequirements(
                        familyMembersMin = 2,
                        featureUnused = NudgeFeature.CALENDAR,
                    ),
                    titleRes = R.string.nudge_calendar_title,
                    bodyRes = R.string.nudge_calendar_body,
                    titleFallback = "Un calendario che vedete in due",
                    bodyFallback = "Visite, sport, compleanni: chi c'è, quando, e chi ci pensa. " +
                        "Senza rincorrersi a messaggi.",
                    destination = NudgeDestination.CALENDAR,
                ),
            ),
        )

        /**
         * Applica l'override remoto sul catalogo compilato.
         *
         * Ogni campo è opzionale e ogni errore di forma fa cadere quel singolo
         * campo, non l'intera configurazione: un `maxFires` scritto come stringa
         * non deve poter spegnere tutte le campagne.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromRemote(data: Map<String, Any?>?): NudgeConfig {
            if (data == null) return BUILT_IN
            fun int(key: String, fallback: Int) = (data[key] as? Number)?.toInt() ?: fallback

            val rawCampaigns = data["campaigns"] as? List<Map<String, Any?>>
            val campaigns = rawCampaigns
                ?.mapNotNull { raw ->
                    val id = raw["id"] as? String ?: return@mapNotNull null
                    val builtIn = BUILT_IN.campaigns.firstOrNull { it.id == id }
                    fun cInt(key: String, fallback: Int) = (raw[key] as? Number)?.toInt() ?: fallback
                    val req = raw["requires"] as? Map<String, Any?>
                    NudgeCampaign(
                        id = id,
                        enabled = raw["enabled"] as? Boolean ?: true,
                        order = cInt("order", builtIn?.order ?: 99),
                        firstDelayDays = cInt("firstDelayDays", builtIn?.firstDelayDays ?: 7),
                        repeatEveryDays = cInt("repeatEveryDays", builtIn?.repeatEveryDays ?: 7),
                        maxFires = cInt("maxFires", builtIn?.maxFires ?: 1),
                        requires = if (req != null) {
                            NudgeRequirements(
                                familyMembersMax = (req["familyMembersMax"] as? Number)?.toInt(),
                                familyMembersMin = (req["familyMembersMin"] as? Number)?.toInt(),
                                featureUnused = NudgeFeature.from(req["featureUnused"] as? String),
                            )
                        } else {
                            builtIn?.requires ?: NudgeRequirements()
                        },
                        titleRes = builtIn?.titleRes,
                        bodyRes = builtIn?.bodyRes,
                        titleFallback = raw["titleFallback"] as? String ?: builtIn?.titleFallback,
                        bodyFallback = raw["bodyFallback"] as? String ?: builtIn?.bodyFallback,
                        destination = NudgeDestination.from(raw["destination"] as? String)
                            ?: builtIn?.destination,
                    )
                }
                ?.takeIf { it.isNotEmpty() }
                ?: BUILT_IN.campaigns

            return NudgeConfig(
                enabled = data["enabled"] as? Boolean ?: BUILT_IN.enabled,
                globalCooldownDays = int("globalCooldownDays", BUILT_IN.globalCooldownDays),
                maxPerQuarter = int("maxPerQuarter", BUILT_IN.maxPerQuarter),
                quietHoursStart = int("quietHoursStart", BUILT_IN.quietHoursStart),
                quietHoursEnd = int("quietHoursEnd", BUILT_IN.quietHoursEnd),
                campaigns = campaigns,
            )
        }
    }
}
