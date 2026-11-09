package it.vittorioscocca.kidbox.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyVault
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyVaultCrypto
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "KeyVaultPrompt"

private enum class KvStep { LOADING, INTRO, CODE, CONFIRM, OPEN, DONE }

/**
 * «Proteggi la chiave della tua famiglia» (piano v2, Fase 2). Gemello di
 * `KeyVaultPrompt.jsx` (web) e `KeyVaultPromptView.swift` (iOS).
 *
 * Cassaforte assente → la crea col codice di recupero; presente → la apre col
 * codice. Dopo: copia v2 verificata per ogni famiglia. La v1 non si tocca.
 * Passkey e QR arrivano dopo.
 */
@Composable
fun KeyVaultPrompt(
    uid: String,
    email: String?,
    familyIds: List<String>,
    allowLater: Boolean,
    onDone: () -> Unit,
    onLater: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(KvStep.LOADING) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var typedCode by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var backupOff by remember { mutableStateOf(false) }
    val code = remember { FamilyKeyVaultCrypto.generateRecoveryCode() }
    val canonical = remember(code) { FamilyKeyVaultCrypto.normalizeRecoveryCode(code) }
    val positions = remember { (0 until 24).shuffled().take(4).sorted() }
    val confirmChars = remember { mutableStateListOf("", "", "", "") }

    LaunchedEffect(uid) {
        step = try {
            if (withContext(Dispatchers.IO) { FamilyKeyVault.slotCount(uid) } > 0) KvStep.OPEN else KvStep.INTRO
        } catch (e: Exception) {
            error = R.string.kv_error
            KvStep.INTRO
        }
    }

    fun confirmCreate() {
        val norm = { c: String -> c.uppercase().replace('O', '0').replace('I', '1').replace('L', '1') }
        val ok = canonical != null && positions.indices.all { canonical[positions[it]].toString() == norm(confirmChars[it]) }
        if (!ok) { error = R.string.kv_confirm_wrong; return }
        scope.launch {
            busy = true; error = null
            try {
                withContext(Dispatchers.IO) {
                    FamilyKeyVault.createVault(uid, code)
                    FamilyKeyVault.syncFamilyKeysV2(context, uid, familyIds)
                    backupOff = FamilyKeyVault.blockStoreE2E() == false
                }
                if (backupOff) FamilyKeyVault.markBackupHintShown(uid)
                step = KvStep.DONE
            } catch (e: FamilyKeyVault.VaultExistsException) {
                notice = R.string.kv_exists_now
                step = KvStep.OPEN
            } catch (e: Exception) {
                KBLog.crypto.error("creazione cassaforte fallita: ${e.message}", TAG)
                error = R.string.kv_error
            } finally { busy = false }
        }
    }

    fun openWithCode() {
        scope.launch {
            busy = true; error = null
            try {
                withContext(Dispatchers.IO) {
                    FamilyKeyVault.openWithRecoveryCode(uid, typedCode)
                    FamilyKeyVault.syncFamilyKeysV2(context, uid, familyIds)
                    backupOff = FamilyKeyVault.blockStoreE2E() == false
                }
                if (backupOff) FamilyKeyVault.markBackupHintShown(uid)
                notice = null
                step = KvStep.DONE
            } catch (e: FamilyKeyVaultCrypto.WrongSecretException) {
                error = R.string.kv_wrong_code
            } catch (e: Exception) {
                KBLog.crypto.error("apertura cassaforte fallita: ${e.message}", TAG)
                error = R.string.kv_error
            } finally { busy = false }
        }
    }

    val fileText = listOfNotNull(
        stringResource(R.string.kv_file_header), code, email?.takeIf { it.isNotBlank() },
        stringResource(R.string.kv_code_warning),
    ).joinToString("\n\n")

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    KvStep.LOADING -> CircularProgressIndicator(Modifier.padding(32.dp))

                    KvStep.INTRO -> {
                        KvIcon(done = false)
                        KvTitle(R.string.kv_protect_title)
                        KvLead(R.string.kv_protect_body)
                        KvSmall(R.string.kv_when_needed)
                        KvError(error)
                        KvPrimary(R.string.kv_create_cta) { error = null; step = KvStep.CODE }
                    }

                    KvStep.CODE -> {
                        KvTitle(R.string.kv_code_title)
                        KvLead(R.string.kv_code_lead)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                code,
                                modifier = Modifier.padding(16.dp),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                copySensitive(context, code)
                                copied = true
                                scope.launch { delay(2000); copied = false }
                            }) { Text(stringResource(if (copied) R.string.kv_copied else R.string.kv_copy)) }
                            OutlinedButton(onClick = { shareText(context, fileText) }) {
                                Text(stringResource(R.string.kv_share))
                            }
                        }
                        Text(
                            stringResource(R.string.kv_code_warning),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        KvSmall(R.string.kv_code_where)
                        KvPrimary(R.string.kv_saved) { error = null; step = KvStep.CONFIRM }
                    }

                    KvStep.CONFIRM -> {
                        KvTitle(R.string.kv_confirm_title)
                        KvLead(R.string.kv_confirm_lead)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            positions.forEachIndexed { i, p ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.kv_confirm_pos, p + 1),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    OutlinedTextField(
                                        value = confirmChars[i],
                                        onValueChange = { confirmChars[i] = it.takeLast(1).uppercase() },
                                        singleLine = true,
                                        modifier = Modifier.width(56.dp),
                                        textStyle = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false,
                                        ),
                                    )
                                }
                            }
                        }
                        KvError(error)
                        KvPrimary(
                            if (busy) R.string.kv_creating else R.string.kv_confirm_cta,
                            enabled = !busy && confirmChars.none { it.isEmpty() } && canonical != null,
                        ) { confirmCreate() }
                        TextButton(onClick = { error = null; step = KvStep.CODE }, enabled = !busy) {
                            Text(stringResource(R.string.kv_back))
                        }
                    }

                    KvStep.OPEN -> {
                        KvIcon(done = false)
                        KvTitle(R.string.kv_open_title)
                        notice?.let { KvNotice(it) }
                        KvLead(R.string.kv_open_lead)
                        OutlinedTextField(
                            value = typedCode,
                            onValueChange = { typedCode = it },
                            label = { Text(stringResource(R.string.kv_code_label)) },
                            placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX-XXXX", fontFamily = FontFamily.Monospace) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        KvError(error)
                        KvPrimary(
                            if (busy) R.string.kv_opening else R.string.kv_open_cta,
                            enabled = !busy && FamilyKeyVaultCrypto.normalizeRecoveryCode(typedCode) != null,
                        ) { openWithCode() }
                        KvSmall(R.string.kv_lost_code)
                    }

                    KvStep.DONE -> {
                        KvIcon(done = true)
                        KvTitle(R.string.kv_done_title)
                        notice?.let { KvNotice(it) }
                        KvLead(R.string.kv_done_body)
                        if (backupOff) BackupHint(context)
                        KvPrimary(R.string.kv_done_cta) { onDone() }
                    }
                }
                if (allowLater && step != KvStep.DONE && step != KvStep.LOADING) {
                    TextButton(onClick = onLater, enabled = !busy) { Text(stringResource(R.string.kv_later)) }
                }
            }
        }
    }
}

@Composable private fun KvIcon(done: Boolean) = Icon(
    if (done) Icons.Filled.CheckCircle else Icons.Filled.Lock,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 4.dp).height(44.dp).width(44.dp),
)

@Composable private fun KvTitle(res: Int) = Text(
    stringResource(res), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
)

@Composable private fun KvLead(res: Int) = Text(
    stringResource(res), style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
)

@Composable private fun KvSmall(res: Int) = Text(
    stringResource(res), style = MaterialTheme.typography.bodySmall,
    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable private fun KvError(res: Int?) {
    if (res != null) Text(
        stringResource(res), style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error,
    )
}

@Composable private fun KvNotice(res: Int) = Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier = Modifier.fillMaxWidth(),
) { Text(stringResource(res), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }

@Composable private fun KvPrimary(res: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Spacer(Modifier.height(2.dp))
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(res)) }
}

/** Riquadro «accendi il backup di Google» con il link all'impostazione. */
@Composable private fun BackupHint(context: Context) = Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier = Modifier.fillMaxWidth(),
) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.kv_backup_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.kv_backup_body), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { openGoogleBackupSettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.kv_backup_cta))
        }
    }
}

/**
 * La pagina «Backup» di Google (servizi Play). Se il telefono non la espone,
 * le Impostazioni generali.
 */
fun openGoogleBackupSettings(context: Context) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    try {
        context.startActivity(Intent("com.google.android.gms.backup.ACTION_BACKUP_SETTINGS").addFlags(flags))
    } catch (e: android.content.ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(flags)) }
    } catch (e: SecurityException) {
        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(flags)) }
    }
}

/** Negli appunti marcato sensibile: niente anteprima nella barra degli appunti (Android 13+). */
private fun copySensitive(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("KidBox", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    cm.setPrimaryClip(clip)
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Promemoria della cassaforte (piano, sezione 4 ter). Compare solo con
 * `config/keyVault` acceso per l'utente e se il telefono non ha la chiave
 * personale; prima prova a riprenderla da Block Store. «Più tardi» vale fino
 * alla prossima apertura dell'app; con `enforce` non c'è.
 */
@Composable
fun KeyVaultGate(familyIds: List<String>) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var backupHintFor by remember { mutableStateOf<String?>(null) }
    val key = familyIds.sorted().joinToString(",")

    LaunchedEffect(key) {
        if (shown != null || familyIds.isEmpty()) return@LaunchedEffect
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return@LaunchedEffect
        val flags = withContext(Dispatchers.IO) { FamilyKeyVault.flags(uid) }
        if (!flags.active) return@LaunchedEffect
        if (FamilyKeyVault.loadRk(uid) != null) {
            // RK già qui: va su Block Store appena diventa E2E; se non lo è, una
            // volta sola si invita ad accendere il backup di Google.
            val e2e = withContext(Dispatchers.IO) { FamilyKeyVault.ensureRkInBlockStore(uid) }
            if (e2e == false && !FamilyKeyVault.backupHintShown(uid)) {
                delay(3000)
                backupHintFor = uid
            }
            return@LaunchedEffect
        }
        if (withContext(Dispatchers.IO) { FamilyKeyVault.restoreFromBlockStore(uid) }) {
            withContext(Dispatchers.IO) { FamilyKeyVault.syncFamilyKeysV2(context, uid, familyIds) }
            return@LaunchedEffect
        }
        if (!flags.enforce && uid in KeyVaultLater.uids) return@LaunchedEffect
        delay(3000) // lascia finire l'avvio (onboarding, inviti, annunci)
        if (FamilyKeyVault.loadRk(uid) == null) shown = uid to flags.enforce
    }

    shown?.let { (uid, enforce) ->
        KeyVaultPrompt(
            uid = uid,
            email = FirebaseAuth.getInstance().currentUser?.email,
            familyIds = familyIds,
            allowLater = !enforce,
            onDone = { shown = null },
            onLater = { KeyVaultLater.uids.add(uid); shown = null },
        )
    }

    backupHintFor?.let { uid ->
        val close = { FamilyKeyVault.markBackupHintShown(uid); backupHintFor = null }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = close,
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.kv_backup_title), textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.kv_backup_body)) },
            confirmButton = {
                TextButton(onClick = { openGoogleBackupSettings(context); close() }) {
                    Text(stringResource(R.string.kv_backup_cta))
                }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.kv_backup_later)) } },
        )
    }
}

/** «Più tardi» per processo: ricompare alla prossima apertura dell'app. */
private object KeyVaultLater {
    val uids: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
}
