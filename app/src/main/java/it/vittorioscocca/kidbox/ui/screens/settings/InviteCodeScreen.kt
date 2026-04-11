package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.screens.onboarding.InviteCodeViewModel
import it.vittorioscocca.kidbox.ui.screens.onboarding.QRCodeView

@Composable
fun InviteCodeScreen(
    onBack: () -> Unit,
    viewModel: InviteCodeViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val qrPayload by viewModel.qrPayload.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    val currentInviteFamilyId by viewModel.currentInviteFamilyId.collectAsStateWithLifecycle()
    val currentInviteId by viewModel.currentInviteId.collectAsStateWithLifecycle()

    var showRevokeConfirmation by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F0EB))
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Invita", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.size(16.dp))
            when {
                isBusy -> CircularProgressIndicator()
                qrPayload != null -> {
                    QRCodeView(payload = qrPayload.orEmpty(), modifier = Modifier.size(220.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Codice: ${code.orEmpty()}")
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(errorMessage.orEmpty(), color = Color(0xFFE53E3E))
                    }
                    val fid = currentInviteFamilyId
                    val iid = currentInviteId
                    if (fid != null && iid != null) {
                        Spacer(modifier = Modifier.size(16.dp))
                        OutlinedButton(
                            onClick = { showRevokeConfirmation = true },
                        ) {
                            Text("Revoca invito")
                        }
                    }
                }
                errorMessage != null -> {
                    Text(errorMessage.orEmpty(), color = Color(0xFFE53E3E))
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = viewModel::generateInviteCode) { Text("Riprova") }
                }
                else -> {
                    Button(onClick = viewModel::generateInviteCode) { Text("Genera invito") }
                }
            }
        }

        val confirmFamilyId = currentInviteFamilyId
        val confirmInviteId = currentInviteId
        if (showRevokeConfirmation && confirmFamilyId != null && confirmInviteId != null) {
            AlertDialog(
                onDismissRequest = { showRevokeConfirmation = false },
                title = { Text("Revocare invito?") },
                text = {
                    Text(
                        "Se revochi l'invito, il codice QR attuale non sarà più valido e nessuno potrà più usarlo per entrare nella famiglia.",
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showRevokeConfirmation = false }) {
                        Text("Annulla")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRevokeConfirmation = false
                            viewModel.revokeInvite(
                                familyId = confirmFamilyId,
                                inviteId = confirmInviteId,
                            )
                        },
                    ) {
                        Text("Revoca", color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    }
}
