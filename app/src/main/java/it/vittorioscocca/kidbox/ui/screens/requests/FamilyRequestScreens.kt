package it.vittorioscocca.kidbox.ui.screens.requests

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.remote.requests.FamilyRequest
import it.vittorioscocca.kidbox.data.remote.requests.FamilyRequestRemoteStore
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/** Arancio KidBox, lo stesso della pagina `kidboxapp.com/r`. */
private val RequestAccent = Color(0xFFFF6B00)

/** Apre il foglio di condivisione di sistema con il testo della richiesta. */
fun shareRequestText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

private fun timeText(millis: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("KidBox", text))
}

// ── Home ─────────────────────────────────────────────────────────────────────

/**
 * Le richieste aperte della famiglia, sopra le sezioni. Senza richieste non
 * disegna niente, nemmeno lo spazio.
 */
@Composable
fun FamilyRequestsHomeSection(
    familyId: String,
    onNavigate: (String) -> Unit,
    onMessage: (String) -> Unit,
    viewModel: FamilyRequestsHomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId) { viewModel.setFamily(familyId) }
    LaunchedEffect(Unit) { viewModel.messages.collect { onMessage(it) } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    if (state.requests.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.requests.forEach { request ->
            FamilyRequestCard(
                request = request,
                names = state.names,
                busy = request.id in busy,
                onOpen = { onNavigate(AppDestination.FamilyRequest.createRoute(request.familyId, request.id)) },
                onRespond = { yes -> viewModel.respond(request, yes) },
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun FamilyRequestCard(
    request: FamilyRequest,
    names: FamilyRequestNames,
    busy: Boolean,
    onOpen: () -> Unit,
    onRespond: (Boolean) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val isMine = request.createdBy == names.me
    val myAnswer = request.responseOf(names.me)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.PanTool, contentDescription = null, tint = RequestAccent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isMine) stringResource(R.string.requests_you_asked)
                        else stringResource(R.string.requests_asks, names.firstName(context, request.createdBy)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                    )
                    Text(request.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
                    request.dueAtMillis?.let {
                        Text(
                            FamilyRequestRemoteStore.whenText(it, request.dueHasTime),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = RequestAccent,
                        )
                    }
                    if (isMine) {
                        val no = request.responses.filter { !it.isYes }.map { it.name }.filter { it.isNotBlank() }
                        Text(
                            if (no.isEmpty()) stringResource(R.string.requests_waiting)
                            else stringResource(R.string.requests_cant_list, no.joinToString(", ")),
                            fontSize = 12.sp,
                            color = kb.subtitle,
                        )
                    }
                }
            }
            if (!isMine) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onRespond(true) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RequestAccent, contentColor = Color.White),
                    ) { Text(stringResource(R.string.requests_yes)) }
                    OutlinedButton(
                        onClick = { onRespond(false) },
                        enabled = !busy && myAnswer?.isYes != false,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RequestAccent),
                    ) {
                        Text(
                            if (myAnswer?.isYes == false) stringResource(R.string.requests_said_no)
                            else stringResource(R.string.requests_no),
                        )
                    }
                }
            }
        }
    }
}

// ── Dettaglio ────────────────────────────────────────────────────────────────

@Composable
fun FamilyRequestDetailScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: FamilyRequestDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Card(
                modifier = Modifier.size(44.dp).clickable(onClick = onBack),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = kb.card),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.requests_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = kb.title)
        }

        val request = state.request
        when {
            request != null -> RequestDetailBody(
                request = request,
                names = state.names,
                busy = busy,
                message = message,
                onRespond = viewModel::respond,
                onResend = { link ->
                    shareRequestText(
                        context,
                        FamilyRequestRemoteStore.shareText(context, request.title, request.dueAtMillis, request.dueHasTime, link),
                    )
                },
                onWithdraw = { confirmCancel = true },
                onOpenTodo = { todoId ->
                    onNavigate(
                        AppDestination.TodoList.createRoute(
                            familyId = request.familyId,
                            childId = request.childId,
                            listId = request.listId,
                            highlightTodoId = todoId,
                        ),
                    )
                },
            )
            state.loaded -> {
                Text(stringResource(R.string.requests_unavailable), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
                Text(stringResource(R.string.requests_unavailable_hint), fontSize = 14.sp, color = kb.subtitle)
            }
            else -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RequestAccent)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.requests_withdraw_q)) },
            text = { Text(stringResource(R.string.requests_withdraw_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancel()
                }) { Text(stringResource(R.string.requests_withdraw_confirm), color = Color(0xFFE53E3E)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.requests_cancel)) }
            },
        )
    }
}

@Composable
private fun RequestDetailBody(
    request: FamilyRequest,
    names: FamilyRequestNames,
    busy: Boolean,
    message: String?,
    onRespond: (Boolean) -> Unit,
    onResend: (String) -> Unit,
    onWithdraw: () -> Unit,
    onOpenTodo: (String) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val isMine = request.createdBy == names.me

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (isMine) stringResource(R.string.requests_you_asked)
                else stringResource(R.string.requests_asks, names.firstName(context, request.createdBy)),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = kb.subtitle,
            )
            Text(request.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title)
            request.dueAtMillis?.let {
                Text(FamilyRequestRemoteStore.whenText(it, request.dueHasTime), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RequestAccent)
            }
            request.notes?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 14.sp, color = kb.subtitle)
            }
        }
    }

    Text(stringResource(R.string.requests_replies), fontWeight = FontWeight.Bold, color = kb.title)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusLine(request, names)
            request.responses.filter { !it.isYes }.forEach { resp ->
                HorizontalDivider(color = kb.divider)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        when {
                            resp.isExternal -> stringResource(R.string.requests_from_link, resp.name)
                            resp.key == names.me -> stringResource(R.string.requests_you)
                            else -> resp.name
                        },
                        color = kb.title,
                    )
                    Text(stringResource(R.string.requests_cant), color = kb.subtitle)
                }
            }
        }
    }

    message?.let { Text(it, fontSize = 14.sp, color = kb.title) }

    if (request.isOpen && !isMine) {
        Button(
            onClick = { onRespond(true) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = RequestAccent, contentColor = Color.White),
        ) { Text(stringResource(R.string.requests_yes)) }
        OutlinedButton(
            onClick = { onRespond(false) },
            enabled = !busy && request.responseOf(names.me)?.isYes != false,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RequestAccent),
        ) { Text(stringResource(R.string.requests_no)) }
    }
    if (request.isOpen && isMine) {
        val link = if (request.hasExternalLink) FamilyRequestRemoteStore.savedShareLink(context, request.id) else null
        if (link != null) {
            OutlinedButton(
                onClick = { onResend(link) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RequestAccent),
            ) { Text(stringResource(R.string.requests_resend_link)) }
        }
        TextButton(
            onClick = onWithdraw,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.requests_withdraw), color = Color(0xFFE53E3E)) }
    }
    val todoId = request.todoId
    if (request.status == FamilyRequest.Status.CLAIMED && todoId != null && request.listId.isNotBlank()) {
        OutlinedButton(
            onClick = { onOpenTodo(todoId) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RequestAccent),
        ) { Text(stringResource(R.string.requests_open_todo)) }
    }
}

@Composable
private fun StatusLine(request: FamilyRequest, names: FamilyRequestNames) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val green = Color(0xFF27AE60)
    when {
        request.status == FamilyRequest.Status.CLAIMED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = green, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            val text = when {
                request.claimedByUid != null && request.claimedByUid == names.me ->
                    stringResource(R.string.requests_claimed_you)
                request.claimedByExternal ->
                    stringResource(R.string.requests_claimed_by_link, request.claimedByName.orEmpty())
                else -> stringResource(
                    R.string.requests_claimed_by,
                    request.claimedByUid?.let { names.name(context, it) } ?: request.claimedByName.orEmpty(),
                )
            }
            Text(text, color = green, fontWeight = FontWeight.SemiBold)
        }
        request.status == FamilyRequest.Status.CANCELLED ->
            Text(stringResource(R.string.requests_cancelled), color = kb.subtitle)
        request.isOpen ->
            Text(stringResource(R.string.requests_waiting), color = kb.subtitle)
        else ->
            Text(stringResource(R.string.requests_expired), color = kb.subtitle)
    }
}

// ── Editor del to-do: a chi chiedere ─────────────────────────────────────────

data class FamilyRequestAskMember(val uid: String, val displayName: String)

@Composable
fun FamilyRequestAskDialog(
    members: List<FamilyRequestAskMember>,
    initial: FamilyRequestRemoteStore.Draft?,
    availability: FamilyRequestAvailability? = null,
    onDismiss: () -> Unit,
    onConfirm: (FamilyRequestRemoteStore.Draft) -> Unit,
) {
    var work by remember {
        mutableStateOf(initial ?: FamilyRequestRemoteStore.Draft(askOutside = members.isEmpty()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.requests_ask_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Contesto, non attribuito a nessuno: gli eventi non dicono chi
                // partecipa. Vedi `FamilyRequestAvailability`.
                if (availability != null && availability.events.isNotEmpty()) {
                    Text(
                        stringResource(R.string.requests_calendar_around, timeText(availability.around)),
                        fontWeight = FontWeight.SemiBold,
                    )
                    availability.events.forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                if (item.isAllDay) stringResource(R.string.requests_all_day)
                                else "${timeText(item.start)}–${timeText(item.end)}",
                                fontSize = 12.sp,
                                modifier = Modifier.width(96.dp),
                            )
                            Text(item.title, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (members.isEmpty()) {
                    Text(stringResource(R.string.requests_only_you), fontSize = 14.sp)
                } else {
                    Text(stringResource(R.string.requests_in_family), fontWeight = FontWeight.SemiBold)
                    members.forEach { m ->
                        val checked = m.uid in work.recipients
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    work = work.copy(
                                        recipients = if (checked) work.recipients - m.uid else work.recipients + m.uid,
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(m.displayName)
                                availability?.busy?.get(m.uid)?.firstOrNull()?.let { busy ->
                                    Text(
                                        stringResource(R.string.requests_already_has, busy.title, timeText(busy.start)),
                                        fontSize = 12.sp,
                                        color = Color(0xFFD9822B),
                                    )
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.requests_in_family_hint), fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.requests_outside), modifier = Modifier.weight(1f))
                    Switch(checked = work.askOutside, onCheckedChange = { work = work.copy(askOutside = it) })
                }
                if (work.askOutside) {
                    OutlinedTextField(
                        value = work.outsideLabel,
                        onValueChange = { work = work.copy(outsideLabel = it.take(40)) },
                        placeholder = { Text(stringResource(R.string.requests_outside_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.requests_include_invite), modifier = Modifier.weight(1f))
                        Switch(checked = work.includeInvite, onCheckedChange = { work = work.copy(includeInvite = it) })
                    }
                    Text(
                        if (work.includeInvite) stringResource(R.string.requests_with_link_hint_invite)
                        else stringResource(R.string.requests_with_link_hint),
                        fontSize = 12.sp,
                    )
                } else {
                    Text(stringResource(R.string.requests_outside_hint), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(work) }, enabled = !work.isEmpty) {
                Text(stringResource(R.string.requests_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.requests_cancel)) }
        },
    )
}

/** Riga del form del to-do: «Chiedi a qualcuno…» o il riassunto di chi si chiede. */
@Composable
fun FamilyRequestAskRow(
    draft: FamilyRequestRemoteStore.Draft?,
    members: List<FamilyRequestAskMember>,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    if (draft == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PanTool, contentDescription = null, tint = RequestAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(R.string.requests_ask_someone), color = RequestAccent, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.requests_ask_hint), fontSize = 12.sp, color = kb.subtitle)
            }
        }
        return
    }
    val parts = draft.recipients.mapNotNull { uid -> members.firstOrNull { it.uid == uid }?.displayName } +
        if (draft.askOutside) {
            listOf(draft.outsideLabel.trim().ifEmpty { context.getString(R.string.requests_outside_lower) })
        } else {
            emptyList()
        }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PanTool, contentDescription = null, tint = RequestAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.requests_ask_section), fontSize = 13.sp, color = kb.subtitle)
                Text(parts.joinToString(", "), fontSize = 15.sp, color = kb.title)
            }
        }
        Text(stringResource(R.string.requests_todo_born_hint), fontSize = 12.sp, color = kb.subtitle)
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.requests_dont_ask), color = Color(0xFFE53E3E))
        }
    }
}

/** Richiesta inviata: link per chi è fuori dall'app. */
@Composable
fun FamilyRequestSentDialog(
    created: FamilyRequestRemoteStore.Created,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.PanTool, contentDescription = null, tint = RequestAccent) },
        title = { Text(stringResource(R.string.requests_sent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (created.notifiedCount > 0) stringResource(R.string.requests_sent_notified)
                    else stringResource(R.string.requests_sent_link_only),
                )
                val text = created.shareText
                val link = created.shareLink
                if (text != null && link != null) {
                    Button(
                        onClick = { shareRequestText(context, text) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RequestAccent, contentColor = Color.White),
                    ) { Text(stringResource(R.string.requests_send_link)) }
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, link)
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RequestAccent),
                    ) {
                        if (copied) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.requests_copied))
                        } else {
                            Text(stringResource(R.string.requests_copy_link))
                        }
                    }
                }
                Text(stringResource(R.string.requests_todo_born_hint), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.requests_done)) }
        },
    )
}
