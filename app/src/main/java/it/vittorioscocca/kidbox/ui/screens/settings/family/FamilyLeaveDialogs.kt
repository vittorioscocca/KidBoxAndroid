package it.vittorioscocca.kidbox.ui.screens.settings.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLog

/**
 * I dialoghi dell'uscita dalla famiglia, e l'errore che ne può derivare.
 *
 * Vivono qui e non dentro una schermata perché l'uscita si può chiedere da due
 * posti — la card in Impostazioni e la schermata Famiglia — e i casi che deve
 * coprire (owner solo, owner con membri, trasferimento) non vanno riscritti due
 * volte: una divergenza fra i due percorsi sarebbe un modo per uscire senza
 * passare dal trasferimento.
 */
@Composable
internal fun FamilyLeaveDialogs(
    viewModel: FamilySettingsViewModel,
    tag: String,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val leaveDialogState by viewModel.leaveDialogState.collectAsStateWithLifecycle()

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
