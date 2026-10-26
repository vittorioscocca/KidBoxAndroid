package it.vittorioscocca.kidbox.data.health.fitness

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.health.HealthConnectGateway
import it.vittorioscocca.kidbox.domain.model.HealthWorkoutEntry
import it.vittorioscocca.kidbox.ui.screens.health.FitnessActivityIcons
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Scarpe da corsa del Piano Fitness: marca, data d'acquisto, km massimi e foto.
 * I km percorsi si ricavano dalle corse chiuse nel piano (sedute fatte e
 * allenamenti Health Connect) e si scalano dai km massimi.
 *
 * Perché un registro e non un ricalcolo dal piano: il piano dura un mese e la
 * rigenerazione lo sostituisce (di sedute restano solo i consuntivi), quindi
 * una scarpa che dura 700 km perderebbe la storia a ogni nuovo piano. Ogni
 * scarpa si porta dietro [FitnessShoe.runs]: id della corsa → metri. L'id è
 * quello della seduta o dell'allenamento, così riconciliare due volte non conta
 * due volte. Parity con `FitnessShoes.swift` su iOS.
 *
 * Percorso Firestore: `users/{uid}/fitnessShoes/{shoeId}` (privato per-utente).
 * I campi sono esplicitati (non un `payload` JSON) e uguali a quelli di iOS.
 */
data class FitnessShoe(
    val id: String = UUID.randomUUID().toString(),
    /** Profilo del piano a cui appartengono le corse (stesso `childId` del piano). */
    val childId: String,
    val brand: String,
    /** Modello o soprannome, facoltativo. */
    val model: String = "",
    val purchaseDateEpochMillis: Long,
    val maxKm: Double,
    /** JPEG ridotto (lato lungo ≤ 480 px) in base64: viaggia nel documento Firestore. */
    val photoBase64: String = "",
    /** Da questo momento la scarpa non riceve più corse. */
    val retiredAtEpochMillis: Long? = null,
    val isDeleted: Boolean = false,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    /** id della corsa → metri. */
    val runs: Map<String, Double> = emptyMap(),
    /**
     * Correzione a mano, in km, che si somma alle corse (può essere negativa):
     * per le scarpe già usate prima di averle inserite o per un conteggio
     * sbagliato.
     */
    val manualKm: Double = 0.0,
    /** id della corsa → minuti, per le corse che li hanno (tempo e andatura). */
    val runMinutes: Map<String, Int> = emptyMap(),
) {
    val displayName: String
        get() = listOf(brand, model).map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" ").ifEmpty { brand }
    /** Km delle sole corse registrate. */
    val runsKm: Double get() = runs.values.sum() / 1000.0
    val usedKm: Double get() = (runsKm + manualKm).coerceAtLeast(0.0)

    /** Corse registrate (la correzione a mano non è una corsa). */
    val runCount: Int get() = runs.size

    private val timedRuns: List<Pair<Double, Int>>
        get() = runMinutes.mapNotNull { (id, minutes) -> runs[id]?.let { it to minutes } }

    val totalMinutes: Int get() = timedRuns.sumOf { it.second }

    /**
     * Secondi per km, calcolati sulle sole corse che hanno durata e distanza,
     * così una corsa senza tempo non falsa l'andatura. `null` se non ce n'è.
     */
    val averagePaceSecondsPerKm: Double?
        get() {
            val km = timedRuns.sumOf { it.first } / 1000.0
            val minutes = totalMinutes
            return if (km >= 0.1 && minutes > 0) minutes * 60.0 / km else null
        }
    val remainingKm: Double get() = (maxKm - usedKm).coerceAtLeast(0.0)
    val progress: Float get() = if (maxKm > 0) (usedKm / maxKm).coerceIn(0.0, 1.0).toFloat() else 0f
    val isWornOut: Boolean get() = maxKm > 0 && usedKm >= maxKm
    val isRetired: Boolean get() = retiredAtEpochMillis != null
}

object FitnessShoeLedger {

    data class Run(val id: String, val dateEpochMillis: Long, val meters: Double, val minutes: Int? = null)

    /**
     * Le corse con distanza del piano: sedute fatte e allenamenti Health
     * Connect non abbinati. Un allenamento già abbinato a una seduta sta nella
     * seduta (`matchedWorkoutId`) e non compare fra i `loggedWorkouts`.
     */
    fun runs(plan: FitnessPlanDocument): List<Run> {
        val result = mutableListOf<Run>()
        plan.doneHistory.forEach { session ->
            val meters = session.actualDistanceMeters ?: return@forEach
            if (meters < 10.0) return@forEach
            val actual = session.actualActivityTitle?.takeIf { it.isNotBlank() }
            val isRun = if (actual != null) {
                FitnessActivityIcons.isRunWorkout(actual)
            } else {
                FitnessActivityIcons.isRunSession(session.activityType, session.title)
            }
            if (!isRun) return@forEach
            result += Run(
                id = session.matchedWorkoutId ?: session.id,
                dateEpochMillis = session.completedAtEpochMillis ?: session.dateEpochMillis,
                meters = meters,
                minutes = session.actualMinutes,
            )
        }
        plan.loggedWorkouts.forEach { workout ->
            val meters = workout.distanceMeters ?: return@forEach
            if (meters >= 10.0 && FitnessActivityIcons.isRunWorkout(workout.title)) {
                result += Run(workout.id, workout.dateEpochMillis, meters, workout.durationMinutes)
            }
        }
        return result
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Le corse lette direttamente da Health Connect: tutte le corse registrate,
     * anche quelle che non c'entrano con nessuna seduta del piano. L'id è quello
     * dell'allenamento, lo stesso che `matchedWorkoutId` ricorda sulla seduta
     * chiusa: se la corsa è anche nel piano, conta una volta sola.
     */
    fun runs(workouts: List<HealthWorkoutEntry>): List<Run> = workouts.mapNotNull { workout ->
        val meters = workout.distanceMeters ?: return@mapNotNull null
        if (meters < 10.0 || !FitnessActivityIcons.isRunWorkout(workout.title)) return@mapNotNull null
        Run(workout.id, workout.startedAtEpochMillis, meters, workout.durationMinutes)
    }

    /** La scarpa che riceve una corsa: la più recente fra quelle già comprate e non ritirate quel giorno. */
    fun owner(run: Run, shoes: List<FitnessShoe>): FitnessShoe? {
        val day = startOfDay(run.dateEpochMillis)
        return shoes
            .filter { shoe ->
                !shoe.isDeleted &&
                    startOfDay(shoe.purchaseDateEpochMillis) <= day &&
                    (shoe.retiredAtEpochMillis == null || run.dateEpochMillis < shoe.retiredAtEpochMillis)
            }
            .maxByOrNull { it.purchaseDateEpochMillis }
    }

    /**
     * Aggiunge al registro le corse nuove e aggiorna i metri di quelle già
     * viste. Non toglie mai niente: una corsa sparita dal piano resta alla
     * scarpa che l'ha consumata. Restituisce le scarpe cambiate.
     */
    fun reconcile(shoes: List<FitnessShoe>, plan: FitnessPlanDocument, now: Long = System.currentTimeMillis()): List<FitnessShoe> =
        reconcile(shoes, runs(plan), now)

    fun reconcile(shoes: List<FitnessShoe>, runs: List<Run>, now: Long = System.currentTimeMillis()): List<FitnessShoe> {
        val updated = shoes.toMutableList()
        val changed = mutableSetOf<String>()
        for (run in runs) {
            val known = updated.indexOfFirst { it.runs.containsKey(run.id) }
            if (known >= 0) {
                if (updated[known].runs[run.id] != run.meters) {
                    updated[known] = updated[known].copy(runs = updated[known].runs + (run.id to run.meters))
                    changed += updated[known].id
                }
                // Le corse registrate prima che si tenesse il tempo lo ricevono qui.
                if (run.minutes != null && updated[known].runMinutes[run.id] != run.minutes) {
                    updated[known] = updated[known].copy(runMinutes = updated[known].runMinutes + (run.id to run.minutes))
                    changed += updated[known].id
                }
                continue
            }
            val target = owner(run, updated) ?: continue
            val index = updated.indexOfFirst { it.id == target.id }
            updated[index] = updated[index].copy(
                runs = updated[index].runs + (run.id to run.meters),
                runMinutes = if (run.minutes != null) updated[index].runMinutes + (run.id to run.minutes) else updated[index].runMinutes,
            )
            changed += target.id
        }
        return updated.filter { it.id in changed }.map { it.copy(updatedAtEpochMillis = now) }
    }

    /** Vince la copia più recente per i campi; le corse si sommano per id. */
    fun merge(local: FitnessShoe, remote: FitnessShoe): FitnessShoe {
        val localWins = local.updatedAtEpochMillis >= remote.updatedAtEpochMillis
        val winner = if (localWins) local else remote
        val loser = if (localWins) remote else local
        return winner.copy(runs = loser.runs + winner.runs, runMinutes = loser.runMinutes + winner.runMinutes)
    }
}

@Singleton
class FitnessShoeStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnect: HealthConnectGateway,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remote = FitnessShoeRemoteStore()

    private fun file(childId: String): File {
        val dir = File(context.filesDir, "fitness_shoes").apply { mkdirs() }
        return File(dir, "shoes_$childId.json")
    }

    /** Tutte le scarpe del profilo, comprese le eliminate (servono a propagare l'eliminazione). */
    fun loadAll(childId: String): List<FitnessShoe> {
        val file = file(childId)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(file.readText())
            (0 until array.length()).map { decode(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Scarpe visibili: più recenti per prime. */
    fun load(childId: String): List<FitnessShoe> =
        loadAll(childId).filter { !it.isDeleted }.sortedByDescending { it.purchaseDateEpochMillis }

    private fun saveAll(childId: String, shoes: List<FitnessShoe>) {
        val array = org.json.JSONArray().apply { shoes.forEach { put(encode(it)) } }
        file(childId).writeText(array.toString())
    }

    /** Crea o modifica una scarpa e la manda a Firestore. */
    fun upsert(shoe: FitnessShoe) {
        val stamped = shoe.copy(updatedAtEpochMillis = System.currentTimeMillis())
        val all = loadAll(shoe.childId).filter { it.id != shoe.id } + stamped
        saveAll(shoe.childId, all)
        scope.launch { remote.upsert(stamped) }
    }

    fun delete(shoe: FitnessShoe) = upsert(shoe.copy(isDeleted = true, photoBase64 = ""))

    /**
     * Chiamata a ogni salvataggio del piano: porta in registro le corse nuove.
     * Non fa nulla (né rete) se l'utente non ha scarpe.
     */
    fun reconcile(childId: String, plan: FitnessPlanDocument) {
        val all = loadAll(childId)
        if (all.none { !it.isDeleted }) return
        val changed = FitnessShoeLedger.reconcile(all, plan)
        if (changed.isEmpty()) return
        val byId = changed.associateBy { it.id }
        saveAll(childId, all.map { byId[it.id] ?: it })
        scope.launch { changed.forEach { remote.upsert(it) } }
    }

    /**
     * Porta in registro le corse di Health Connect dalla data d'acquisto della
     * scarpa più vecchia in poi. È l'innesco principale: il piano vede solo le
     * uscite che chiudono una seduta, Health Connect le vede tutte.
     */
    suspend fun syncFromHealth(childId: String) {
        val oldest = loadAll(childId).filter { !it.isDeleted }.minOfOrNull { it.purchaseDateEpochMillis } ?: return
        val workouts = healthConnect.runningWorkoutsSince(FitnessPlanDates.startOfDay(oldest))
        val runs = FitnessShoeLedger.runs(workouts)
        if (runs.isEmpty()) return
        // Si rilegge dopo l'attesa: nel frattempo l'utente può aver modificato.
        val all = loadAll(childId)
        val changed = FitnessShoeLedger.reconcile(all, runs)
        if (changed.isEmpty()) return
        val byId = changed.associateBy { it.id }
        saveAll(childId, all.map { byId[it.id] ?: it })
        changed.forEach { remote.upsert(it) }
    }

    /** Allinea con Firestore all'apertura delle impostazioni. */
    suspend fun pull(childId: String): List<FitnessShoe> {
        val local = loadAll(childId)
        val fetched = remote.fetchAll(childId) ?: return load(childId)
        val merged = local.associateBy { it.id }.toMutableMap()
        val toPush = mutableListOf<FitnessShoe>()
        for (theirs in fetched) {
            val mine = merged[theirs.id]
            if (mine == null) {
                merged[theirs.id] = theirs
            } else {
                val both = FitnessShoeLedger.merge(mine, theirs)
                merged[theirs.id] = both
                if (both != theirs) toPush += both
            }
        }
        val remoteIds = fetched.map { it.id }.toSet()
        toPush += local.filter { it.id !in remoteIds }
        saveAll(childId, merged.values.toList())
        toPush.forEach { remote.upsert(it) }
        return load(childId)
    }

    private fun encode(shoe: FitnessShoe) = JSONObject().apply {
        put("id", shoe.id)
        put("childId", shoe.childId)
        put("brand", shoe.brand)
        put("model", shoe.model)
        put("purchaseDate", shoe.purchaseDateEpochMillis)
        put("maxKm", shoe.maxKm)
        put("photoBase64", shoe.photoBase64)
        shoe.retiredAtEpochMillis?.let { put("retiredAt", it) }
        put("isDeleted", shoe.isDeleted)
        put("updatedAt", shoe.updatedAtEpochMillis)
        put("runs", JSONObject().apply { shoe.runs.forEach { (k, v) -> put(k, v) } })
        put("manualKm", shoe.manualKm)
        put("runMinutes", JSONObject().apply { shoe.runMinutes.forEach { (k, v) -> put(k, v) } })
    }

    private fun decode(json: JSONObject): FitnessShoe {
        val runsJson = json.optJSONObject("runs")
        val runs = buildMap {
            runsJson?.keys()?.forEach { put(it, runsJson.getDouble(it)) }
        }
        return FitnessShoe(
            id = json.getString("id"),
            childId = json.getString("childId"),
            brand = json.getString("brand"),
            model = json.optString("model", ""),
            purchaseDateEpochMillis = json.getLong("purchaseDate"),
            maxKm = json.getDouble("maxKm"),
            photoBase64 = json.optString("photoBase64", ""),
            retiredAtEpochMillis = if (json.has("retiredAt")) json.getLong("retiredAt") else null,
            isDeleted = json.optBoolean("isDeleted", false),
            updatedAtEpochMillis = json.optLong("updatedAt", 0L),
            runs = runs,
            manualKm = json.optDouble("manualKm", 0.0),
            runMinutes = buildMap {
                json.optJSONObject("runMinutes")?.let { obj -> obj.keys().forEach { put(it, obj.getInt(it)) } }
            },
        )
    }
}

class FitnessShoeRemoteStore {
    private val db get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun collection() = auth.currentUser?.uid?.let { uid ->
        db.collection("users").document(uid).collection("fitnessShoes")
    }

    suspend fun upsert(shoe: FitnessShoe): Boolean {
        val collection = collection() ?: return false
        return runCatching {
            // Senza merge: `runs` è una mappa e una corsa tolta non deve restare.
            collection.document(shoe.id).set(
                mapOf(
                    "childId" to shoe.childId,
                    "brand" to shoe.brand,
                    "model" to shoe.model,
                    "purchaseDate" to Timestamp(Date(shoe.purchaseDateEpochMillis)),
                    "maxKm" to shoe.maxKm,
                    "isDeleted" to shoe.isDeleted,
                    "updatedAt" to Timestamp(Date(shoe.updatedAtEpochMillis)),
                    "runs" to shoe.runs,
                    "manualKm" to shoe.manualKm,
                    "runMinutes" to shoe.runMinutes,
                    "photoBase64" to shoe.photoBase64,
                    "retiredAt" to shoe.retiredAtEpochMillis?.let { Timestamp(Date(it)) },
                ),
            ).await()
        }.isSuccess
    }

    /** `null` se la lettura fallisce (si resta sulla copia locale). */
    suspend fun fetchAll(childId: String): List<FitnessShoe>? {
        val collection = collection() ?: return null
        val snap = runCatching { collection.whereEqualTo("childId", childId).get().await() }
            .getOrNull() ?: return null
        return snap.documents.mapNotNull { doc ->
            val brand = doc.getString("brand") ?: return@mapNotNull null
            val purchase = doc.getTimestamp("purchaseDate") ?: return@mapNotNull null
            val maxKm = doc.getDouble("maxKm") ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val rawRuns = doc.get("runs") as? Map<String, Any?> ?: emptyMap()
            FitnessShoe(
                id = doc.id,
                childId = childId,
                brand = brand,
                model = doc.getString("model").orEmpty(),
                purchaseDateEpochMillis = purchase.toDate().time,
                maxKm = maxKm,
                photoBase64 = doc.getString("photoBase64").orEmpty(),
                retiredAtEpochMillis = doc.getTimestamp("retiredAt")?.toDate()?.time,
                isDeleted = doc.getBoolean("isDeleted") ?: false,
                updatedAtEpochMillis = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
                runs = rawRuns.mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toDouble() } }.toMap(),
                manualKm = doc.getDouble("manualKm") ?: 0.0,
                runMinutes = (doc.get("runMinutes") as? Map<*, *>).orEmpty()
                    .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toInt() } } }
                    .toMap(),
            )
        }
    }
}
