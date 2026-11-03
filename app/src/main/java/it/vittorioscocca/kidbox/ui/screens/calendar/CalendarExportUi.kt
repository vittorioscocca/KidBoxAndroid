package it.vittorioscocca.kidbox.ui.screens.calendar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.functions.FirebaseFunctions
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLog
import java.net.URLEncoder
import java.util.TimeZone
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Il calendario di KidBox dentro Google Calendar e Outlook: un link iCal
// personale (functions/calendarExport.js) a cui quei calendari si iscrivono.
// Personale e non di famiglia perché rispetta la visibilità degli eventi. Sola
// lettura, e Google lo rilegge quando vuole lui (12-24 ore): il testo lo dice,
// se no sembra rotto. Gemello di `CalendarExportView.swift` su iOS.

private object CalendarExportClient {
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    /**
     * «get» non crea niente; «enable» crea se manca; «rotate» sostituisce il
     * link (il vecchio muore subito); «disable» lo spegne. Null su errore.
     */
    suspend fun run(familyId: String, action: String): Result<String?> = runCatching {
        val res = functions.getHttpsCallable("calendarExportLink").call(
            hashMapOf("familyId" to familyId, "action" to action, "tz" to TimeZone.getDefault().id),
        ).await()
        (res.getData() as? Map<*, *>)?.get("url") as? String
    }.onFailure { KBLog.app.error("calendarExportLink $action fallita", "CalendarExport", it) }
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

private fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
internal fun CalendarExportSection(familyId: String) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    fun run(action: String) {
        scope.launch {
            if (action == "get") loading = true else busy = true
            CalendarExportClient.run(familyId, action)
                .onSuccess { url = it; failed = false }
                .onFailure { failed = true }
            loading = false
            busy = false
            copied = false
        }
    }

    LaunchedEffect(familyId) { if (familyId.isNotBlank()) run("get") }

    Text(
        stringResource(R.string.calendar_export_section),
        color = kb.subtitle,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(stringResource(R.string.calendar_export_intro), color = kb.subtitle, fontSize = 12.sp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            val link = url
            when {
                loading -> Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                link == null -> ExportButton(Icons.Filled.Link, stringResource(R.string.calendar_export_enable), !busy) { run("enable") }
                else -> {
                    val webcal = link.replaceFirst("https://", "webcal://")
                    ExportButton(Icons.Filled.OpenInNew, stringResource(R.string.calendar_export_google), !busy) {
                        openLink(context, "https://calendar.google.com/calendar/r?cid=${enc(webcal)}")
                    }
                    ExportButton(Icons.Filled.OpenInNew, stringResource(R.string.calendar_export_outlook), !busy) {
                        openLink(context, "https://outlook.live.com/calendar/0/addfromweb?url=${enc(link)}&name=KidBox")
                    }
                    ExportButton(Icons.Filled.OpenInNew, stringResource(R.string.calendar_export_outlook_work), !busy) {
                        openLink(context, "https://outlook.office.com/calendar/0/addfromweb?url=${enc(link)}&name=KidBox")
                    }
                    ExportButton(
                        Icons.Filled.ContentCopy,
                        stringResource(if (copied) R.string.calendar_export_copied else R.string.calendar_export_copy),
                        !busy,
                    ) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("KidBox", link))
                        copied = true
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { confirm = "rotate" }, enabled = !busy) {
                            Text(stringResource(R.string.calendar_export_rotate))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { confirm = "disable" }, enabled = !busy) {
                            Text(stringResource(R.string.calendar_export_disable), color = Color(0xFFD32F2F))
                        }
                    }
                }
            }
        }
    }
    if (failed) {
        Text(stringResource(R.string.calendar_export_error), color = Color(0xFFD32F2F), fontSize = 12.sp)
    }
    if (url != null) {
        Text(stringResource(R.string.calendar_export_google_hint), color = kb.subtitle, fontSize = 12.sp)
    }
    Text(stringResource(R.string.calendar_export_refresh), color = kb.subtitle, fontSize = 12.sp)
    Text(stringResource(R.string.calendar_export_privacy), color = kb.subtitle, fontSize = 12.sp)

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(stringResource(if (action == "rotate") R.string.calendar_export_rotate else R.string.calendar_export_disable))
            },
            text = {
                Text(
                    stringResource(
                        if (action == "rotate") R.string.calendar_export_rotate_confirm else R.string.calendar_export_disable_confirm,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    run(action)
                }) {
                    Text(
                        stringResource(if (action == "rotate") R.string.calendar_export_rotate else R.string.calendar_export_disable),
                        color = Color(0xFFD32F2F),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.calendar_feeds_cancel)) }
            },
        )
    }
}

@Composable
private fun ExportButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
