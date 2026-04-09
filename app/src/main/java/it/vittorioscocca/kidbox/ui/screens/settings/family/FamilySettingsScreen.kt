package it.vittorioscocca.kidbox.ui.screens.settings.family

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun FamilySettingsScreen(
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onJoin: () -> Unit,
    onEditFamily: () -> Unit,
    onLeaveDone: () -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLeaveDialog by remember { mutableStateOf(false) }

    // Lifecycle-aware: calls startObserving() every time the screen resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                viewModel.startObserving()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Family", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.kidBoxColors.title)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Famiglia", fontWeight = FontWeight.Bold, color = MaterialTheme.kidBoxColors.title)
        Text("Qui gestisci la famiglia e inviti l'altro genitore.", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (state.family == null) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF6B00))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Nessuna famiglia configurata", fontWeight = FontWeight.SemiBold)
                    }
                    SimpleActionCard("Crea una famiglia", "Configura la tua famiglia", onClick = onEditFamily)
                    SimpleActionCard("Entra con codice", "Usa un invito", onClick = onJoin)
                }
            }
            return@Column
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x552E86FF)),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, tint = Color(0xFF2E86FF))
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.family?.name.orEmpty(), fontWeight = FontWeight.Bold)
                    val childrenText = if (state.children.isEmpty()) {
                        "Nessun figlio configurato."
                    } else if (state.children.size == 1) {
                        "Figlio: ${state.children[0].name}"
                    } else {
                        "Figli: ${state.children.joinToString(", ") { it.name }}"
                    }
                    Text(childrenText, color = MaterialTheme.kidBoxColors.subtitle, fontSize = 13.sp)
                }
                IconButton(onClick = onEditFamily) {
                    Icon(Icons.Filled.Edit, null, tint = Color(0xFF2E86FF))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, null, tint = MaterialTheme.kidBoxColors.subtitle)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Membri", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("${state.members.size} membri collegati.", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 13.sp)
                }
                Divider(color = MaterialTheme.kidBoxColors.divider)
                state.members.forEachIndexed { idx, member ->
                    val memberLabel = sequenceOf(member.displayName, member.email)
                        .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                        .firstOrNull()
                        ?: "Membro"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (member.role.equals("owner", true)) Icons.Filled.Groups else Icons.Filled.Person,
                            null,
                            tint = if (member.role.equals("owner", true)) Color(0xFFFF6B00) else MaterialTheme.kidBoxColors.subtitle,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(memberLabel)
                            Text(
                                if (member.role.equals("owner", true)) "Owner" else "Membro",
                                fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle,
                            )
                        }
                        if (state.isOwner && member.userId != state.currentUid) {
                            IconButton(onClick = { viewModel.removeMember(member) }) {
                                Icon(Icons.Filled.RemoveCircle, null, tint = Color(0xFFE53E3E))
                            }
                        }
                    }
                    if (idx != state.members.lastIndex) Divider(color = MaterialTheme.kidBoxColors.divider)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        SimpleActionCard(
            title = "Invita l'altro genitore o un altro componente della famiglia",
            subtitle = "Genera un codice e condividilo.",
            icon = Icons.Filled.QrCode2,
            iconTint = Color(0xFF2E86FF),
            borderColor = Color(0x552E86FF),
            onClick = onInvite,
        )
        Spacer(modifier = Modifier.height(10.dp))

        SimpleActionCard(
            title = "Entra con codice",
            subtitle = "Usa un codice se vuoi unirti a un'altra famiglia.",
            icon = Icons.Filled.Key,
            iconTint = MaterialTheme.kidBoxColors.subtitle,
            onClick = onJoin,
        )

        if (state.canLeave) {
            Spacer(modifier = Modifier.height(10.dp))
            SimpleActionCard(
                title = "Esci dalla famiglia",
                subtitle = "Non potrai più accedere ai dati condivisi.",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                iconTint = Color(0xFFE53E3E),
                borderColor = Color(0x66E53E3E),
                onClick = { showLeaveDialog = true },
            )
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Esci dalla famiglia?") },
            text = { Text("Confermi di voler uscire dalla famiglia?") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveFamily(onLeaveDone)
                }) { Text("Esci", color = Color(0xFFE53E3E)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
fun SimpleActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Groups,
    iconTint: Color = Color(0xFF2E86FF),
    borderColor: Color? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint)
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.kidBoxColors.subtitle)
            }
        }
    }
}