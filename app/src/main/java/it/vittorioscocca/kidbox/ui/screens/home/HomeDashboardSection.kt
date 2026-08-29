package it.vittorioscocca.kidbox.ui.screens.home

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLocale
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tutte le sezioni che sanno riassumersi, in ordine di preferenza a parità di
 * urgenza. È anche l'ordine di ripiego quando non c'è ancora niente.
 *
 * Fuori di proposito: Chat e Password (contenuto cifrato, una tessera potrebbe
 * dire solo "quante", e il badge lo dice già), Family (non cambia mai) e
 * Assistente (è il bottone flottante).
 */
private val DASHBOARD_CANDIDATES = listOf(
    "calendar", "todo", "health", "shopping", "wallet", "expenses",
    "notes", "photos", "location", "documents", "vehicles", "home_items", "pets", "travel",
)

/** Quante tessere stanno in Dashboard. Tre file da due. */
private const val DASHBOARD_TILE_COUNT = 6

/** Cosa mostra una tessera adesso. */
private data class TileContent(
    /** Numero o importo. `null` sulle tessere che raccontano uno stato invece di
     *  contare qualcosa (la posizione), e su quella foto, che mostra le miniature. */
    val value: String?,
    val subtitle: String,
    /** Niente da mostrare: il valore diventa un trattino spento. La tessera resta
     *  dov'è, sparire sposterebbe tutte le altre. */
    val isEmpty: Boolean,
    /** Quanto la sezione merita un posto in Dashboard, dal più urgente al vuoto:
     *  0 = oggi o in ritardo, 1 = entro sette giorni, 2 = c'è del nuovo non visto,
     *  3 = ha contenuto ma niente di imminente, 4 = vuota. */
    val band: Int,
    /** Solo per la tessera foto; `null` su tutte le altre. */
    val thumbnails: List<String>? = null,
) {
    /** Falso solo per chi non ha un numero da mostrare: il sottotitolo si prende
     *  quella riga e va su due righe. */
    val showsValueSlot: Boolean get() = thumbnails != null || value != null || isEmpty
}

/**
 * Dashboard della Home: sei tessere che dicono cosa c'è dentro le sezioni.
 *
 * Quali sei non è deciso una volta per tutte: si scelgono fra quattordici
 * sezioni candidate. E non conta la freschezza — è la metrica sbagliata, una
 * nota toccata due minuti fa conta meno di una visita fra un'ora: conta
 * l'imminenza (vedi `band`), e a parità l'ordine di preferenza.
 *
 * Il rimescolamento avviene solo all'ingresso, alla prima comparsa dei dati e al
 * ritorno in foreground: qui non cambia solo l'ordine ma *quali* tessere ci
 * sono, e vederle ballare mentre le guardi sarebbe peggio che non cambiare mai.
 */
@Composable
internal fun HomeDashboardSection(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    onFeatureOpened: (CounterField?) -> Unit,
    onRecordFeatureUsage: (String) -> Unit,
    viewModel: HomeDashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val aiAccessBlocked by it.vittorioscocca.kidbox.ai.CurrentPlanStore.aiAccessBlocked.collectAsStateWithLifecycle()
    val chatEnabled by it.vittorioscocca.kidbox.data.local.ChatAvailability.enabled.collectAsStateWithLifecycle()
    val features = featureItems(context, state.familyId, state, aiAccessBlocked, chatEnabled)
    val byId = remember(features) { features.associateBy { it.id } }

    LaunchedEffect(state.familyId) { viewModel.bind(state.familyId) }
    val data by viewModel.data.collectAsStateWithLifecycle()

    // Viaggi vive dietro l'assistente: senza, la tessera porterebbe a un muro.
    val candidates = DASHBOARD_CANDIDATES.filter { it != "travel" || !aiAccessBlocked }
    val contents = candidates.associateWith { id -> tileContent(context, id, data, state) }

    // Le tessere e le loro posizioni si muovono all'ingresso, una volta sola
    // quando arrivano i primi dati, e al ritorno in foreground. I numeri invece
    // sono sempre vivi.
    var order by remember { mutableStateOf(candidates.take(DASHBOARD_TILE_COUNT)) }
    var didSettle by remember { mutableStateOf(false) }

    fun recompute() {
        order = candidates.withIndex().sortedWith(
            compareBy({ contents[it.value]?.band ?: 4 }, { it.index }),
        ).take(DASHBOARD_TILE_COUNT).map { it.value }
    }

    val hasData = !data.isEmpty

    LaunchedEffect(hasData) {
        if (hasData && !didSettle) {
            didSettle = true
            recompute()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recompute()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Eyebrow(stringResource(R.string.home_dashboard_eyebrow))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            order.chunked(2).forEach { rowIds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowIds.forEach { id ->
                        val feat = byId[id]
                        val content = contents[id]
                        if (feat != null && content != null) {
                            DashboardTile(
                                title = feat.title,
                                icon = feat.icon,
                                tint = feat.iconColor,
                                content = content,
                                modifier = Modifier.weight(1f),
                            ) {
                                onRecordFeatureUsage(feat.id)
                                onFeatureOpened(feat.counterField)
                                onNavigate(feat.route)
                            }
                        }
                    }
                    // Riga dispari: il buco tiene la larghezza della colonna.
                    if (rowIds.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DashboardTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    content: TileContent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = kb.card,
        border = BorderStroke(1.dp, kb.divider),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 96.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val thumbnails = content.thumbnails
            if (thumbnails != null) {
                ThumbnailStrip(thumbnails)
            } else if (content.value != null || content.isEmpty) {
                // Le tessere vuote mostrano il trattino: sparire sposterebbe tutte le altre.
                Text(
                    content.value ?: EMPTY_VALUE,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (content.isEmpty) kb.subtitle else kb.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Chi non ha un numero da mostrare (la posizione) si prende quella riga.
            Text(
                content.subtitle,
                fontSize = 12.sp,
                color = kb.subtitle,
                maxLines = if (content.showsValueSlot) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Quattro caselle sempre: le mancanti restano vuote, così la tessera foto ha
 *  la stessa altezza delle altre anche in un album appena creato. */
@Composable
private fun ThumbnailStrip(thumbnails: List<String>) {
    val kb = MaterialTheme.kidBoxColors
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(kb.divider),
            ) {
                val base64 = thumbnails.getOrNull(index)
                val bitmap = remember(base64) {
                    base64?.let {
                        runCatching {
                            val bytes = Base64.decode(it, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ── Contenuto delle tessere ──────────────────────────────────────────────────

private fun tileContent(
    context: android.content.Context,
    id: String,
    data: HomeDashboardData,
    state: HomeUiState,
): TileContent = when (id) {
    "calendar" -> {
        val next = data.upcomingEvents.firstOrNull()
        if (next == null) {
            empty(context.getString(R.string.home_dashboard_empty_calendar))
        } else {
            TileContent(
                value = data.upcomingEvents.size.toString(),
                subtitle = "${next.title}, ${whenLabel(context, next.startDateEpochMillis, next.isAllDay)}",
                isEmpty = false,
                band = if (data.hasEventToday) 0 else band(next.startDateEpochMillis, state.badgeCalendar),
            )
        }
    }

    "todo" -> {
        val next = data.openTodos.firstOrNull()
        if (next == null) {
            empty(context.getString(R.string.home_dashboard_empty_todo))
        } else {
            val due = next.dueAtEpochMillis
            TileContent(
                value = data.openTodos.size.toString(),
                subtitle = next.title,
                isEmpty = false,
                band = when {
                    data.hasTodoDueToday -> 0
                    due != null -> band(due, state.badgeTodos)
                    state.badgeTodos > 0 -> 2
                    else -> 3
                },
            )
        }
    }

    // Visite e vaccini in arrivo; se non ce n'è, le terapie in corso.
    "health" -> {
        val next = data.upcomingHealth.firstOrNull()
        val treatment = data.ongoingTreatments.firstOrNull()
        when {
            next != null -> TileContent(
                value = data.upcomingHealth.size.toString(),
                subtitle = "${next.title}, ${whenLabel(context, next.epochMillis, true)}",
                isEmpty = false,
                band = band(next.epochMillis),
            )
            treatment != null -> TileContent(
                value = data.ongoingTreatments.size.toString(),
                subtitle = treatment.title,
                isEmpty = false,
                band = 3,
            )
            else -> empty(context.getString(R.string.home_dashboard_empty_health))
        }
    }

    "shopping" -> {
        if (data.toBuy.isEmpty()) {
            empty(context.getString(R.string.home_dashboard_empty_shopping))
        } else {
            TileContent(
                value = data.toBuy.size.toString(),
                subtitle = data.toBuy.take(3).joinToString(", ") { it.name },
                isEmpty = false,
                band = if (state.badgeShopping > 0) 2 else 3,
            )
        }
    }

    "wallet" -> {
        val next = data.upcomingTickets.firstOrNull()
        if (next == null) {
            empty(context.getString(R.string.home_dashboard_empty_wallet))
        } else {
            TileContent(
                value = data.upcomingTickets.size.toString(),
                subtitle = "${next.title}, ${whenLabel(context, next.epochMillis, true)}",
                isEmpty = false,
                band = band(next.epochMillis, state.badgeWallet),
            )
        }
    }

    "expenses" -> {
        val month = monthLabel()
        if (data.monthExpensesTotal <= 0.0) {
            empty(context.getString(R.string.home_dashboard_empty_expenses, month))
        } else {
            TileContent(
                value = formatMoneyCompact(data.monthExpensesTotal),
                subtitle = month,
                isEmpty = false,
                band = if (state.badgeExpenses > 0) 2 else 3,
            )
        }
    }

    "notes" -> {
        val latest = data.notes.firstOrNull()
        if (latest == null) {
            empty(context.getString(R.string.home_dashboard_empty_notes))
        } else {
            val title = latest.title.trim()
            TileContent(
                value = data.notes.size.toString(),
                subtitle = title.ifBlank { context.getString(R.string.home_dashboard_note_untitled) },
                isEmpty = false,
                band = if (state.badgeNotes > 0) 2 else 3,
            )
        }
    }

    "photos" -> {
        val latest = data.latestPhotos.firstOrNull()
        if (latest == null) {
            empty(context.getString(R.string.home_dashboard_empty_photos))
        } else {
            TileContent(
                value = null,
                subtitle = addedLabel(context, latest.takenAtEpochMillis),
                isEmpty = false,
                band = 3,
                thumbnails = data.latestPhotos.mapNotNull { it.thumbnailBase64 },
            )
        }
    }

    // L'unica tessera senza un numero: al posto della cifra il sottotitolo si
    // prende due righe.
    "location" -> {
        if (state.isLocationSharing) {
            TileContent(
                value = null,
                subtitle = context.getString(R.string.home_dashboard_location_sharing),
                isEmpty = false,
                band = 3,
            )
        } else {
            empty(context.getString(R.string.home_dashboard_empty_location))
        }
    }

    "documents" -> {
        val latest = data.recentDocuments.firstOrNull()
        if (latest == null) {
            empty(context.getString(R.string.home_dashboard_empty_documents))
        } else {
            TileContent(
                value = data.recentDocuments.size.toString(),
                subtitle = latest.title,
                isEmpty = false,
                band = if (state.badgeDocuments > 0) 2 else 3,
            )
        }
    }

    "vehicles" -> deadlineTile(
        context,
        data.upcomingVehicleEvents,
        R.string.home_dashboard_empty_vehicles,
    )

    "home_items" -> deadlineTile(
        context,
        data.upcomingHomeDeadlines,
        R.string.home_dashboard_empty_home_items,
    )

    "pets" -> deadlineTile(
        context,
        data.upcomingPetDeadlines,
        R.string.home_dashboard_empty_pets,
    )

    else -> deadlineTile(
        context,
        data.upcomingTrips,
        R.string.home_dashboard_empty_travel,
    )
}

/** Le tessere che contano solo scadenze: cambia il testo di quando è vuota. */
private fun deadlineTile(
    context: android.content.Context,
    deadlines: List<DashboardDeadline>,
    @StringRes emptyRes: Int,
): TileContent {
    val next = deadlines.firstOrNull() ?: return empty(context.getString(emptyRes))
    return TileContent(
        value = deadlines.size.toString(),
        subtitle = "${next.title}, ${whenLabel(context, next.epochMillis, true)}",
        isEmpty = false,
        band = band(next.epochMillis),
    )
}

/** Tessera senza niente da dire: resta al suo posto con un trattino, e in banda 4
 *  finisce dietro a tutte quelle che qualcosa ce l'hanno. */
private fun empty(subtitle: String): TileContent =
    TileContent(value = null, subtitle = subtitle, isEmpty = true, band = 4)

/** Banda di una scadenza: oggi o in ritardo, entro una settimana, o più in là. */
private fun band(epochMillis: Long, badge: Int = 0): Int = when {
    epochMillis <= endOfTodayMillis() -> 0
    epochMillis <= System.currentTimeMillis() + SEVEN_DAYS_MILLIS -> 1
    badge > 0 -> 2
    else -> 3
}

private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000

/** Il trattino delle tessere senza contenuto. */
private const val EMPTY_VALUE = "–"

private fun localDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/** "oggi 08:00", "domani 16:00", "ven 05/09". */
private fun whenLabel(context: android.content.Context, epochMillis: Long, isAllDay: Boolean): String {
    val date = localDate(epochMillis)
    val today = LocalDate.now()
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", KBLocale.current()))
    return when {
        isAllDay && date == today -> context.getString(R.string.home_dashboard_today_all_day)
        isAllDay && date == today.plusDays(1) -> context.getString(R.string.home_dashboard_tomorrow_all_day)
        date == today -> context.getString(R.string.home_dashboard_today, time)
        date == today.plusDays(1) -> context.getString(R.string.home_dashboard_tomorrow, time)
        else -> date.format(DateTimeFormatter.ofPattern("EEE dd/MM", KBLocale.current()))
    }
}

private fun addedLabel(context: android.content.Context, epochMillis: Long): String {
    val date = localDate(epochMillis)
    val today = LocalDate.now()
    return when (date) {
        today -> context.getString(R.string.home_dashboard_photos_added_today)
        today.minusDays(1) -> context.getString(R.string.home_dashboard_photos_added_yesterday)
        else -> context.getString(
            R.string.home_dashboard_photos_last,
            date.format(DateTimeFormatter.ofPattern("d MMMM", KBLocale.current())),
        )
    }
}

private fun monthLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", KBLocale.current()))

/** Senza decimali: nella tessera conta l'ordine di grandezza, non i centesimi. */
private fun formatMoneyCompact(value: Double): String {
    val fmt = NumberFormat.getCurrencyInstance(KBLocale.current())
    fmt.maximumFractionDigits = 0
    fmt.minimumFractionDigits = 0
    return fmt.format(value)
}
