package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.screens.onboarding.InviteCodeViewModel
import it.vittorioscocca.kidbox.ui.screens.onboarding.QRCodeView
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth

// Verde/ambra dell'avviso sicurezza sull'invito. Locali come gli accenti
// delle altre schermate: la palette del tema non ha semantici per questi due.
private val InviteSafeGreen = Color(0xFF3DA668)
private val InviteCautionAmber = Color(0xFFB26A00)

@Composable
fun InviteCodeScreen(
    onBack: () -> Unit,
    viewModel: InviteCodeViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val qrPayload by viewModel.qrPayload.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val shareLink by viewModel.shareLink.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentInviteFamilyId by viewModel.currentInviteFamilyId.collectAsStateWithLifecycle()
    val currentInviteId by viewModel.currentInviteId.collectAsStateWithLifecycle()

    var showRevokeConfirmation by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Il contenuto della schermata è centrato: il tasto indietro sta
        // sovrapposto in alto a sinistra.

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.kidBoxColors.background)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.settings_invite_title),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.kidBoxColors.title,
            )
            Spacer(modifier = Modifier.size(16.dp))
            when {
                isBusy -> CircularProgressIndicator()
                qrPayload != null -> {
                    QRCodeView(payload = qrPayload.orEmpty(), modifier = Modifier.size(220.dp))
                    Spacer(modifier = Modifier.size(8.dp))

                    // Perché il QR viene prima del link: stessa chiave, ma non
                    // transita da nessun canale di terzi.
                    Text(
                        stringResource(R.string.invite_qr_safer_badge),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InviteSafeGreen,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.invite_qr_safer),
                        fontSize = 12.sp,
                        color = MaterialTheme.kidBoxColors.subtitle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(modifier = Modifier.size(16.dp))

                    // Il link resta per chi non è vicino: trasporta la stessa
                    // chiave, ma dentro un messaggio che sopravvive all'invio.
                    shareLink?.let { link ->
                        Text(
                            stringResource(R.string.invite_link_when_far),
                            fontSize = 13.sp,
                            color = MaterialTheme.kidBoxColors.subtitle,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(
                            onClick = {
                                AppAnalytics.inviteShared(context, "system_share_sheet")
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            // I client di posta usano EXTRA_SUBJECT come
                                            // oggetto: senza, l'invito parte con oggetto
                                            // vuoto. Le app di messaggistica lo ignorano.
                                            putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                context.getString(R.string.settings_invite_share_subject),
                                            )
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                context.getString(R.string.settings_invite_share_text, link),
                                            )
                                        },
                                        null,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.settings_invite_send_link))
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedButton(
                            onClick = {
                                AppAnalytics.inviteShared(context, "copy")
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("KidBox", link))
                            },
                        ) {
                            Text(stringResource(R.string.settings_invite_copy_link))
                        }
                        Text(
                            stringResource(R.string.invite_link_caution),
                            fontSize = 12.sp,
                            color = InviteCautionAmber,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
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
                            Text(stringResource(R.string.settings_invite_revoke))
                        }
                    }
                }
                errorMessage != null -> {
                    Text(errorMessage.orEmpty(), color = Color(0xFFE53E3E))
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = viewModel::generateInviteCode) { Text(stringResource(R.string.settings_invite_retry)) }
                }
                else -> {
                    Button(onClick = viewModel::generateInviteCode) { Text(stringResource(R.string.settings_invite_generate)) }
                }
            }
        }

        val confirmFamilyId = currentInviteFamilyId
        val confirmInviteId = currentInviteId
        if (showRevokeConfirmation && confirmFamilyId != null && confirmInviteId != null) {
            AlertDialog(
                onDismissRequest = { showRevokeConfirmation = false },
                title = { Text(stringResource(R.string.settings_invite_revoke_q)) },
                text = {
                    Text(
                        stringResource(R.string.settings_invite_revoke_body),
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showRevokeConfirmation = false }) {
                        Text(stringResource(R.string.settings_common_cancel))
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
                        Text(stringResource(R.string.settings_ai_revoke), color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }

        KBBackButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        )
    }
}
