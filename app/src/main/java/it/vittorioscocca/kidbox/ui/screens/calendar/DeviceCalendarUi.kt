package it.vittorioscocca.kidbox.ui.screens.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import it.vittorioscocca.kidbox.data.calendarfeed.CalendarFeed
import it.vittorioscocca.kidbox.data.calendarfeed.CalendarFeedRepository
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.devicecalendar.DeviceCalendarEvent
import it.vittorioscocca.kidbox.data.devicecalendar.DeviceCalendarState
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * I calendari del telefono dentro il calendario KidBox, in sola lettura.
 * Il motore è `DeviceCalendarRepository`; gemello di `DeviceCalendarViews.swift`.
 *
 * Per disegnarli la griglia usa le stesse funzioni degli eventi KidBox: ogni
 * evento del telefono diventa una **copia di sola visualizzazione** con l'id
 * che inizia per `device:` e il colore del suo calendario nel campo categoria.
 * Stesso principio delle ripetizioni: si disegna, non si salva mai. Al tocco
 * si riconosce dall'id e apre la scheda di sola lettura.
 */

internal const val DEVICE_EVENT_ID_PREFIX = "device:"
private const val DEVICE_CATEGORY_PREFIX = "device:"

internal fun KBCalendarEventEntity.isDeviceEvent(): Boolean = id.startsWith(DEVICE_EVENT_ID_PREFIX)

/** Il colore del calendario del telefono, o `null` se l'evento è di KidBox. */
internal fun deviceCategoryColor(categoryRaw: String): Color? =
    categoryRaw.takeIf { it.startsWith(DEVICE_CATEGORY_PREFIX) }
        ?.removePrefix(DEVICE_CATEGORY_PREFIX)
        ?.toLongOrNull()
        ?.let { Color(it.toInt()).copy(alpha = 1f) }

/**
 * La copia per la griglia. La fine si porta a un millisecondo prima: il
 * sistema la dà esclusa (mezzanotte del giorno dopo) e le mappe per giorno
 * la considerano inclusa, quindi l'evento comparirebbe anche il giorno dopo.
 */
internal fun DeviceCalendarEvent.toGridEntity(): KBCalendarEventEntity = KBCalendarEventEntity(
    id = DEVICE_EVENT_ID_PREFIX + id,
    familyId = "",
    childId = null,
    title = title,
    notes = notes,
    location = location,
    startDateEpochMillis = startMillis,
    endDateEpochMillis = if (endMillis > startMillis) endMillis - 1 else endMillis,
    isAllDay = isAllDay,
    categoryRaw = DEVICE_CATEGORY_PREFIX + (color.toLong() and 0xFFFFFFFFL),
    recurrenceRaw = "none",
    reminderMinutes = null,
    priorityRaw = 0,
    linkedHealthItemId = null,
    linkedHealthItemType = null,
    visibilityScope = KBVisibilityScope.ONLY_CREATOR,
    visibilityMemberIdsJson = "[]",
    isDeleted = false,
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
    updatedBy = "",
    createdBy = "",
    syncStateRaw = KBSyncState.SYNCED.rawValue,
    lastSyncError = null,
)

/** I campi di un evento del telefono che passano a «Nuovo evento» con «Copia in KidBox». */
data class CalendarEventPrefill(
    val title: String,
    val notes: String?,
    val location: String?,
    val startMillis: Long,
    /** Fine inclusa, pronta per il modulo. */
    val endMillis: Long,
    val isAllDay: Boolean,
)

internal fun DeviceCalendarEvent.toPrefill(untitled: String): CalendarEventPrefill = CalendarEventPrefill(
    title = title.ifBlank { untitled },
    notes = notes,
    location = location,
    startMillis = startMillis,
    // Un tutto-il-giorno finisce alla mezzanotte dopo: KidBox lo vuole sul giorno stesso.
    endMillis = if (isAllDay && endMillis > startMillis) endMillis - 1 else endMillis,
    isAllDay = isAllDay,
)

// ── Invito ─────────────────────────────────────────────────────────────────

/** Compare finché l'utente non ha mai risposto: è la risposta a «il mio calendario è già su Google». */
@Composable
internal fun DeviceCalendarPromptCard(onConnect: () -> Unit, onDismiss: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.EditCalendar,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.padding(top = 2.dp).size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.device_calendar_prompt_title),
                color = kb.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                stringResource(R.string.device_calendar_prompt_body),
                color = kb.subtitle,
                fontSize = 12.sp,
            )
            Button(onClick = onConnect, modifier = Modifier.padding(top = 4.dp).height(34.dp)) {
                Text(stringResource(R.string.device_calendar_prompt_action), fontSize = 12.sp)
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.device_calendar_close_cd),
                tint = kb.subtitle,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Scelta dei calendari ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceCalendarSettingsSheet(
    state: DeviceCalendarState,
    onRequestPermission: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetCalendarVisible: (Long, Boolean) -> Unit,
    feeds: List<CalendarFeed>,
    feedBusy: Boolean,
    feedError: String?,
    onClearFeedError: () -> Unit,
    onSubscribeFeed: (name: String, url: String, colorHex: String, onDone: (Boolean) -> Unit) -> Unit,
    onDeleteFeed: (CalendarFeed) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    var showAddFeed by remember { mutableStateOf(false) }
    var feedToRemove by remember { mutableStateOf<CalendarFeed?>(null) }
    var showOutlookSteps by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = kb.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.device_calendar_settings_title),
                    modifier = Modifier.weight(1f),
                    color = kb.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.device_calendar_done))
                }
            }

            Text(
                stringResource(R.string.device_calendar_phone_section),
                color = kb.subtitle,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = kb.card),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        state.hasPermission -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.device_calendar_show_toggle),
                                modifier = Modifier.weight(1f),
                                color = kb.title,
                            )
                            Switch(checked = state.isEnabled, onCheckedChange = onSetEnabled)
                        }

                        state.wasAsked -> {
                            // Negato: se Android non ripropone più la domanda,
                            // l'unica strada sono le impostazioni dell'app.
                            Text(
                                stringResource(R.string.device_calendar_denied_title),
                                color = kb.title,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.device_calendar_denied_body),
                                color = kb.subtitle,
                                fontSize = 13.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onRequestPermission) {
                                    Text(stringResource(R.string.device_calendar_allow))
                                }
                                TextButton(onClick = { openAppSettings(context) }) {
                                    Text(stringResource(R.string.device_calendar_open_settings))
                                }
                            }
                        }

                        else -> Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.device_calendar_allow))
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.device_calendar_settings_footer),
                color = kb.subtitle,
                fontSize = 12.sp,
            )

            if (state.isShowing) {
                val otherLabel = stringResource(R.string.device_calendar_other_calendars)
                state.calendars.groupBy { it.accountName.ifBlank { otherLabel } }.forEach { (account, calendars) ->
                    Text(
                        account,
                        color = kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            calendars.forEach { cal ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(cal.color).copy(alpha = 1f), CircleShape),
                                    )
                                    Text(
                                        cal.title,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                                        color = kb.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Switch(
                                        checked = cal.id !in state.hiddenCalendarIds,
                                        onCheckedChange = { onSetCalendarVisible(cal.id, it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ConnectAccountSection(onOutlook = { showOutlookSteps = true })

            CalendarFeedsSection(
                feeds = feeds,
                busy = feedBusy,
                onAdd = {
                    onClearFeedError()
                    showAddFeed = true
                },
                onRemove = { feedToRemove = it },
            )
        }
    }

    if (showAddFeed) {
        AddCalendarFeedDialog(
            busy = feedBusy,
            error = feedError,
            onSubscribe = { name, url, color ->
                onSubscribeFeed(name, url, color) { ok -> if (ok) showAddFeed = false }
            },
            onDismiss = {
                showAddFeed = false
                onClearFeedError()
            },
        )
    }

    if (showOutlookSteps) {
        val outlookInstalled = remember { isPackageInstalled(context, OUTLOOK_PACKAGE) }
        AlertDialog(
            onDismissRequest = { showOutlookSteps = false },
            title = { Text(stringResource(R.string.calendar_connect_outlook_title)) },
            text = { Text(stringResource(R.string.calendar_connect_outlook_steps)) },
            confirmButton = {
                TextButton(onClick = {
                    showOutlookSteps = false
                    if (outlookInstalled) openOutlook(context) else openOutlookInStore(context)
                }) {
                    Text(
                        stringResource(
                            if (outlookInstalled) R.string.calendar_connect_open_outlook else R.string.calendar_connect_install_outlook,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showOutlookSteps = false }) {
                    Text(stringResource(R.string.calendar_feeds_cancel))
                }
            },
        )
    }

    feedToRemove?.let { feed ->
        AlertDialog(
            onDismissRequest = { feedToRemove = null },
            title = { Text(stringResource(R.string.calendar_feeds_remove_title, feed.name)) },
            text = { Text(stringResource(R.string.calendar_feeds_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFeed(feed)
                    feedToRemove = null
                }) { Text(stringResource(R.string.calendar_feeds_remove_confirm), color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { feedToRemove = null }) {
                    Text(stringResource(R.string.calendar_feeds_cancel))
                }
            },
        )
    }
}

// ── Calendari iscritti da link (feed ICS) ──────────────────────────────────

@Composable
private fun CalendarFeedsSection(
    feeds: List<CalendarFeed>,
    busy: Boolean,
    onAdd: () -> Unit,
    onRemove: (CalendarFeed) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        stringResource(R.string.calendar_feeds_section),
        color = kb.subtitle,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            feeds.forEach { feed ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                runCatching { Color(android.graphics.Color.parseColor(feed.colorHex)) }
                                    .getOrDefault(Color.Gray),
                                CircleShape,
                            ),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(feed.name, color = kb.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (feed.lastError != null) {
                                stringResource(R.string.calendar_feeds_status_error)
                            } else {
                                stringResource(R.string.calendar_feeds_status, feed.eventCount)
                            },
                            color = if (feed.lastError != null) Color(0xFFD32F2F) else kb.subtitle,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = { onRemove(feed) }, enabled = !busy) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.calendar_feeds_remove_confirm),
                            tint = Color(0xFFD32F2F),
                        )
                    }
                }
            }
            TextButton(onClick = onAdd, enabled = !busy) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.calendar_feeds_add))
            }
        }
    }
    Text(stringResource(R.string.calendar_feeds_footer), color = kb.subtitle, fontSize = 12.sp)
}

@Composable
private fun AddCalendarFeedDialog(
    busy: Boolean,
    error: String?,
    onSubscribe: (name: String, url: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(CalendarFeedRepository.PALETTE.first()) }
    var showHelp by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.calendar_feeds_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.calendar_feeds_url_label)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.calendar_feeds_name_label)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalendarFeedRepository.PALETTE.forEach { hex ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(if (hex == color) 30.dp else 24.dp)
                                .background(c, CircleShape)
                                .clickable(enabled = !busy) { color = hex },
                        )
                    }
                }
                TextButton(onClick = { showHelp = !showHelp }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.calendar_feeds_help_title), fontSize = 13.sp)
                }
                if (showHelp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            R.string.calendar_feeds_help_google,
                            R.string.calendar_feeds_help_outlook,
                            R.string.calendar_feeds_help_other,
                            R.string.calendar_feeds_help_privacy,
                        ).forEach { Text(stringResource(it), fontSize = 12.sp) }
                    }
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.calendar_feeds_loading), fontSize = 13.sp)
                    }
                }
                error?.let {
                    Text(stringResource(feedErrorRes(it)), color = Color(0xFFD32F2F), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubscribe(name, url, color) }, enabled = !busy && url.isNotBlank()) {
                Text(stringResource(R.string.calendar_feeds_subscribe))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.calendar_feeds_cancel))
            }
        },
    )
}

// ── Collegare Google o Outlook al telefono ─────────────────────────────────

/**
 * La strada senza OAuth: un account aggiunto al telefono porta con sé i suoi
 * calendari, e KidBox li vede come tutti gli altri (il ContentObserver li fa
 * comparire al ritorno). Google si aggiunge dalle impostazioni di sistema;
 * Outlook no: i calendari li sincronizza l'app Outlook, se glielo si chiede.
 */
@Composable
private fun ConnectAccountSection(onOutlook: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    Text(
        stringResource(R.string.calendar_connect_section),
        color = kb.subtitle,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(onClick = { addGoogleAccount(context) }) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.calendar_connect_google))
            }
            TextButton(onClick = onOutlook) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.calendar_connect_outlook))
            }
        }
    }
    Text(stringResource(R.string.calendar_connect_footer), color = kb.subtitle, fontSize = 12.sp)
}

private const val OUTLOOK_PACKAGE = "com.microsoft.office.outlook"

private fun addGoogleAccount(context: Context) {
    val intent = Intent(Settings.ACTION_ADD_ACCOUNT)
        .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Alcune ROM non accettano il filtro sul tipo: si ripiega sull'elenco completo.
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun isPackageInstalled(context: Context, pkg: String): Boolean =
    runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

private fun openOutlook(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(OUTLOOK_PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(launch ?: return) }.onFailure { openOutlookInStore(context) }
}

private fun openOutlookInStore(context: Context) {
    val market = Uri.parse("market://details?id=$OUTLOOK_PACKAGE")
    // Prima il Play Store: su Xiaomi `market://` apre la scelta con GetApps.
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, market).setPackage("com.android.vending")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.recoverCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, market).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$OUTLOOK_PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** I codici di `calendarFeeds.js` → testo per chi ha incollato il link. */
internal fun feedErrorRes(code: String): Int = when (code) {
    "invalid_url" -> R.string.calendar_feeds_error_invalid_url
    "private_address" -> R.string.calendar_feeds_error_private
    "http_error" -> R.string.calendar_feeds_error_http
    "too_large" -> R.string.calendar_feeds_error_too_large
    "not_ics" -> R.string.calendar_feeds_error_not_ics
    "too_many_feeds" -> R.string.calendar_feeds_error_too_many
    else -> R.string.calendar_feeds_error_unreachable
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ── Riga dell'elenco del giorno ────────────────────────────────────────────

@Composable
internal fun DeviceCalendarEventCard(event: DeviceCalendarEvent, onOpen: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val zone = ZoneId.systemDefault()
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val timeLabel = if (event.isAllDay) {
        stringResource(R.string.calendar_all_day)
    } else {
        "${Instant.ofEpochMilli(event.startMillis).atZone(zone).format(hm)} - " +
            Instant.ofEpochMilli(event.endMillis).atZone(zone).format(hm)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color(event.color).copy(alpha = 1f), RoundedCornerShape(2.dp)),
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    event.title.ifBlank { stringResource(R.string.device_calendar_untitled) },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = kb.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(timeLabel, color = kb.subtitle, fontSize = 12.sp, maxLines = 1)
                    Text("·", color = kb.subtitle, fontSize = 12.sp)
                    Icon(
                        if (event.feedId != null) Icons.Filled.Link else Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = kb.subtitle,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        event.calendarTitle,
                        color = kb.subtitle,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── Scheda dell'evento ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceCalendarEventSheet(
    event: DeviceCalendarEvent,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = kb.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (event.feedId != null) R.string.calendar_feeds_event_title else R.string.device_calendar_event_title,
                ),
                color = kb.subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = kb.card),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        event.title.ifBlank { stringResource(R.string.device_calendar_untitled) },
                        color = kb.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(whenLabel(event), color = kb.subtitle, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(Color(event.color).copy(alpha = 1f), CircleShape))
                        Text(event.calendarTitle, color = kb.title, modifier = Modifier.padding(start = 8.dp))
                    }
                    event.location?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(16.dp))
                            Text(it, color = kb.title, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.device_calendar_copy))
            }
            // Un calendario iscritto non ha un'app di sistema in cui aprirlo.
            if (event.feedId == null) {
                OutlinedButton(onClick = { openInSystemCalendar(context, event) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.device_calendar_open_in_calendar))
                }
            }
            Text(
                if (event.feedId != null) {
                    stringResource(R.string.calendar_feeds_event_footer, event.calendarTitle)
                } else {
                    stringResource(R.string.device_calendar_event_footer)
                },
                color = kb.subtitle,
                fontSize = 12.sp,
            )
            // Le note in fondo: un invito di Teams è lungo pagine, e prima
            // spingeva i pulsanti fuori dallo schermo.
            event.notes?.let {
                Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = kb.card),
                shape = RoundedCornerShape(14.dp),
            ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.device_calendar_notes), color = kb.subtitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(it, color = kb.title, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun whenLabel(event: DeviceCalendarEvent): String {
    val locale = KBLocale.current()
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zone)
    // Fine esclusa: per le date si guarda l'ultimo istante incluso.
    val lastIncluded = Instant.ofEpochMilli(maxOf(event.endMillis - 1, event.startMillis)).atZone(zone)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(zone)
    val day = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    val hm = DateTimeFormatter.ofPattern("HH:mm", locale)
    val sameDay = start.toLocalDate() == lastIncluded.toLocalDate()
    val label = when {
        event.isAllDay && sameDay -> start.format(day)
        event.isAllDay -> "${start.format(date)} – ${lastIncluded.format(date)}"
        sameDay -> "${start.format(day)}, ${start.format(hm)} – ${end.format(hm)}"
        else -> "${start.format(date)} ${start.format(hm)} – ${end.format(date)} ${end.format(hm)}"
    }
    return label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/**
 * Apre l'evento nell'app calendario del telefono. Senza un'app che lo
 * gestisca sarebbe un `ActivityNotFoundException`: `runCatching`.
 */
private fun openInSystemCalendar(context: Context, event: DeviceCalendarEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
