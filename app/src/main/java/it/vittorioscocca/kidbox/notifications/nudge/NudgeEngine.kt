package it.vittorioscocca.kidbox.notifications.nudge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.NudgeSignalsDao
import it.vittorioscocca.kidbox.ui.screens.home.onboarding.OnboardingChecklistState
import it.vittorioscocca.kidbox.ui.screens.home.onboarding.OnboardingStep
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Motore dei nudge — gemello di `NudgeEngine.swift`.
 *
 * Gira interamente sul dispositivo. Il server non sa quali feature un utente non
 * usa e non deve saperlo: quel profilo esiste solo qui, in memoria, per il tempo
 * di una valutazione, e non viene mai scritto da nessuna parte.
 *
 * Le notifiche sono LOCALI e PRE-PIANIFICATE via [AlarmManager]. È la proprietà
 * che rende il sistema utile proprio sull'utente che vogliamo raggiungere: un
 * allarme scatta anche se l'app non viene mai più aperta, mentre qualsiasi
 * logica "al prossimo avvio" non raggiungerebbe mai chi non torna.
 *
 * A ogni foreground la coda viene CANCELLATA e ricalcolata da zero. Costa poco
 * ed evita l'errore peggiore: un nudge stantio che invita a fare una cosa che
 * l'utente ha già fatto ieri.
 */
@Singleton
class NudgeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signalsDao: NudgeSignalsDao,
    private val auth: FirebaseAuth,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private var cachedConfig: NudgeConfig? = null

    /** Da chiamare al passaggio in foreground. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (auth.currentUser == null) return@withContext
        if (NudgeState.isOptedOut(context)) {
            cancelAll()
            return@withContext
        }
        // Il permesso non si chiede qui: un nudge non vale una richiesta di
        // permesso a freddo. Se non c'è, non si pianifica e basta.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return@withContext

        val familyId = activeFamilyId()
        if (familyId.isBlank()) return@withContext

        val config = loadConfig()
        if (!config.enabled) {
            cancelAll()
            return@withContext
        }

        val signals = buildSignals(familyId)
        val plan = buildPlan(config, signals)

        cancelAll()
        plan.forEach { schedule(it) }
        KBLog.app.info("Nudge: pianificati ${plan.size}", TAG)
    }

    // ── Configurazione ───────────────────────────────────────────────────────

    /**
     * Legge `config/nudges`, con il catalogo compilato come rete di sicurezza.
     * Un errore qui (documento assente, permessi, campo malformato) non deve
     * spegnere il motore: si continua con il default.
     */
    private suspend fun loadConfig(): NudgeConfig {
        cachedConfig?.let { return it }
        val remote = runCatching {
            FirebaseFirestore.getInstance()
                .collection("config").document("nudges").get().await().data
        }.getOrNull()
        val config = NudgeConfig.fromRemote(remote)
        cachedConfig = config
        return config
    }

    // ── Segnali locali ───────────────────────────────────────────────────────

    /**
     * Fotografia dello stato dell'utente, letta dal database locale. Costruita
     * una volta per valutazione e mai persistita.
     */
    private data class Signals(
        val familyMembers: Int,
        val unusedFeatures: Set<NudgeFeature>,
    )

    private suspend fun buildSignals(familyId: String): Signals {
        val unused = buildSet {
            if (signalsDao.documentCount(familyId) == 0) add(NudgeFeature.DOCUMENTS)
            if (signalsDao.walletTicketCount(familyId) == 0) add(NudgeFeature.WALLET)
            if (signalsDao.medicalExamCount(familyId) == 0) add(NudgeFeature.HEALTH)
            if (signalsDao.chatMessageCount(familyId) == 0) add(NudgeFeature.CHAT)
            if (signalsDao.calendarEventCount(familyId) == 0) add(NudgeFeature.CALENDAR)
            if (signalsDao.aiConversationCount(familyId) == 0) add(NudgeFeature.AI)
        }
        return Signals(
            familyMembers = signalsDao.familyMemberCount(familyId),
            unusedFeatures = unused,
        )
    }

    // ── Pianificazione ───────────────────────────────────────────────────────

    private data class PlannedNudge(val campaign: NudgeCampaign, val fireAtMillis: Long)

    /**
     * Costruisce la coda: campagne ammissibili in ordine, distanziate dal
     * cooldown globale, ognuna non prima di quando le sue regole permettono.
     */
    private fun buildPlan(config: NudgeConfig, signals: Signals): List<PlannedNudge> {
        val now = System.currentTimeMillis()

        // Tetto trimestrale: conta anche quelli già consegnati.
        val remainingQuarter = config.maxPerQuarter - NudgeState.firesInLastDays(context, 90)
        if (remainingQuarter <= 0) return emptyList()

        // Il cursore parte dal primo istante in cui è lecito notificare
        // qualsiasi cosa: mai prima che il cooldown dall'ultimo invio sia
        // scaduto.
        var cursor = now
        NudgeState.lastFireAny(context)?.let { last ->
            cursor = maxOf(cursor, last + config.globalCooldownDays * DAY_MS)
        }

        val plan = mutableListOf<PlannedNudge>()
        val installMillis = NudgeState.installMillis(context)

        for (campaign in config.campaigns.sortedBy { it.order }) {
            if (!campaign.enabled) continue
            if (!isEligible(campaign, signals)) continue

            val remaining = campaign.maxFires - NudgeState.fireCount(context, campaign.id)
            if (remaining <= 0) continue

            // Base della ripetizione: l'ultimo invio di QUESTA campagna se c'è
            // stato, altrimenti l'installazione più il ritardo iniziale.
            var campaignCursor = NudgeState.lastFire(context, campaign.id)
                ?.let { it + campaign.repeatEveryDays * DAY_MS }
                ?: (installMillis + campaign.firstDelayDays * DAY_MS)

            repeat(remaining) {
                if (plan.size >= MAX_SCHEDULED || plan.size >= remainingQuarter) return plan
                val fire = adjustForQuietHours(maxOf(cursor, campaignCursor), config)
                plan += PlannedNudge(campaign, fire)
                cursor = fire + config.globalCooldownDays * DAY_MS
                campaignCursor = fire + campaign.repeatEveryDays * DAY_MS
            }
        }
        return plan
    }

    private fun isEligible(campaign: NudgeCampaign, signals: Signals): Boolean {
        val r = campaign.requires
        r.familyMembersMax?.let { if (signals.familyMembers > it) return false }
        r.familyMembersMin?.let { if (signals.familyMembers < it) return false }
        r.featureUnused?.let { if (it !in signals.unusedFeatures) return false }
        if (isCoveredByChecklist(campaign)) return false
        return true
    }

    /**
     * Una richiesta già in vista nella Home non ha bisogno anche della push.
     *
     * È un rinvio, non un annullamento: la coda si ricostruisce a ogni
     * foreground, quindi appena la checklist esce di scena — completata, chiusa
     * o mai mostrata — la campagna torna ammissibile e riparte con le sue
     * cadenze. Senza questo filtro `family_invite` scatterebbe il giorno dopo
     * l'installazione per chiedere esattamente la cosa che l'utente si vede
     * scritta in cima alla Home ogni volta che apre l'app.
     */
    private fun isCoveredByChecklist(campaign: NudgeCampaign): Boolean {
        val step = when (campaign.destination) {
            NudgeDestination.INVITE -> OnboardingStep.INVITE
            NudgeDestination.DOCUMENTS -> OnboardingStep.DOCUMENT
            NudgeDestination.CALENDAR -> OnboardingStep.CALENDAR_EVENT
            // Wallet, salute, AI e chat non sono passi della checklist: nessuna
            // sovrapposizione da evitare.
            NudgeDestination.WALLET, NudgeDestination.HEALTH,
            NudgeDestination.AI, NudgeDestination.CHAT, null -> return false
        }
        return step in OnboardingChecklistState.liveSteps(context)
    }

    /**
     * Sposta l'orario fuori dalla fascia silenziosa. Un suggerimento che sveglia
     * qualcuno alle 3 di notte non viene letto: viene disattivato, e quella è
     * una porta che non si riapre.
     */
    private fun adjustForQuietHours(atMillis: Long, config: NudgeConfig): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = atMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val start = config.quietHoursStart
        val end = config.quietHoursEnd

        // Fascia che scavalca la mezzanotte (es. 21 → 9), il caso normale.
        val inQuiet = if (start > end) hour >= start || hour < end else hour in start until end
        if (!inQuiet) return atMillis

        cal.set(Calendar.HOUR_OF_DAY, end)
        cal.set(Calendar.MINUTE, 30)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= atMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    // ── Consegna ─────────────────────────────────────────────────────────────

    private fun schedule(item: PlannedNudge) {
        val pi = pendingIntent(item.campaign, item.fireAtMillis)
        // Allarme INESATTO di proposito: un suggerimento non merita il budget
        // degli allarmi esatti, che è riservato ai promemoria che l'utente ha
        // chiesto (cure, scadenze). `setAndAllowWhileIdle` sopravvive comunque
        // al Doze, che è ciò che serve per raggiungere chi non apre l'app.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, item.fireAtMillis, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, item.fireAtMillis, pi)
            }
        }.onSuccess {
            // Si registra alla PIANIFICAZIONE, non alla consegna: al momento
            // della consegna l'app può essere chiusa e non ci sarebbe nessuno
            // ad aggiornare lo stato, col risultato di ripianificare
            // all'infinito lo stesso nudge.
            NudgeState.recordFire(context, item.campaign.id, item.fireAtMillis)
            KBAnalytics.logNudge("nudge_scheduled", item.campaign.id)
        }.onFailure {
            KBLog.app.warning("Nudge non pianificato: ${it.message}", TAG)
        }
    }

    private fun cancelAll() {
        for (campaign in NudgeConfig.BUILT_IN.campaigns) {
            val pi = pendingIntent(campaign, 0L)
            alarmManager.cancel(pi)
            pi.cancel()
        }
        // Le pianificazioni cancellate vanno tolte anche dallo storico,
        // altrimenti un nudge mai consegnato consumerebbe per sempre una delle
        // occasioni della sua campagna.
        val now = System.currentTimeMillis()
        NudgeState.setFires(context, NudgeState.fires(context).filter { it.atMillis <= now })
    }

    /**
     * Il requestCode dipende SOLO dall'id campagna: è ciò che permette a
     * [cancelAll] di ritrovare e annullare un allarme senza conoscerne l'orario.
     */
    private fun pendingIntent(campaign: NudgeCampaign, fireAtMillis: Long): PendingIntent {
        val intent = Intent(context, NudgeReceiver::class.java).apply {
            action = "kb.nudge.${campaign.id}"
            putExtra(NudgeReceiver.EXTRA_CAMPAIGN_ID, campaign.id)
            putExtra(NudgeReceiver.EXTRA_TITLE, resolve(campaign.titleRes, campaign.titleFallback))
            putExtra(NudgeReceiver.EXTRA_BODY, resolve(campaign.bodyRes, campaign.bodyFallback))
            putExtra(NudgeReceiver.EXTRA_DESTINATION, campaign.destination?.name.orEmpty())
            putExtra("fireAt", fireAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            ("nudge:" + campaign.id).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Risorsa locale se c'è (tradotta), altrimenti il testo remoto.
     *
     * NON usa `context.getString` direttamente: `context` è
     * l'`@ApplicationContext`, e la lingua scelta in-app è impostata con
     * `AppCompatDelegate.setApplicationLocales`, che sotto API 33 è gestita da
     * AppCompat a livello di Activity — l'application context resterebbe sulla
     * lingua di SISTEMA. Il risultato sarebbe subdolo: interfaccia in francese e
     * notifica in italiano, solo sui dispositivi vecchi.
     *
     * La lingua si risolve al momento della PIANIFICAZIONE, non della consegna,
     * perché il testo viaggia negli extra dell'allarme. Non è un problema:
     * cambiare lingua ricrea le Activity, quindi si ripassa da foreground e la
     * coda viene ricalcolata con i testi nuovi.
     */
    private fun resolve(res: Int?, fallback: String?): String =
        res?.let { runCatching { localizedContext().getString(it) }.getOrNull() }
            ?: fallback.orEmpty()

    private fun localizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(config)
    }

    private fun activeFamilyId(): String =
        context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
            .getString("active_family_id", null).orEmpty()

    companion object {
        private const val TAG = "Nudge"
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * Quanti invii futuri tenere in coda. Non serve pianificare tutto: la
         * coda viene ricalcolata a ogni apertura, e ogni allarme pendente in più
         * è un allarme in più da annullare quando le condizioni cambiano.
         */
        private const val MAX_SCHEDULED = 6
    }
}
