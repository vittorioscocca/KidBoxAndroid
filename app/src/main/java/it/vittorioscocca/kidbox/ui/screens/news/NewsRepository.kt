package it.vittorioscocca.kidbox.ui.screens.news

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.util.KBLocale
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Errori delle Notizie, già con il testo da mostrare. */
sealed class NewsError(open val text: String) : Exception(text) {
    data class PlanRequired(override val text: String) : NewsError(text)
    data class Quota(override val text: String) : NewsError(text)
    data class Generic(override val text: String) : NewsError(text)
}

/**
 * Le due callable delle Notizie. È il presidio di servizio di /gating-pro:
 * ogni percorso passa di qui e il Free si ferma prima della rete. Il presidio
 * vero resta il server (`gate()` in functions/news).
 */
@Singleton
class NewsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val familyStore: NewsFamilyStore,
) {
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    /** L'ultima edizione scaricata, per riaprire la scheda senza attese. */
    @Volatile var cachedFeed: Pair<String, NewsFeed>? = null
        private set

    /**
     * Luogo e lingua sono quelli della famiglia ([NewsFamilyStore]), gli
     * argomenti di chi legge. Prima si aspetta che l'ultima scelta della
     * famiglia sia sul server: il server la legge e la preferisce.
     */
    suspend fun fetchFeed(familyId: String, family: NewsFamilySettings, prefs: NewsPrefs): NewsFeed {
        ensurePaidPlan()
        familyStore.settled()
        val payload = hashMapOf(
            "familyId" to familyId,
            "lang" to family.effectiveLang,
            "timeZone" to TimeZone.getDefault().id,
            "categories" to prefs.categories.map { it.id },
            "place" to family.effectivePlace.toMap(),
        )
        val data = call("getFamilyNews", payload, 70)
        val feed = NewsParser.feed(data)
        cachedFeed = familyId to feed
        return feed
    }

    /**
     * Senza [brief] legge solo le ultime offerte salvate (gratis); con [brief] ne
     * cerca di nuove. Le offerte sono della famiglia: le vede ogni membro.
     */
    suspend fun fetchOffers(familyId: String, family: NewsFamilySettings, brief: NewsBrief?): NewsOffersPayload {
        ensurePaidPlan()
        familyStore.settled()
        val payload = hashMapOf<String, Any>(
            "familyId" to familyId,
            "lang" to family.effectiveLang,
            "timeZone" to TimeZone.getDefault().id,
            "place" to family.effectivePlace.toMap(),
            "refresh" to (brief != null),
        )
        if (brief != null) payload["brief"] = brief.toMap()
        return NewsParser.offers(call("getNewsOffers", payload, 190))
    }

    fun clearCache() {
        cachedFeed = null
    }

    // ── Presidio di piano ───────────────────────────────────────────────────

    /** Il piano, non `aiAccessBlocked`: quello è falso anche per un Free col bonus intatto. */
    private fun ensurePaidPlan() {
        if (CurrentPlanStore.plan.value == KBPlan.FREE) {
            throw NewsError.PlanRequired(context.getString(R.string.news_error_plan))
        }
    }

    // ── Chiamata ed errori ──────────────────────────────────────────────────

    private suspend fun call(name: String, payload: Map<String, Any>, timeoutSeconds: Long): Map<*, *> {
        try {
            val result = functions.getHttpsCallable(name)
                .withTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .call(payload)
                .await()
            return result.getData() as? Map<*, *> ?: throw NewsError.Generic(context.getString(R.string.news_error_unexpected))
        } catch (e: NewsError) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            throw map(e)
        } catch (e: IOException) {
            throw NewsError.Generic(context.getString(R.string.news_error_offline))
        }
    }

    private fun map(e: FirebaseFunctionsException): NewsError {
        val details = e.details as? Map<*, *>
        val reason = details?.get("reason") as? String
        val units = (details?.get("units") as? Number)?.toInt()
        val remaining = (details?.get("remaining") as? Number)?.toInt()
        return when {
            e.code == FirebaseFunctionsException.Code.PERMISSION_DENIED && reason == "plan" ->
                NewsError.PlanRequired(context.getString(R.string.news_error_plan))
            e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED && reason == "news-budget" ->
                NewsError.Generic(context.getString(R.string.news_error_budget))
            e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED && reason == "trial-limit" ->
                NewsError.Quota(context.getString(R.string.news_error_trial))
            e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED && reason == "monthly-limit" &&
                units != null && remaining != null && remaining > 0 ->
                NewsError.Quota(context.getString(R.string.news_error_quota_month_units, units, remaining))
            e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED && reason == "monthly-limit" ->
                NewsError.Quota(context.getString(R.string.news_error_quota_month))
            e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED && units != null && remaining != null ->
                NewsError.Quota(context.getString(R.string.news_error_quota_units, units, remaining))
            e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                NewsError.Quota(context.getString(R.string.news_error_quota))
            e.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && reason == "news-disabled" ->
                NewsError.Generic(context.getString(R.string.news_error_disabled))
            e.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && reason == "news-off" ->
                NewsError.Generic(context.getString(R.string.news_error_family_off))
            e.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT && reason == "empty-brief" ->
                NewsError.Generic(context.getString(R.string.news_error_empty_brief))
            e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
                e.code == FirebaseFunctionsException.Code.UNAVAILABLE ->
                NewsError.Generic(context.getString(R.string.news_error_timeout))
            else -> NewsError.Generic(e.message ?: context.getString(R.string.news_error_unexpected))
        }
    }
}

/**
 * La lingua effettiva dell'app (it/en/fr/es), quella in cui il server scrive
 * le notizie. Inglese con regione Italia resta italiano, come su iOS.
 */
fun newsAppLanguage(): String {
    val locale = KBLocale.current()
    val code = locale.language
    if (code == "en" && locale.country == "IT") return "it"
    return if (code in setOf("it", "en", "fr", "es")) code else "it"
}
