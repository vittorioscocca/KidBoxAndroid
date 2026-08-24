package it.vittorioscocca.kidbox.ui.screens.settings.family

import it.vittorioscocca.kidbox.util.KBLog

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@Composable
fun FamilySettingsScreen(
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onJoin: () -> Unit,
    onEditFamily: () -> Unit,
    onLeaveDone: () -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel(),
) {
    val tag = "FamilySettingsScreen"
    BackHandler { onBack() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val leaveDialogState by viewModel.leaveDialogState.collectAsStateWithLifecycle()
    val navigateAway by viewModel.navigateAwayAfterLeave.collectAsStateWithLifecycle()
    var memberToRevoke by remember { mutableStateOf<KBFamilyMemberEntity?>(null) }
    LaunchedEffect(navigateAway) {
        if (navigateAway) {
            KBLog.ui.info("navigateAwayAfterLeave triggered -> calling onLeaveDone", tag)
            viewModel.resetNavigateAway()
            onLeaveDone()
        }
    }
    LaunchedEffect(state.error) {
        if (!state.error.isNullOrBlank()) {
            KBLog.ui.error("uiState.error=${state.error}", tag)
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.kidBoxColors.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.settings_family_title), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.kidBoxColors.title)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.settings_family_title), fontWeight = FontWeight.Bold, color = MaterialTheme.kidBoxColors.title)
            Text(stringResource(R.string.settings_family_intro), color = MaterialTheme.kidBoxColors.subtitle, fontSize = 13.sp)
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
                            Text(
                                stringResource(R.string.settings_family_none),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.kidBoxColors.title,
                            )
                        }
                        SimpleActionCard(stringResource(R.string.settings_family_create), stringResource(R.string.settings_family_configure), onClick = onEditFamily)
                        SimpleActionCard(stringResource(R.string.settings_join_title), stringResource(R.string.settings_family_use_invite), onClick = onJoin)
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
                        Text(
                            state.family?.name.orEmpty(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.kidBoxColors.title,
                        )
                        val childrenText = if (state.children.isEmpty()) {
                            stringResource(R.string.settings_family_no_children)
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
                val showMembersLoading =
                    state.isOwner && state.family != null && state.members.isEmpty()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Groups, null, tint = MaterialTheme.kidBoxColors.subtitle)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.settings_family_members), fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            if (showMembersLoading) {
                                stringResource(R.string.settings_family_loading_members)
                            } else {
                                pluralStringResource(R.plurals.settings_family_members_connected, state.members.size, state.members.size)
                            },
                            color = MaterialTheme.kidBoxColors.subtitle,
                            fontSize = 13.sp,
                        )
                    }
                    Divider(color = MaterialTheme.kidBoxColors.divider)
                    if (showMembersLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2E86FF),
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                stringResource(R.string.settings_family_loading_members),
                                fontSize = 14.sp,
                                color = MaterialTheme.kidBoxColors.subtitle,
                            )
                        }
                    }
                    state.members.forEachIndexed { idx, member ->
                        val memberLabel = sequenceOf(member.displayName, member.email)
                            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                            .firstOrNull() ?: stringResource(R.string.settings_family_member)
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
                                Text(memberLabel, color = MaterialTheme.kidBoxColors.title)
                                Text(
                                    if (member.role.equals("owner", true)) stringResource(R.string.settings_family_owner) else stringResource(R.string.settings_family_member),
                                    fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle,
                                )
                            }
                            if (state.isOwner && member.userId != state.currentUid) {
                                IconButton(onClick = { memberToRevoke = member }) {
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
                title = stringResource(R.string.settings_family_invite_other),
                subtitle = stringResource(R.string.settings_family_generate_share),
                icon = Icons.Filled.QrCode2,
                iconTint = Color(0xFF2E86FF),
                borderColor = Color(0x552E86FF),
                onClick = onInvite,
            )
            Spacer(modifier = Modifier.height(10.dp))

            SimpleActionCard(
                title = stringResource(R.string.settings_join_title),
                subtitle = stringResource(R.string.settings_family_use_code_hint),
                icon = Icons.Filled.Key,
                iconTint = MaterialTheme.kidBoxColors.subtitle,
                onClick = onJoin,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66E53E3E)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            KBLog.ui.debug("tap leave card; forwarding to viewModel.onLeaveButtonTapped()", tag)
                            viewModel.onLeaveButtonTapped()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color(0xFFE53E3E))
                    Spacer(modifier = Modifier.size(10.dp))
                    Column {
                        Text(stringResource(R.string.settings_family_leave), fontWeight = FontWeight.SemiBold, color = Color(0xFFE53E3E))
                        Text(stringResource(R.string.settings_family_leave_sub), fontSize = 13.sp, color = MaterialTheme.kidBoxColors.subtitle)
                    }
                }
            }
        }

        // ── Dialogs dentro Box ───────────────────────────────────────────────

        if (memberToRevoke != null) {
            AlertDialog(
                onDismissRequest = { memberToRevoke = null },
                title = { Text(stringResource(R.string.settings_family_remove_q)) },
                text = {
                    Text(
                        stringResource(R.string.settings_family_remove_body),
                    )
                },
                dismissButton = {
                    TextButton(onClick = { memberToRevoke = null }) { Text(stringResource(R.string.settings_common_cancel)) }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val m = memberToRevoke ?: return@TextButton
                            memberToRevoke = null
                            viewModel.removeMember(m)
                        },
                    ) {
                        Text(stringResource(R.string.settings_family_remove), color = Color(0xFFE53E3E))
                    }
                },
            )
        }

        when (leaveDialogState) {
            LeaveDialogState.Hidden -> {}
            LeaveDialogState.ConfirmLeave -> AlertDialog(
                onDismissRequest = { viewModel.dismissLeaveDialog() },
                title = { Text(stringResource(R.string.settings_family_leave_q)) },
                text = { Text(stringResource(R.string.settings_family_leave_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        KBLog.ui.info("ConfirmLeave -> Esci clicked", tag)
                        viewModel.leaveFamily()
                    }) {
                        Text(stringResource(R.string.settings_family_exit), color = Color(0xFFE53E3E))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        KBLog.ui.debug("ConfirmLeave -> Annulla clicked", tag)
                        viewModel.dismissLeaveDialog()
                    }) { Text(stringResource(R.string.settings_common_cancel)) }
                },
            )
            LeaveDialogState.OwnerAlone -> AlertDialog(
                onDismissRequest = { viewModel.dismissLeaveDialog() },
                title = { Text(stringResource(R.string.settings_family_cannot_leave)) },
                text = { Text(stringResource(R.string.settings_family_only_member)) },
                confirmButton = {
                    TextButton(onClick = {
                        KBLog.ui.info("OwnerAlone -> Elimina famiglia clicked", tag)
                        viewModel.deleteFamily()
                    }) {
                        Text(stringResource(R.string.settings_family_delete), color = Color(0xFFE53E3E))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        KBLog.ui.debug("OwnerAlone -> Annulla clicked", tag)
                        viewModel.dismissLeaveDialog()
                    }) { Text(stringResource(R.string.settings_common_cancel)) }
                },
            )
            is LeaveDialogState.OwnerWithMembers -> AlertDialog(
                onDismissRequest = { viewModel.dismissLeaveDialog() },
                title = { Text(stringResource(R.string.settings_family_you_are_creator)) },
                text = { Text(stringResource(R.string.settings_family_transfer_hint)) },
                confirmButton = {
                    TextButton(onClick = {
                        KBLog.ui.info("OwnerWithMembers -> Trasferisci ownership clicked", tag)
                        viewModel.showTransferOwnershipDialog()
                    }) {
                        Text(stringResource(R.string.settings_family_transfer))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            KBLog.ui.info("OwnerWithMembers -> Elimina famiglia clicked", tag)
                            viewModel.deleteFamily()
                        }) {
                            Text(stringResource(R.string.settings_family_delete), color = Color(0xFFE53E3E))
                        }
                        TextButton(onClick = {
                            KBLog.ui.debug("OwnerWithMembers -> Annulla clicked", tag)
                            viewModel.dismissLeaveDialog()
                        }) { Text(stringResource(R.string.settings_common_cancel)) }
                    }
                },
            )
            LeaveDialogState.TransferOwnership -> {
                val candidates = (viewModel.checkLeaveScenario() as? LeaveScenario.OwnerWithMembers)?.otherMembers ?: emptyList()
                KBLog.ui.debug("TransferOwnership candidates=${candidates.size}", tag)
                AlertDialog(
                    onDismissRequest = { viewModel.dismissLeaveDialog() },
                    title = { Text(stringResource(R.string.settings_family_select_owner)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            candidates.forEach { member ->
                                val label = sequenceOf(member.displayName, member.email)
                                    .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                                    .firstOrNull() ?: stringResource(R.string.settings_family_member)
                                Text(
                                    text = label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            KBLog.ui.info("TransferOwnership -> selected uid=${member.userId} label=$label", tag)
                                            viewModel.transferOwnershipAndLeave(member.userId)
                                        }
                                        .padding(vertical = 8.dp),
                                    color = MaterialTheme.kidBoxColors.title,
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = {
                            KBLog.ui.debug("TransferOwnership -> Annulla clicked", tag)
                            viewModel.dismissLeaveDialog()
                        }) { Text(stringResource(R.string.settings_common_cancel)) }
                    },
                )
            }
        }

        if (!state.error.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text(stringResource(R.string.settings_family_leave_error)) },
                text = { Text(state.error ?: stringResource(R.string.settings_common_unknown_error)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                },
            )
        }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = iconTint)
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.kidBoxColors.subtitle)
            }
        }
    }
}