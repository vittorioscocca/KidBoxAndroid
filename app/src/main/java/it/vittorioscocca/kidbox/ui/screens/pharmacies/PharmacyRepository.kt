package it.vittorioscocca.kidbox.ui.screens.pharmacies

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.ui.screens.news.NewsFamilyStore
import it.vittorioscocca.kidbox.ui.screens.news.NewsPlace
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Errori delle farmacie di turno, già con il testo da mostrare. */
class PharmacyError(val text: String, val planRequired: Boolean = false) : Exception(text)

/**
 * Le tre callable delle farmacie di turno. Presidio di servizio di /gating-pro:
 * il Free si ferma prima della rete; il presidio vero resta il server
 * (`gate()` in functions/pharmacies). Gemello di `PharmacyService.swift`.
 */
@Singleton
class PharmacyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val familyStore: NewsFamilyStore,
) {
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    suspend fun fetch(familyId: String, place: NewsPlace): PharmacyFeed {
        ensurePaidPlan()
        familyStore.settled()
        return PharmacyParser.feed(call("getPharmacies", payload(familyId, place), 70))
    }

    /** `exists`, `found`, `unreadable` o `not-found`. Scala i messaggi AI. */
    suspend fun findSource(familyId: String, place: NewsPlace): String {
        ensurePaidPlan()
        familyStore.settled()
        return call("findPharmacySource", payload(familyId, place), 300)["status"] as? String ?: "not-found"
    }

    /**
     * `url == null` toglie la scelta e torna alla fonte automatica. Risposta:
     * `ok`, `unreadable` (resta il link) o `cleared`.
     */
    suspend fun setSource(familyId: String, place: NewsPlace, url: String?): String {
        ensurePaidPlan()
        familyStore.settled()
        val body = payload(familyId, place).toMutableMap<String, Any?>()
        body["url"] = url
        return call("setPharmacySource", body, 190)["status"] as? String ?: "unreadable"
    }

    private fun payload(familyId: String, place: NewsPlace): Map<String, Any?> =
        hashMapOf("familyId" to familyId, "place" to place.toMap())

    /** Il piano, non `aiAccessBlocked`: quello è falso anche per un Free col bonus intatto. */
    private fun ensurePaidPlan() {
        if (CurrentPlanStore.plan.value == KBPlan.FREE) {
            throw PharmacyError(context.getString(R.string.pharmacies_error_plan), planRequired = true)
        }
    }

    private suspend fun call(name: String, payload: Map<String, Any?>, timeoutSeconds: Long): Map<*, *> {
        try {
            val result = functions.getHttpsCallable(name)
                .withTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .call(payload)
                .await()
            return result.getData() as? Map<*, *> ?: throw PharmacyError(context.getString(R.string.pharmacies_error_unexpected))
        } catch (e: PharmacyError) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            throw map(e)
        } catch (e: IOException) {
            throw PharmacyError(context.getString(R.string.pharmacies_error_offline))
        }
    }

    private fun map(e: FirebaseFunctionsException): PharmacyError {
        val details = e.details as? Map<*, *>
        val reason = details?.get("reason") as? String
        val units = (details?.get("units") as? Number)?.toInt()
        val remaining = (details?.get("remaining") as? Number)?.toInt()
        val code = FirebaseFunctionsException.Code.values().firstOrNull { it == e.code }
        return when {
            code == FirebaseFunctionsException.Code.PERMISSION_DENIED && reason == "plan" ->
                PharmacyError(context.getString(R.string.pharmacies_error_plan), planRequired = true)
            code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED && reason == "pharmacies-budget" ->
                PharmacyError(context.getString(R.string.pharmacies_error_budget))
            code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED && reason == "pharmacies-attempts" ->
                PharmacyError(context.getString(R.string.pharmacies_error_attempts))
            code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                PharmacyError(
                    if (units != null && remaining != null) context.getString(R.string.pharmacies_error_quota, units, remaining)
                    else context.getString(R.string.pharmacies_error_quota_plain),
                )
            code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && reason == "pharmacies-disabled" ->
                PharmacyError(context.getString(R.string.pharmacies_error_disabled))
            code == FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                PharmacyError(context.getString(R.string.pharmacies_error_unreachable))
            code == FirebaseFunctionsException.Code.INVALID_ARGUMENT && reason == "invalid-url" ->
                PharmacyError(context.getString(R.string.pharmacies_error_invalid_url))
            code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED || code == FirebaseFunctionsException.Code.UNAVAILABLE ->
                PharmacyError(context.getString(R.string.pharmacies_error_slow))
            else -> PharmacyError(context.getString(R.string.pharmacies_error_unexpected))
        }
    }
}
