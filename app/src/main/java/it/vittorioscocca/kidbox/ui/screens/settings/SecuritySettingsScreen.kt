package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyVault
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.components.KeyVaultPrompt
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat

/**
 * Impostazioni › Sicurezza: la cassaforte della chiave di famiglia (piano v2,
 * Fase 2). Gemello di `SecuritySettingsView.swift`.
 */
@Composable
fun SecuritySettingsScreen(familyIds: List<String>, onBack: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var slots by remember { mutableStateOf<List<FamilyKeyVault.Slot>>(emptyList()) }
    var hasRk by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) } // "open" | "regenerate" | "passkey"

    LaunchedEffect(uid, reloadTick) {
        if (uid.isBlank()) return@LaunchedEffect
        hasRk = FamilyKeyVault.loadRk(uid) != null
        try {
            slots = withContext(Dispatchers.IO) { FamilyKeyVault.listSlots(uid) }
            failed = false
        } catch (e: Exception) {
            failed = true
        }
        loaded = true
    }
    val recovery = slots.firstOrNull { it.type == "recoveryCode" }
    val passkeys = slots.filter { it.type == "passkey" }

    Scaffold(
        containerColor = MaterialTheme.kidBoxColors.background,
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                KBBackButton(onClick = onBack)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.security_title),
                    fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.kidBoxColors.title,
                )
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                when {
                    !loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.security_checking), color = MaterialTheme.kidBoxColors.subtitle)
                    }
                    failed -> Status(Icons.Filled.Warning, R.string.security_status_failed, warn = true)
                    slots.isEmpty() -> Status(Icons.Filled.LockOpen, R.string.security_status_none, warn = true)
                    hasRk -> Status(Icons.Filled.Lock, R.string.security_status_ok, warn = false)
                    else -> Status(Icons.Filled.Lock, R.string.security_status_locked, warn = true)
                }
            }
            Text(
                stringResource(R.string.security_explain),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kidBoxColors.subtitle,
            )

            if (loaded && slots.isNotEmpty()) {
                Text(
                    stringResource(R.string.security_methods),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.kidBoxColors.title,
                )
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recovery?.let { r ->
                            Method(Icons.Filled.Key, stringResource(R.string.kv_code_label),
                                (r.updatedAt ?: r.createdAt)?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(it) })
                        }
                        passkeys.forEach { p ->
                            Method(Icons.Filled.Fingerprint, p.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.security_passkey), null)
                        }
                    }
                }
            }

            if (loaded && !failed) {
                if (slots.isEmpty() || !hasRk) {
                    Button(onClick = { dialog = "open" }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (slots.isEmpty()) R.string.kv_create_cta else R.string.security_open_here))
                    }
                }
                if (hasRk && slots.isNotEmpty() && it.vittorioscocca.kidbox.data.crypto.VaultPasskey.maybeSupported()) {
                    OutlinedButton(onClick = { dialog = "passkey" }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.security_add_passkey))
                    }
                }
                if (hasRk && recovery != null) {
                    OutlinedButton(onClick = { dialog = "regenerate" }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.security_regenerate))
                    }
                    Text(
                        stringResource(R.string.security_regenerate_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kidBoxColors.subtitle,
                    )
                }
            }
        }
    }

    dialog?.let { mode ->
        KeyVaultPrompt(
            uid = uid,
            email = FirebaseAuth.getInstance().currentUser?.email,
            familyIds = familyIds,
            allowLater = true,
            onDone = { dialog = null; reloadTick++ },
            onLater = { dialog = null },
            regenerate = mode == "regenerate",
            addPasskey = mode == "passkey",
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) = Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.kidBoxColors.card,
    contentColor = MaterialTheme.kidBoxColors.title,
    modifier = Modifier.fillMaxWidth(),
) { Column(Modifier.padding(16.dp)) { content() } }

@Composable
private fun Status(icon: ImageVector, res: Int, warn: Boolean) = Row(verticalAlignment = Alignment.CenterVertically) {
    val tint = if (warn) Color(0xFFE08A00) else Color(0xFF2E9D4F)
    Icon(icon, contentDescription = null, tint = tint)
    Spacer(Modifier.size(12.dp))
    Text(stringResource(res), color = tint, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Method(icon: ImageVector, title: String, trailing: String?) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle)
    Spacer(Modifier.size(12.dp))
    Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.kidBoxColors.title)
    trailing?.let { Text(it, color = MaterialTheme.kidBoxColors.subtitle, style = MaterialTheme.typography.bodySmall) }
}
