package it.vittorioscocca.kidbox.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.HomeItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentSummary
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyPhotoDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBNoteDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDao
import it.vittorioscocca.kidbox.data.local.dao.KBVaccineDao
import it.vittorioscocca.kidbox.data.local.dao.PetEventDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleEventDao
import it.vittorioscocca.kidbox.data.local.dao.WalletTicketDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.model.VaccineStatus
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray

/** Una data che si avvicina, con l'etichetta da mostrare accanto. */
data class DashboardDeadline(val epochMillis: Long, val title: String)

/**
 * Dati della Dashboard in Home.
 *
 * Vengono da Room, non dall'AI: il briefing del mattino
 * (`DailyBriefingService`) gira una volta al giorno, resta congelato fino
 * all'indomani ed è dietro il piano AI, mentre qui servono conteggi vivi —
 * gli stessi che il briefing raccoglie in locale *prima* di spedirli al modello.
 *
 * Le liste sono già filtrate e ordinate qui: la composizione deve solo scegliere
 * la prima riga e formattarla.
 */
data class HomeDashboardData(
    val upcomingEvents: List<KBCalendarEventEntity> = emptyList(),
    val openTodos: List<KBTodoItemEntity> = emptyList(),
    val toBuy: List<KBGroceryItemEntity> = emptyList(),
    val notes: List<KBNoteEntity> = emptyList(),
    val monthExpensesTotal: Double = 0.0,
    val latestPhotos: List<KBFamilyPhotoEntity> = emptyList(),
    /** Visite e vaccini programmati entro trenta giorni, i più vicini per primi. */
    val upcomingHealth: List<DashboardDeadline> = emptyList(),
    /** Il ripiego di Salute quando non c'è niente in arrivo: le terapie in corso. */
    val ongoingTreatments: List<DashboardDeadline> = emptyList(),
    /** Biglietti e prenotazioni con una data futura. */
    val upcomingTickets: List<DashboardDeadline> = emptyList(),
    /** Documenti mossi negli ultimi novanta giorni, il più recente per primo. */
    val recentDocuments: List<KBDocumentSummary> = emptyList(),
    val upcomingVehicleEvents: List<DashboardDeadline> = emptyList(),
    /** Garanzie in scadenza e manutenzioni in arrivo, fuse: per chi guarda sono
     *  la stessa cosa, una data che si avvicina. */
    val upcomingHomeDeadlines: List<DashboardDeadline> = emptyList(),
    val upcomingPetDeadlines: List<DashboardDeadline> = emptyList(),
    val upcomingTrips: List<DashboardDeadline> = emptyList(),
) {
    /** C'è un evento che parte oggi (o già iniziato e non finito). */
    val hasEventToday: Boolean
        get() {
            val endOfToday = endOfTodayMillis()
            return upcomingEvents.any { it.startDateEpochMillis <= endOfToday }
        }

    /** C'è un to-do scaduto o in scadenza entro stasera. */
    val hasTodoDueToday: Boolean
        get() {
            val endOfToday = endOfTodayMillis()
            return openTodos.any { (it.dueAtEpochMillis ?: Long.MAX_VALUE) <= endOfToday }
        }

    /** Serve solo a capire quando i dati sono arrivati, non a decidere l'ordine. */
    val isEmpty: Boolean
        get() = upcomingEvents.isEmpty() && openTodos.isEmpty() && toBuy.isEmpty() &&
            notes.isEmpty() && monthExpensesTotal <= 0.0 && latestPhotos.isEmpty() &&
            upcomingHealth.isEmpty() && ongoingTreatments.isEmpty() &&
            upcomingTickets.isEmpty() && recentDocuments.isEmpty() &&
            upcomingVehicleEvents.isEmpty() && upcomingHomeDeadlines.isEmpty() &&
            upcomingPetDeadlines.isEmpty() && upcomingTrips.isEmpty()
}

internal fun endOfTodayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

/** Le tre terzine in cui `observe` spezza i flussi, prima di ricomporli. */
private data class OrganizationSlice(
    val events: List<KBCalendarEventEntity>,
    val todos: List<KBTodoItemEntity>,
    val groceries: List<KBGroceryItemEntity>,
    val notes: List<KBNoteEntity>,
    val monthExpensesTotal: Double,
)

private data class CareSlice(
    val photos: List<KBFamilyPhotoEntity>,
    val health: List<DashboardDeadline>,
    val treatments: List<DashboardDeadline>,
    val documents: List<KBDocumentSummary>,
)

private data class LifeSlice(
    val tickets: List<DashboardDeadline>,
    val vehicles: List<DashboardDeadline>,
    val homeDeadlines: List<DashboardDeadline>,
    val pets: List<DashboardDeadline>,
    val trips: List<DashboardDeadline>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeDashboardViewModel @Inject constructor(
    private val calendarEventDao: KBCalendarEventDao,
    private val todoItemDao: KBTodoItemDao,
    private val groceryItemDao: KBGroceryItemDao,
    private val noteDao: KBNoteDao,
    private val expenseDao: KBExpenseDao,
    private val familyPhotoDao: KBFamilyPhotoDao,
    private val medicalVisitDao: KBMedicalVisitDao,
    private val vaccineDao: KBVaccineDao,
    private val treatmentDao: KBTreatmentDao,
    private val documentDao: KBDocumentDao,
    private val walletTicketDao: WalletTicketDao,
    private val vehicleEventDao: VehicleEventDao,
    private val homeItemDao: HomeItemDao,
    private val petEventDao: PetEventDao,
    private val tripDao: KBTripDao,
) : ViewModel() {

    private val familyId = MutableStateFlow("")

    fun bind(id: String) {
        familyId.value = id
    }

    // Niente `distinctUntilChanged`: uno StateFlow non riemette lo stesso valore.
    val data: StateFlow<HomeDashboardData> = familyId
        .flatMapLatest { fid ->
            if (fid.isBlank()) flowOf(HomeDashboardData()) else observe(fid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeDashboardData())

    // `combine` tipizzato arriva a cinque flussi: i quattordici si uniscono in tre gruppi.
    private fun observe(fid: String): Flow<HomeDashboardData> =
        combine(organization(fid), care(fid), life(fid)) { org, care, life ->
            HomeDashboardData(
                upcomingEvents = org.events,
                openTodos = org.todos,
                toBuy = org.groceries,
                notes = org.notes,
                monthExpensesTotal = org.monthExpensesTotal,
                latestPhotos = care.photos,
                upcomingHealth = care.health,
                ongoingTreatments = care.treatments,
                upcomingTickets = life.tickets,
                recentDocuments = care.documents,
                upcomingVehicleEvents = life.vehicles,
                upcomingHomeDeadlines = life.homeDeadlines,
                upcomingPetDeadlines = life.pets,
                upcomingTrips = life.trips,
            )
        }

    private fun organization(fid: String): Flow<OrganizationSlice> = combine(
        calendarEventDao.observeByFamilyId(fid),
        todoItemDao.observeOpenByFamilyId(fid),
        groceryItemDao.observeByFamilyId(fid),
        noteDao.observeByFamilyId(fid),
        expenseDao.observeByFamilyId(fid),
    ) { events, todos, groceries, notes, expenses ->
        val uid = currentUid()
        val now = System.currentTimeMillis()
        val horizon = now + SEVEN_DAYS_MILLIS
        val monthStart = startOfMonthMillis()

        OrganizationSlice(
            // Una tessera non deve svelare quello che la sezione nasconde.
            events = events
                .asSequence()
                .filter { !it.isDeleted }
                .filter { it.endDateEpochMillis >= now && it.startDateEpochMillis <= horizon }
                .filter { visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy, uid) }
                .sortedBy { it.startDateEpochMillis }
                .toList(),
            todos = todos
                .filter { visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy, uid) }
                .sortedBy { it.dueAtEpochMillis ?: Long.MAX_VALUE },
            groceries = groceries
                .filter { !it.isDeleted && !it.isPurchased }
                .sortedByDescending { it.createdAtEpochMillis },
            notes = notes
                .filter { !it.isDeleted }
                .filter { visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy, uid) }
                .sortedByDescending { it.updatedAtEpochMillis },
            monthExpensesTotal = expenses
                .filter { !it.isDeleted && it.dateEpochMillis >= monthStart }
                .sumOf { it.amount },
        )
    }

    private fun care(fid: String): Flow<CareSlice> = combine(
        familyPhotoDao.observeByFamilyId(fid),
        medicalVisitDao.observeByFamilyId(fid),
        vaccineDao.observeByFamilyId(fid),
        treatmentDao.observeByFamilyId(fid),
        documentDao.observeRecentSummariesByFamilyId(fid, System.currentTimeMillis() - NINETY_DAYS_MILLIS),
    ) { photos, visits, vaccines, treatments, documents ->
        val uid = currentUid()
        val now = System.currentTimeMillis()
        // Salute guarda più lontano delle altre: un vaccino si prenota con settimane
        // di anticipo e non ha senso farlo comparire solo la settimana prima.
        val horizon = now + THIRTY_DAYS_MILLIS

        val health = buildList {
            visits
                .filter { it.dateEpochMillis in now..horizon }
                .forEach { add(DashboardDeadline(it.dateEpochMillis, it.reason)) }
            vaccines
                .filter { it.statusRaw != VaccineStatus.ADMINISTERED.rawValue }
                .forEach { vaccine ->
                    val date = vaccine.scheduledDateEpochMillis ?: return@forEach
                    if (date !in now..horizon) return@forEach
                    val name = vaccine.commercialName?.trim().takeUnless { it.isNullOrBlank() }
                        ?: vaccine.name
                    add(DashboardDeadline(date, name))
                }
        }.sortedBy { it.epochMillis }

        CareSlice(
            photos = photos
                .filter { !it.isDeleted }
                .sortedByDescending { it.takenAtEpochMillis }
                .take(4),
            health = health,
            treatments = treatments
                .filter { it.isLongTerm || (it.endDateEpochMillis ?: Long.MAX_VALUE) >= now }
                .map { DashboardDeadline(it.startDateEpochMillis, it.drugName) },
            documents = documents
                .filter { visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy, uid) },
        )
    }

    private fun life(fid: String): Flow<LifeSlice> = combine(
        walletTicketDao.observeActiveByFamilyId(fid, currentUid().orEmpty()),
        vehicleEventDao.observeByFamily(fid),
        homeItemDao.observeByFamily(fid),
        petEventDao.observeByFamily(fid),
        tripDao.observeAll(fid),
    ) { tickets, vehicleEvents, homeItems, petEvents, trips ->
        val now = System.currentTimeMillis()

        LifeSlice(
            // I biglietti senza data non scadono: non hanno niente da anticipare in Home.
            tickets = tickets
                .mapNotNull { ticket ->
                    val date = ticket.eventDateEpochMillis ?: return@mapNotNull null
                    if ((ticket.eventEndDateEpochMillis ?: date) < now) return@mapNotNull null
                    DashboardDeadline(date, ticket.title)
                }
                .sortedBy { it.epochMillis },
            vehicles = vehicleEvents
                .filter { it.date >= now }
                .map { DashboardDeadline(it.date, it.title) }
                .sortedBy { it.epochMillis },
            homeDeadlines = homeItems
                .flatMap { item ->
                    buildList {
                        item.warrantyExpiryDate?.takeIf { it >= now }
                            ?.let { add(DashboardDeadline(it, item.name)) }
                        item.nextServiceDate?.takeIf { it >= now }
                            ?.let { add(DashboardDeadline(it, item.name)) }
                    }
                }
                .sortedBy { it.epochMillis },
            pets = petEvents
                .mapNotNull { event ->
                    val next = event.nextDueDate ?: return@mapNotNull null
                    if (next < now) return@mapNotNull null
                    DashboardDeadline(next, event.title)
                }
                .sortedBy { it.epochMillis },
            trips = trips
                .filter { it.endDateEpoch >= now }
                .map { DashboardDeadline(it.startDateEpoch, it.name) }
                .sortedBy { it.epochMillis },
        )
    }

    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun visible(scope: String, memberIdsJson: String, createdBy: String?, uid: String?): Boolean =
        KBVisibilityScope.isVisible(scope, parseMemberIds(memberIdsJson), createdBy, uid)

    private fun parseMemberIds(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun startOfMonthMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val NINETY_DAYS_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}
