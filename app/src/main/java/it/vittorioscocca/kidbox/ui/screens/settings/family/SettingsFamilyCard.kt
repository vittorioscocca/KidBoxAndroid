package it.vittorioscocca.kidbox.ui.screens.settings.family

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLog
import java.io.File

private const val TAG = "SettingsFamilyCard"

/** Il rosso delle azioni che non si tornano indietro, lo stesso della schermata Famiglia. */
private val LeaveRed = Color(0xFFE53E3E)

/**
 * La card Famiglia in cima a Impostazioni: il nome della famiglia, chi sei tu
 * dentro, e la via d'uscita.
 *
 * Sta in Impostazioni e non solo dentro la schermata Famiglia perché sono le due
 * cose che si vengono a cercare qui — di che famiglia faccio parte, e come ne
 * esco — e non meritano un passaggio in più. Il resto (figli, membri, inviti)
 * resta nella schermata, a un tap dal titolo.
 */
@Composable
internal fun SettingsFamilyCard(
    onOpenFamilySettings: () -> Unit,
    onLeaveDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FamilySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateAway by viewModel.navigateAwayAfterLeave.collectAsStateWithLifecycle()

    LaunchedEffect(navigateAway) {
        if (navigateAway) {
            KBLog.ui.info("navigateAwayAfterLeave triggered -> calling onLeaveDone", TAG)
            viewModel.resetNavigateAway()
            onLeaveDone()
        }
    }

    // Come nella schermata Famiglia: l'osservazione riparte a ogni resume, così
    // un cambio fatto altrove (o su un altro device) si vede al ritorno.
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

    val kb = MaterialTheme.kidBoxColors
    val family = state.family

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_family_title),
                        fontSize = 13.sp,
                        color = kb.subtitle,
                    )
                    Text(
                        // Senza famiglia la card resta: è l'unico posto da cui
                        // crearne una o entrare con un codice.
                        text = family?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.settings_family_none),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = kb.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_row_family),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF6B00),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenFamilySettings)
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                FamilyPhoto(
                    localPath = family?.heroPhotoLocalPath,
                    url = family?.heroPhotoURL,
                )
            }

            if (family != null) {
                Spacer(modifier = Modifier.height(14.dp))
                CurrentMemberRow(
                    onLeave = {
                        KBLog.ui.debug("tap leave from settings card", TAG)
                        viewModel.onLeaveButtonTapped()
                    },
                    state = state,
                )
            }
        }
    }

    FamilyLeaveDialogs(viewModel, TAG)
}

/** Chi sei tu in questa famiglia, e come ne esci. */
@Composable
private fun CurrentMemberRow(
    state: FamilySettingsUiState,
    onLeave: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    // L'uid della sessione, non solo quello fotografato dal ViewModel: se
    // l'osservazione parte prima che Firebase abbia ripristinato la sessione,
    // `currentUid` resta vuoto e nessuno risulta né membro corrente né owner.
    val authUser = FirebaseAuth.getInstance().currentUser
    val uid = state.currentUid.takeIf { it.isNotBlank() } ?: authUser?.uid.orEmpty()
    val me = state.members.firstOrNull { it.userId == uid }
    val isOwner = state.isOwner || (
        uid.isNotBlank() &&
            (state.family?.createdBy == uid || me?.role.equals("owner", ignoreCase = true) == true)
        )
    // Il nome viene dalla riga membro, ma se quella non è ancora arrivata resta
    // quello dell'account: qui deve comparire chi sei, non un'etichetta di ruolo.
    val name = sequenceOf(me?.displayName, authUser?.displayName, me?.email, authUser?.email)
        .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
        .firstOrNull() ?: stringResource(R.string.settings_family_member)
    val email = (me?.email ?: authUser?.email)?.trim().orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(kb.background)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(kb.divider),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = kb.title,
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = kb.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // L'email si ripete solo quando il nome non è già l'email.
            if (email.isNotEmpty() && email != name) {
                Text(
                    text = email,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = kb.subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Il ruolo si vede sempre, owner o membro che sia: dice cosa puoi
            // fare in questa famiglia, e sta sopra il tasto che la lascia.
            Text(
                text = stringResource(
                    if (isOwner) R.string.settings_family_owner else R.string.settings_family_member,
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isOwner) Color(0xFFFF6B00) else kb.subtitle,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isOwner) Color(0x1AFF6B00) else kb.divider)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            // Su due righe di proposito: in una sola si mangiava la larghezza
            // che serve a nome ed email per non finire in puntini.
            Text(
                text = stringResource(R.string.settings_family_card_leave),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LeaveRed,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .widthIn(max = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLeave)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * La foto della famiglia scelta in Home. Quando non ce n'è una, la prima
 * immagine del carosello: un riquadro vuoto qui direbbe che manca qualcosa,
 * mentre la foto è facoltativa.
 */
@Composable
private fun FamilyPhoto(localPath: String?, url: String?) {
    val modifier = Modifier
        .size(84.dp)
        .clip(RoundedCornerShape(18.dp))
    val local = localPath?.let { File(it) }?.takeIf { it.exists() }
    val model = local ?: url

    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(R.drawable.home_promo_invite),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
