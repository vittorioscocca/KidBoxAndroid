package it.vittorioscocca.kidbox.feature.insights

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un insight salvato sul telefono — gemello di `KBInsight.swift`. Vive solo in
 * locale e non si sincronizza: lo stato è di chi ha il telefono.
 *
 * L'id è la chiave deterministica famiglia + tipo + entità: rivalutare la
 * stessa regola aggiorna la riga invece di crearne un'altra.
 */
data class InsightRecord(
    val id: String,
    val familyId: String,
    val recipientUid: String,
    val typeRaw: String,
    val entityId: String,
    val priority: InsightPriority,
    val state: InsightState,
    val fireAtMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val stateChangedAtMillis: Long? = null,
    /** Quando la notifica è stata (o sarà) consegnata. */
    val notifiedAtMillis: Long? = null,
    /** Quando `insight_shown` è stato registrato. */
    val shownReportedAtMillis: Long? = null,
    /** Quando `insight_acted` è stato registrato. */
    val actedReportedAtMillis: Long? = null,
) {
    val type: InsightType? get() = InsightType.fromRaw(typeRaw)

    fun mark(newState: InsightState, now: Long): InsightRecord =
        if (state == newState) this else copy(state = newState, stateChangedAtMillis = now, updatedAtMillis = now)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("familyId", familyId).put("recipientUid", recipientUid)
        .put("type", typeRaw).put("entityId", entityId).put("priority", priority.raw)
        .put("state", state.raw).put("fireAt", fireAtMillis)
        .put("createdAt", createdAtMillis).put("updatedAt", updatedAtMillis)
        .putOpt("stateChangedAt", stateChangedAtMillis)
        .putOpt("notifiedAt", notifiedAtMillis)
        .putOpt("shownReportedAt", shownReportedAtMillis)
        .putOpt("actedReportedAt", actedReportedAtMillis)

    companion object {
        fun key(familyId: String, type: InsightType, entityId: String) = "$familyId|${type.raw}|$entityId"

        fun fromJson(o: JSONObject): InsightRecord = InsightRecord(
            id = o.getString("id"),
            familyId = o.getString("familyId"),
            recipientUid = o.optString("recipientUid"),
            typeRaw = o.getString("type"),
            entityId = o.getString("entityId"),
            priority = InsightPriority.fromRaw(o.optInt("priority")),
            state = InsightState.fromRaw(o.optString("state")),
            fireAtMillis = o.getLong("fireAt"),
            createdAtMillis = o.optLong("createdAt"),
            updatedAtMillis = o.optLong("updatedAt"),
            stateChangedAtMillis = o.optLongOrNull("stateChangedAt"),
            notifiedAtMillis = o.optLongOrNull("notifiedAt"),
            shownReportedAtMillis = o.optLongOrNull("shownReportedAt"),
            actedReportedAtMillis = o.optLongOrNull("actedReportedAt"),
        )

        fun listToJson(rows: List<InsightRecord>): String = JSONArray(rows.map { it.toJson() }).toString()

        fun listFromJson(raw: String?): List<InsightRecord> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() }
            }.getOrDefault(emptyList())
        }
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

/** Cosa è cambiato in una riconciliazione: la consegna riparte da qui. */
data class InsightChanges(
    val opened: List<String> = emptyList(),
    val rescheduled: List<String> = emptyList(),
    val resolved: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = opened.isEmpty() && rescheduled.isEmpty() && resolved.isEmpty()
}

/**
 * Riconciliazione dei candidati con le righe già salvate — come
 * `KBInsightStore.reconcile` su iOS:
 * - candidato nuovo → riga nuova (`NEW`);
 * - candidato noto → stesso stato, orario e priorità aggiornati; una riga chiusa
 *   dal motore (`DONE`) torna `NEW`, una scartata (`DISMISSED`) resta scartata;
 * - riga aperta che nessuna regola propone più → `DONE`.
 * Le righe di altre famiglie restano com'erano.
 */
object InsightReconciler {

    fun reconcile(
        existing: List<InsightRecord>,
        candidates: List<InsightCandidate>,
        familyId: String,
        uid: String,
        now: Long,
    ): Pair<List<InsightRecord>, InsightChanges> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        val opened = mutableListOf<String>()
        val rescheduled = mutableListOf<String>()
        val resolved = mutableListOf<String>()
        val proposed = mutableSetOf<String>()

        for (c in candidates) {
            val key = InsightRecord.key(familyId, c.type, c.entityId)
            proposed += key
            val row = byId[key]
            if (row == null) {
                byId[key] = InsightRecord(
                    id = key, familyId = familyId, recipientUid = uid, typeRaw = c.type.raw,
                    entityId = c.entityId, priority = c.priority, state = InsightState.NEW,
                    fireAtMillis = c.fireAtMillis, createdAtMillis = now, updatedAtMillis = now,
                )
                opened += key
                continue
            }
            var next = if (row.state == InsightState.DONE) row.mark(InsightState.NEW, now) else row
            if (next.priority != c.priority || next.fireAtMillis != c.fireAtMillis) {
                next = next.copy(priority = c.priority, fireAtMillis = c.fireAtMillis, updatedAtMillis = now)
            }
            when {
                next.state != row.state -> opened += key
                next.state.isOpen && (next.priority != row.priority || next.fireAtMillis != row.fireAtMillis) ->
                    rescheduled += key
            }
            byId[key] = next
        }

        for (row in existing) {
            if (row.familyId != familyId || !row.state.isOpen || row.type == null || row.id in proposed) continue
            byId[row.id] = row.mark(InsightState.DONE, now)
            resolved += row.id
        }
        return byId.values.sortedBy { it.id } to InsightChanges(opened, rescheduled, resolved)
    }
}

/**
 * Preferenze degli avvisi — gemello di `KBInsightPreferences.swift`. Stesso
 * schema in `users/{uid}.insightPrefs`, così valgono su ogni dispositivo.
 */
data class InsightPreferences(
    val delivery: Delivery = Delivery.IMMEDIATE,
    /** Minuti dalla mezzanotte; se l'inizio viene dopo la fine, scavalca la mezzanotte. */
    val quietStartMinutes: Int = 21 * 60 + 30,
    val quietEndMinutes: Int = 8 * 60,
    val mutedTypes: Set<InsightType> = emptySet(),
    /** Delega (livello 5): la esegue il server (`delegateUnassignedTodos`). */
    val delegateAsk: Boolean = false,
) {
    enum class Delivery(val raw: String) {
        IMMEDIATE("immediate"), EVENING_DIGEST("eveningDigest"), OFF("off");

        companion object {
            fun fromRaw(raw: String?): Delivery? = entries.firstOrNull { it.raw == raw }
        }
    }

    fun isMuted(type: InsightType) = type in mutedTypes

    fun isQuiet(minutes: Int): Boolean {
        if (quietStartMinutes == quietEndMinutes) return false
        return if (quietStartMinutes < quietEndMinutes) {
            minutes >= quietStartMinutes && minutes < quietEndMinutes
        } else {
            minutes >= quietStartMinutes || minutes < quietEndMinutes
        }
    }

    fun toFirestore(): Map<String, Any> = mapOf(
        "delivery" to delivery.raw,
        "quietStart" to quietStartMinutes,
        "quietEnd" to quietEndMinutes,
        "mutedTypes" to mutedTypes.map { it.raw }.sorted(),
        "delegateAsk" to delegateAsk,
    )

    companion object {
        const val DIGEST_MINUTES = 19 * 60

        fun fromFirestore(map: Map<*, *>): InsightPreferences {
            val d = InsightPreferences()
            fun minutes(v: Any?, fallback: Int) = (v as? Number)?.toInt()?.takeIf { it in 0 until 1440 } ?: fallback
            return InsightPreferences(
                delivery = Delivery.fromRaw(map["delivery"] as? String) ?: d.delivery,
                quietStartMinutes = minutes(map["quietStart"], d.quietStartMinutes),
                quietEndMinutes = minutes(map["quietEnd"], d.quietEndMinutes),
                mutedTypes = (map["mutedTypes"] as? List<*>)
                    ?.mapNotNull { InsightType.fromRaw(it as? String) }?.toSet() ?: emptySet(),
                delegateAsk = map["delegateAsk"] as? Boolean ?: false,
            )
        }
    }
}

/**
 * Quando consegnare — gemello della parte oraria di
 * `KBInsightNotificationScheduler.swift`.
 */
object InsightDelivery {

    /** Margine minimo prima di una consegna «subito». */
    private const val SOON_MS = 5_000L

    /** Nel riepilogo serale vanno i to-do; le dosi restano puntuali. */
    fun goesInDigest(type: InsightType) = type != InsightType.TREATMENT_DOSE_MISSING

    fun deliveryTime(fireAt: Long, type: InsightType, now: Long, prefs: InsightPreferences, zone: ZoneId): Long {
        val earliest = maxOf(fireAt, now + SOON_MS)
        if (type == InsightType.TREATMENT_DOSE_MISSING) return earliest
        return outsideQuietHours(earliest, prefs, zone)
    }

    fun outsideQuietHours(time: Long, prefs: InsightPreferences, zone: ZoneId): Long {
        val z = Instant.ofEpochMilli(time).atZone(zone)
        if (!prefs.isQuiet(z.hour * 60 + z.minute)) return time
        val end = z.toLocalDate()
            .atTime(LocalTime.of(prefs.quietEndMinutes / 60, prefs.quietEndMinutes % 60))
            .atZone(zone)
        val result = if (end.toInstant().toEpochMilli() > time) end else end.plusDays(1)
        return result.toInstant().toEpochMilli()
    }

    /** Il prossimo riepilogo serale (19:00), spostato fuori dal silenzio se serve. */
    fun nextDigest(now: Long, prefs: InsightPreferences, zone: ZoneId): Long {
        val z = Instant.ofEpochMilli(now).atZone(zone)
        var digest = z.toLocalDate()
            .atTime(LocalTime.of(InsightPreferences.DIGEST_MINUTES / 60, InsightPreferences.DIGEST_MINUTES % 60))
            .atZone(zone)
        if (!digest.toInstant().isAfter(Instant.ofEpochMilli(now))) digest = digest.plusDays(1)
        return outsideQuietHours(digest.toInstant().toEpochMilli(), prefs, zone)
    }

    /** Una notifica da armare. */
    data class Planned(val insightId: String, val atMillis: Long)

    /** Il piano di consegna di un giro, e le righe con `notifiedAt` aggiornato. */
    data class Plan(
        val rows: List<InsightRecord>,
        val notifications: List<Planned>,
        val digestAtMillis: Long?,
        val digestIds: List<String>,
        /** Righe chiuse o scartate: anche il banner già mostrato va tolto. */
        val finishedIds: List<String>,
    )

    /**
     * Decide cosa armare. Una consegna già decisa per l'orario attuale non si
     * ripete: se è futura resta armata, se è passata è già arrivata.
     * @param resetFuture le preferenze sono cambiate: le consegne non ancora
     *   avvenute si ricalcolano.
     */
    fun plan(
        rows: List<InsightRecord>,
        familyId: String,
        prefs: InsightPreferences,
        now: Long,
        zone: ZoneId,
        resetFuture: Boolean = false,
    ): Plan {
        val digestAt = if (prefs.delivery == InsightPreferences.Delivery.EVENING_DIGEST) nextDigest(now, prefs, zone) else null
        val notifications = mutableListOf<Planned>()
        val digestIds = mutableListOf<String>()
        val finished = mutableListOf<String>()
        val out = rows.map { original ->
            if (original.familyId != familyId) return@map original
            var row = original
            if (resetFuture && row.state.isOpen && (row.notifiedAtMillis ?: 0) > now) row = row.copy(notifiedAtMillis = null)
            val type = row.type
            if (!row.state.isOpen || type == null) {
                finished += row.id
                return@map row
            }
            if (digestAt != null && goesInDigest(type)) {
                val n = row.notifiedAtMillis
                val undelivered = n == null || n < row.fireAtMillis || n > now
                if (undelivered && row.fireAtMillis <= digestAt) {
                    digestIds += row.id
                    row = row.copy(notifiedAtMillis = digestAt)
                }
                return@map row
            }
            val n = row.notifiedAtMillis
            if (n != null && n >= row.fireAtMillis) {
                if (n > now) notifications += Planned(row.id, n)
                return@map row
            }
            val at = deliveryTime(row.fireAtMillis, type, now, prefs, zone)
            notifications += Planned(row.id, at)
            row.copy(notifiedAtMillis = at)
        }
        return Plan(out, notifications, digestAt?.takeIf { digestIds.isNotEmpty() }, digestIds, finished)
    }
}
