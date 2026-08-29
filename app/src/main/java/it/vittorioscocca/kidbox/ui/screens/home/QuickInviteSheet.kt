package it.vittorioscocca.kidbox.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.screens.onboarding.InviteCodeViewModel
import it.vittorioscocca.kidbox.ui.screens.onboarding.QRCodeView
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics

/**
 * L'invito in versione corta, aperto dal "+" sulla foto di famiglia.
 *
 * Non è una seconda implementazione dell'invito: usa lo stesso
 * [InviteCodeViewModel] della schermata «Invita genitore», quindi la stessa
 * creazione cifrata e lo stesso link. Qui cambia solo quanto si legge — chi
 * tocca il "+" dalla Home vuole mandare un invito, non studiare come funziona.
 * Le spiegazioni lunghe e la revoca restano nella schermata piena.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickInviteSheet(
    onDismiss: () -> Unit,
    viewModel: InviteCodeViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val qrPayload by viewModel.qrPayload.collectAsStateWithLifecycle()
    val shareLink by viewModel.shareLink.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = kb.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // L'illustrazione serve prima, quando il foglio deve solo spiegarsi.
            // Creato l'invito il soggetto è il QR, e lo spazio va a lui.
            if (qrPayload == null) {
                Image(
                    painter = painterResource(R.drawable.home_promo_invite),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }

            Text(
                stringResource(R.string.quick_invite_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = kb.title,
            )
            Text(
                stringResource(R.string.quick_invite_subtitle),
                fontSize = 13.sp,
                color = kb.subtitle,
                textAlign = TextAlign.Center,
            )

            val payload = qrPayload
            val link = shareLink
            if (payload != null) {
                QRCodeView(payload = payload, modifier = Modifier.size(220.dp))

                if (link != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { shareInviteLink(context, link) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = orange),
                        ) {
                            Text(stringResource(R.string.quick_invite_send))
                        }
                        OutlinedButton(
                            onClick = { copyInviteLink(context, link) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.quick_invite_copy), color = orange)
                        }
                    }

                    // Il segreto viaggia dentro il link: chi lo riceve entra, e
                    // il link resta nella conversazione. Detto corto, ma detto.
                    Text(
                        stringResource(R.string.quick_invite_link_note),
                        fontSize = 12.sp,
                        color = kb.subtitle,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Button(
                    onClick = viewModel::generateInviteCode,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        stringResource(
                            if (isBusy) R.string.quick_invite_creating else R.string.quick_invite_create,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.quick_invite_validity),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                    textAlign = TextAlign.Center,
                )
            }

            errorMessage?.let { message ->
                Text(
                    message,
                    fontSize = 13.sp,
                    color = Color(0xFFE53E3E),
                    textAlign = TextAlign.Center,
                )
            }

            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.quick_invite_close), color = kb.subtitle)
            }
        }
    }
}

private fun shareInviteLink(context: Context, link: String) {
    AppAnalytics.inviteShared(context, "system_share_sheet")
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                // I client di posta usano EXTRA_SUBJECT come oggetto: senza,
                // l'invito parte con oggetto vuoto.
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_invite_share_subject))
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_invite_share_text, link))
            },
            null,
        ),
    )
}

private fun copyInviteLink(context: Context, link: String) {
    AppAnalytics.inviteShared(context, "copy")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("KidBox", link))
}
