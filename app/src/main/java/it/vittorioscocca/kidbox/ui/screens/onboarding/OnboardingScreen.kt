package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import it.vittorioscocca.kidbox.util.analytics.OnboardingAnalyticsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.EdgeToEdgeController
import it.vittorioscocca.kidbox.ui.screens.settings.CornerBrackets
import it.vittorioscocca.kidbox.data.remote.family.InviteReferrerPickup
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import it.vittorioscocca.kidbox.data.remote.family.PendingFamilyInvite
import it.vittorioscocca.kidbox.ui.screens.settings.JoinFamilyViewModel
import it.vittorioscocca.kidbox.ui.screens.settings.QRScannerView
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private val BackgroundBeige = Color(0xFFF2F0EB)
private val OrangeAccent = Color(0xFFFF6B00)
private val PurpleAccent = Color(0xFF7B5EA7)
private val GreenAccent = Color(0xFF3DA668)
private val SuccessGreen = Color(0xFF3DA668)
private val BlackText = Color(0xFF1A1A1A)
private val GraySubtitle = Color(0xFF666666)
private val GrayCaption = Color(0xFF888888)
private val GrayFieldBorder = Color(0xFFE0E0E0)
private val GrayDisabled = Color(0xFFBDBDBD)

private enum class FamilyPath {
    Create,
    Join,

    /**
     * Impostato in automatico quando c'è un [PendingFamilyInvite] da link:
     * sostituisce tutto il wizard con la conferma d'invito.
     */
    LinkJoin,
}

/**
 * Nomi dei passi per GA4. `setup` è nuovo; `create_family`, `name`,
 * `join_family`, `invite` e `link_invite_confirm` restano quelli di prima,
 * così il funnel per passo della routine continua a leggersi.
 */
private fun stepName(page: Int, familyPath: FamilyPath): String = when {
    familyPath == FamilyPath.LinkJoin -> "link_invite_confirm"
    page == 0 -> "setup"
    familyPath == FamilyPath.Join -> "join_family"
    else -> "invite"
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFamilyCreated: (familyId: String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    // `.Create` è il default: la scelta esplicita «Come vuoi iniziare?» non c'è
    // più. Chi ha un QR tocca «Ho un invito» e passa a Join; chi ha toccato un
    // link arriva già in LinkJoin.
    var familyPath by rememberSaveable { mutableStateOf(FamilyPath.Create) }
    // Il wizard disegna sotto le barre di sistema: il gradiente d'accento parte
    // dal bordo superiore e le pagine hanno già il loro `statusBarsPadding()`.
    // Senza questa richiesta la radice della UI (MainActivity) applica il
    // padding di `systemBars` a tutta la schermata e il wizard resta rientrato,
    // con una fascia sopra e una sotto.
    EdgeToEdgeController.RequestFullBleed()

    val pagerState = rememberPagerState(
        // 0 setup (nome, cognome, nome famiglia) · 1 invita (Create) o QR (Join).
        // Percorso LinkJoin: una pagina sola, la conferma d'invito.
        pageCount = { if (familyPath == FamilyPath.LinkJoin) 1 else 2 },
    )
    val scope = rememberCoroutineScope()

    val nameViewModel: OnboardingNameViewModel = hiltViewModel()
    val nameState by nameViewModel.uiState.collectAsStateWithLifecycle()
    var familyName by rememberSaveable { mutableStateOf("") }

    val createdFamilyId by viewModel.createdFamilyId.collectAsStateWithLifecycle()
    val isCreatingFamily by viewModel.isCreatingFamily.collectAsStateWithLifecycle()
    val createFamilyError by viewModel.createFamilyError.collectAsStateWithLifecycle()
    val isSigningOut by viewModel.isSigningOut.collectAsStateWithLifecycle()
    var showSignOutConfirm by remember { mutableStateOf(false) }

    // Invito da link, se il wizard è partito da un App Link. Controllato una
    // volta sola: `familyPath` diventa poi lo stato di navigazione, e un
    // secondo tocco sul link a wizard già avviato non deve resettare la
    // pagina in cui l'utente si trova.
    val context = LocalContext.current
    var pendingLinkInvite by remember { mutableStateOf<PendingFamilyInvite?>(null) }
    var linkInvitePreview by remember { mutableStateOf<InviteRemoteStore.InvitePreview?>(null) }
    LaunchedEffect(Unit) {
        if (familyPath != FamilyPath.Create) return@LaunchedEffect
        val invite = PendingFamilyInvite.load(context) ?: return@LaunchedEffect
        pendingLinkInvite = invite
        familyPath = FamilyPath.LinkJoin
        linkInvitePreview = InviteRemoteStore().fetchInvitePreview(invite.familyId, invite.inviteId)
    }

    // Invito noto dal referrer di Play ma senza segreto (gli appunti non
    // l'avevano): invece del wizard generico si dice all'utente che l'invito
    // esiste e come completarlo. Vedi InviteReferrerPickup.
    var partialInvite by remember { mutableStateOf(InviteReferrerPickup.partialInvite(context)) }
    var partialFamilyName by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }
    LaunchedEffect(partialInvite, retryTick) {
        val p = partialInvite ?: return@LaunchedEffect
        // Gli appunti possono arrivare dopo: l'utente installa, apre l'app, e
        // solo allora torna sul messaggio a copiare il link.
        InviteReferrerPickup.pickUp(context)
        PendingFamilyInvite.load(context)?.let { found ->
            pendingLinkInvite = found
            familyPath = FamilyPath.LinkJoin
            linkInvitePreview = InviteRemoteStore().fetchInvitePreview(found.familyId, found.inviteId)
            partialInvite = null
            return@LaunchedEffect
        }
        partialFamilyName = InviteRemoteStore().fetchInvitePreview(p.familyId, p.inviteId).familyName
    }

    val currentPage = pagerState.currentPage
    val accent = pageAccent(currentPage, familyPath)
    val iconTint = pageIconTint(currentPage, familyPath)

    // Il cognome, se c'è, propone il nome della famiglia («Famiglia Rossi»):
    // una cosa in meno da scrivere, e si può sempre cambiare.
    val familyPrefix = stringResource(R.string.onboarding_family_name_prefill)
    LaunchedEffect(nameState.lastName) {
        if (familyName.isBlank() && nameState.lastName.isNotBlank()) {
            familyName = familyPrefix.format(nameState.lastName.trim())
        }
    }

    // Una volta creata la famiglia (o completato un join) non si torna più
    // indietro: la scrittura su Firestore è già avvenuta, e riproporre la
    // pagina precedente farebbe pensare all'utente di poterla ancora annullare.
    val canGoBack = currentPage > 0 && !nameState.isSaving && !isCreatingFamily && createdFamilyId == null

    // Percorso "crea": il documento membro owner nasce senza displayName, quindi
    // appena la famiglia esiste ci si porta il nome raccolto a pagina 0 — e si
    // passa alla pagina invito.
    LaunchedEffect(createdFamilyId) {
        val id = createdFamilyId ?: return@LaunchedEffect
        nameViewModel.propagateNameToMember(id)
        AppAnalytics.onboardingStepCompleted(context, "create_family")
        AppAnalytics.onboardingStepCompleted(context, "setup")
        if (pagerState.currentPage == 0) pagerState.animateScrollToPage(1)
    }

    val onboardingStartTime = rememberSaveable { System.currentTimeMillis() }

    LaunchedEffect(currentPage, familyPath) {
        val name = stepName(currentPage, familyPath)
        AppAnalytics.onboardingStepShown(context, name, currentPage)
        OnboardingAnalyticsState.lastStepSeen = name
    }

    val onFamilyCreatedTracked: (String) -> Unit = { familyId ->
        val elapsedSeconds = ((System.currentTimeMillis() - onboardingStartTime) / 1000).toInt()
        AppAnalytics.onboardingCompleted(context, elapsedSeconds)
        OnboardingAnalyticsState.lastStepSeen = null
        OnboardingAnalyticsState.abandonReportedStep = null
        onFamilyCreated(familyId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBeige),
    ) {
        TopAccentGradient(accent = accent, pageIndex = currentPage)

        Column(
            // L'app è edge-to-edge (`setDecorFitsSystemWindows(window, false)`),
            // quindi `adjustResize` da solo non rimpicciolisce il contenuto e la
            // tastiera finisce sopra i campi. Applicato qui e non dentro la
            // singola pagina così a salire sono anche indicatori e CTA,
            // altrimenti il pulsante resterebbe comunque sotto la tastiera.
            //
            // `union` e non modificatori separati in catena: si sommerebbero, e a
            // tastiera aperta resterebbe un vuoto pari alla barra di navigazione.
            // L'unione è per lato — sopra vale la status bar, sotto il maggiore
            // fra tastiera e barra di navigazione — che è il comportamento
            // corretto in tutti gli stati.
            //
            // Il padding sta QUI e non sul Box esterno di proposito: lo sfondo e
            // il gradiente d'accento devono arrivare ai bordi dello schermo, solo
            // il contenuto va tenuto dentro le barre.
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.ime
                        .union(WindowInsets.navigationBars)
                        .union(WindowInsets.statusBars)
                        .union(WindowInsets.displayCutout),
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = false,
            ) { page ->
                when {
                    familyPath == FamilyPath.LinkJoin && pendingLinkInvite != null ->
                        LinkInviteConfirmPageContent(
                            invite = pendingLinkInvite!!,
                            preview = linkInvitePreview,
                            nameState = nameState,
                            nameViewModel = nameViewModel,
                            onJoined = { familyId -> onFamilyCreatedTracked(familyId) },
                            onFallbackToManual = {
                                PendingFamilyInvite.clear(context)
                                pendingLinkInvite = null
                                familyPath = FamilyPath.Create
                            },
                        )
                    page == 0 -> SetupPageContent(
                        pendingInviteFamilyName = partialInvite?.let { partialFamilyName ?: "" },
                        onRetryInvite = { retryTick++ },
                        state = nameState,
                        viewModel = nameViewModel,
                        familyName = familyName,
                        onFamilyNameChange = {
                            familyName = it
                            viewModel.clearCreateError()
                        },
                        isJoin = familyPath == FamilyPath.Join,
                        isBusy = nameState.isSaving || isCreatingFamily,
                        errorText = createFamilyError,
                        onTogglePath = {
                            familyPath = if (familyPath == FamilyPath.Join) FamilyPath.Create else FamilyPath.Join
                        },
                    )
                    familyPath == FamilyPath.Join -> JoinFamilyPageContent(
                        onJoined = { familyId ->
                            // Il nome sul documento membro lo scrive
                            // JoinFamilyViewModel, attraversato da ogni join.
                            AppAnalytics.onboardingStepCompleted(context, "join_family")
                            onFamilyCreatedTracked(familyId)
                        },
                    )
                    else -> InvitePartnerPageContent(
                        familyId = createdFamilyId.orEmpty(),
                        onFinish = { onFamilyCreatedTracked(createdFamilyId.orEmpty()) },
                    )
                }
            }

            val totalPages = if (familyPath == FamilyPath.LinkJoin) 1 else 2

            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PageIndicators(
                        pageCount = totalPages,
                        currentPage = currentPage,
                        accent = accent,
                    )
                }
            }

            val isSetupPage = currentPage == 0 && familyPath != FamilyPath.LinkJoin
            val isBusy = nameState.isSaving || isCreatingFamily
            // Nome e cognome sono obbligatori; il nome famiglia solo nel
            // percorso «crea» (nel join lo porta l'invito).
            val ctaEnabled = nameState.canSubmit && !isBusy &&
                (familyPath == FamilyPath.Join || familyName.isNotBlank())
            val ctaLabel = when {
                isBusy && familyPath == FamilyPath.Join -> stringResource(R.string.onboarding_saving)
                isBusy -> stringResource(R.string.onboarding_creating)
                familyPath == FamilyPath.Join -> stringResource(R.string.travel_continue)
                else -> stringResource(R.string.onboarding_create_family_cta)
            }

            if (!isSetupPage) {
                // Le altre pagine hanno i loro pulsanti dentro il contenuto.
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 28.dp)
                        .padding(bottom = 12.dp),
                )
            } else {
                MainCtaButton(
                    label = ctaLabel,
                    accent = accent,
                    iconTint = iconTint,
                    enabled = ctaEnabled,
                    onClick = {
                        // Si avanza solo a salvataggio riuscito: proseguire dopo
                        // un errore lascerebbe l'utente convinto di aver messo il
                        // nome, e la famiglia nascerebbe comunque con un membro
                        // anonimo.
                        nameViewModel.save {
                            AppAnalytics.onboardingStepCompleted(context, "name")
                            if (familyPath == FamilyPath.Join) {
                                AppAnalytics.onboardingStepCompleted(context, "setup")
                                scope.launch { pagerState.animateScrollToPage(1) }
                            } else {
                                // Il passaggio alla pagina invito lo fa il
                                // LaunchedEffect(createdFamilyId) qui sopra.
                                viewModel.createFamily(familyName, "", null)
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .padding(bottom = 12.dp),
                )
            }
        }

        // Dichiarati DOPO la Column apposta: in un Box, l'ultimo figlio è quello
        // in cima nello z-order e riceve i tap per primo.
        if (canGoBack) {
            OnboardingBackButton(
                accent = accent,
                onClick = { scope.launch { pagerState.animateScrollToPage(currentPage - 1) } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 8.dp),
            )
        }
        OnboardingSignOutButton(
            accent = accent,
            enabled = !isSigningOut && !nameState.isSaving && !isCreatingFamily,
            onClick = { showSignOutConfirm = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 20.dp, top = 8.dp),
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.onboarding_sign_out_title)) },
            text = { Text(stringResource(R.string.onboarding_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    viewModel.signOut(onSignedOut)
                }) { Text(stringResource(R.string.onboarding_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text(stringResource(R.string.travel_cancel)) }
            },
        )
    }
}

/** «Esci», in alto a destra su ogni pagina del wizard. */
@Composable
private fun OnboardingSignOutButton(
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .shadow(elevation = 8.dp, shape = CircleShape, spotColor = accent.copy(alpha = 0.12f))
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.onboarding_sign_out),
            color = if (enabled) accent else GrayDisabled,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun pageAccent(page: Int, familyPath: FamilyPath): Color =
    if (page == 1 && familyPath == FamilyPath.Join) PurpleAccent else OrangeAccent

private fun pageIconTint(page: Int, familyPath: FamilyPath): Color =
    if (page == 1 && familyPath == FamilyPath.Join) Color(0xFF9B7BC9) else Color(0xFFFFBF40)

@Composable
private fun TopAccentGradient(accent: Color, pageIndex: Int) {
    val strength = when (pageIndex) {
        in 0..2 -> 0.12f
        else -> 0.06f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = strength), Color.Transparent),
                ),
            ),
    )
}

@Composable
private fun OnboardingBackButton(
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .shadow(elevation = 8.dp, shape = CircleShape, spotColor = accent.copy(alpha = 0.12f))
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.onboarding_back),
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun IconHeroCard(
    icon: ImageVector,
    iconTint: Color,
    accent: Color,
    glow: Color,
) {
    val iconScale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        iconScale.animateTo(1f, tween(500))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(200.dp),
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.45f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(130.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = accent.copy(alpha = 0.35f),
                    ambientColor = accent.copy(alpha = 0.2f),
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .border(1.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    },
                tint = iconTint,
            )
        }
    }
}

/**
 * Pagina 0: nome e cognome dell'utente e nome della famiglia, in una
 * schermata sola. Il salvataggio e la creazione li fa il CTA del parent.
 *
 * Il nome sta qui, prima della famiglia, apposta: il documento membro nasce
 * alla creazione/join, quindi avere già il nome permette di scriverlo lì
 * subito invece di lasciare il membro anonimo agli altri.
 *
 * Nel percorso «entra» (QR) il campo famiglia sparisce: il nome della
 * famiglia lo porta l'invito. Gemello di `SetupFamilyCard` su iOS.
 */
@Composable
private fun SetupPageContent(
    /**
     * Non nullo quando il referrer di Play dice che c'è un invito ma il
     * segreto non è stato trovato negli appunti: stringa vuota se il nome
     * della famiglia non è ancora arrivato.
     */
    pendingInviteFamilyName: String?,
    onRetryInvite: () -> Unit,
    state: OnboardingNameUiState,
    viewModel: OnboardingNameViewModel,
    familyName: String,
    onFamilyNameChange: (String) -> Unit,
    isJoin: Boolean,
    isBusy: Boolean,
    errorText: String?,
    onTogglePath: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 24.dp, top = 16.dp, end = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(OrangeAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isJoin) Icons.Filled.QrCodeScanner else Icons.Filled.Home,
                contentDescription = null,
                tint = OrangeAccent,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(if (isJoin) R.string.onboarding_setup_join_title else R.string.onboarding_setup_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = BlackText,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(if (isJoin) R.string.onboarding_setup_join_subtitle else R.string.onboarding_setup_subtitle),
            fontSize = 15.sp,
            color = GraySubtitle,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        if (pendingInviteFamilyName != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrangeAccent.copy(alpha = 0.10f))
                    .padding(16.dp),
            ) {
                Text(
                    if (pendingInviteFamilyName.isBlank()) {
                        stringResource(R.string.onboarding_pending_invite_generic)
                    } else {
                        stringResource(R.string.onboarding_pending_invite, pendingInviteFamilyName)
                    },
                    fontSize = 14.sp,
                    color = BlackText,
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_pending_invite_retry),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OrangeAccent,
                    modifier = Modifier.clickable(enabled = !isBusy, onClick = onRetryInvite),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FormFieldLabel(stringResource(R.string.onboarding_name_first_label))
        OutlinedTextField(
            value = state.firstName,
            onValueChange = viewModel::setFirstName,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            singleLine = true,
            placeholder = {
                Text(stringResource(R.string.onboarding_name_first_placeholder), color = GrayCaption)
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(16.dp),
            colors = onboardingFieldColors(OrangeAccent),
        )
        Spacer(modifier = Modifier.height(12.dp))

        FormFieldLabel(stringResource(R.string.onboarding_name_last_label))
        OutlinedTextField(
            value = state.lastName,
            onValueChange = viewModel::setLastName,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            singleLine = true,
            placeholder = {
                Text(stringResource(R.string.onboarding_name_last_placeholder), color = GrayCaption)
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(16.dp),
            colors = onboardingFieldColors(OrangeAccent),
        )

        if (!isJoin) {
            Spacer(modifier = Modifier.height(12.dp))
            FormFieldLabel(stringResource(R.string.onboarding_family_name))
            OutlinedTextField(
                value = familyName,
                onValueChange = onFamilyNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.onboarding_family_hint), color = GrayCaption) },
                leadingIcon = { Text("👨‍👩‍👧", fontSize = 20.sp) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(16.dp),
                colors = onboardingFieldColors(OrangeAccent),
            )
        }

        val error = errorText?.takeIf { it.isNotBlank() } ?: state.error?.takeIf { it.isNotBlank() }
        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Percorso alternativo, in fondo e discreto: chi ha un invito è una
        // minoranza, e chi ha toccato un link non passa nemmeno di qui.
        TextButton(onClick = onTogglePath, enabled = !isBusy) {
            Text(
                stringResource(if (isJoin) R.string.onboarding_switch_to_create else R.string.onboarding_switch_to_join),
                color = OrangeAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Sostituisce tutto il wizard quando parte da un App Link:
 * mostra la famiglia (e chi ha invitato, se noti), chiede nome e cognome e fa
 * il join in un solo passaggio — niente QR, niente pagina di setup.
 *
 * Gemello di `LinkInviteConfirmCard` su iOS.
 */
@Composable
private fun LinkInviteConfirmPageContent(
    invite: PendingFamilyInvite,
    preview: InviteRemoteStore.InvitePreview?,
    nameState: OnboardingNameUiState,
    nameViewModel: OnboardingNameViewModel,
    onJoined: (familyId: String) -> Unit,
    onFallbackToManual: () -> Unit,
) {
    val joinViewModel: JoinFamilyViewModel = hiltViewModel()
    val joinState by joinViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Il join ha già fatto `addMember`, quindi il membro nasce senza
    // displayName: il nome raccolto qui va portato lì adesso.
    //
    // `PendingFamilyInvite.clear()` è indispensabile qui: questa vista lo
    // ha caricato una volta e lo tiene in stato locale (`invite`), senza mai
    // ripassare da `PendingFamilyInvite.load()`. Se non lo si toglie da
    // SharedPreferences, appena si atterra in Home `PendingInviteHandler` lo
    // trova ancora lì e lo rielabora — invito ormai marcato "usedAt" sul
    // server, quindi il secondo tentativo fallisce con "Invito già
    // utilizzato" e lo mostra come falso errore proprio dopo un join riuscito.
    LaunchedEffect(joinState.didJoin, joinState.joinedFamilyId) {
        val joinedId = joinState.joinedFamilyId
        if (joinState.didJoin && joinedId != null) {
            PendingFamilyInvite.clear(context)
            AppAnalytics.onboardingStepCompleted(context, "link_invite_confirm")
            onJoined(joinedId)
        }
    }

    val isBusy = nameState.isSaving || joinState.isBusy
    val canSubmit = nameState.firstName.isNotBlank() && nameState.lastName.isNotBlank() && !isBusy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 24.dp, top = 16.dp, end = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PurpleAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.People,
                contentDescription = null,
                tint = PurpleAccent,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val familyName = preview?.familyName?.takeIf { it.isNotBlank() }
        Text(
            if (familyName != null) {
                stringResource(R.string.onboarding_link_invite_title, familyName)
            } else {
                stringResource(R.string.onboarding_link_invite_title_generic)
            },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = BlackText,
            textAlign = TextAlign.Center,
        )

        val inviterName = preview?.inviterDisplayName?.takeIf { it.isNotBlank() }
        Text(
            if (inviterName != null) {
                stringResource(R.string.onboarding_link_invite_subtitle_by, inviterName)
            } else {
                stringResource(R.string.onboarding_link_invite_subtitle_generic)
            },
            fontSize = 15.sp,
            color = GraySubtitle,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))

        FormFieldLabel(stringResource(R.string.onboarding_name_first_label))
        OutlinedTextField(
            value = nameState.firstName,
            onValueChange = nameViewModel::setFirstName,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            singleLine = true,
            placeholder = {
                Text(stringResource(R.string.onboarding_name_first_placeholder), color = GrayCaption)
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(16.dp),
            colors = onboardingFieldColors(PurpleAccent),
        )
        Spacer(modifier = Modifier.height(12.dp))

        FormFieldLabel(stringResource(R.string.onboarding_name_last_label))
        OutlinedTextField(
            value = nameState.lastName,
            onValueChange = nameViewModel::setLastName,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            singleLine = true,
            placeholder = {
                Text(stringResource(R.string.onboarding_name_last_placeholder), color = GrayCaption)
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(16.dp),
            colors = onboardingFieldColors(PurpleAccent),
        )

        val errorText = nameState.error ?: joinState.error
        if (!errorText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                errorText,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                nameViewModel.save {
                    joinViewModel.joinFromInvite(invite) { /* stato osservato dal LaunchedEffect */ }
                }
            },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PurpleAccent,
                disabledContainerColor = GrayDisabled.copy(alpha = 0.4f),
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.7f),
            ),
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.onboarding_link_invite_join), fontWeight = FontWeight.SemiBold)
            }
        }

        TextButton(onClick = onFallbackToManual, enabled = !isBusy) {
            Text(
                stringResource(R.string.onboarding_link_invite_fallback),
                fontSize = 13.sp,
                color = GraySubtitle,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FormFieldLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = GrayCaption,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
}

@Composable
private fun InvitePartnerPageContent(
    familyId: String,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: InviteCodeViewModel = hiltViewModel()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val qrPayload by viewModel.qrPayload.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val shareLink by viewModel.shareLink.collectAsStateWithLifecycle()
    var didCopy by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppAnalytics.onboardingInviteStepShown(context)
    }

    LaunchedEffect(familyId) {
        if (familyId.isNotBlank()) {
            viewModel.generateInviteCode(preferredFamilyId = familyId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // ── Header ──
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(OrangeAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.People, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_invite_title),
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BlackText, textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.onboarding_invite_subtitle),
            fontSize = 16.sp, color = GraySubtitle, textAlign = TextAlign.Center, lineHeight = 23.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        // ── Pulsante condividi (primario) ──
        when {
            isBusy -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GrayCaption.copy(alpha = 0.08f))
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OrangeAccent)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.onboarding_generating_qr), fontSize = 14.sp, color = GraySubtitle)
                }
            }
            !shareLink.isNullOrBlank() -> {
                // Si condivide il link, non più il codice: il link porta anche la
                // chiave di cifratura (nel frammento) e apre l'app da solo.
                // Stesse stringhe delle impostazioni e di iOS: l'invito deve
                // arrivare identico da qualunque punto lo si mandi.
                val shareText = stringResource(R.string.settings_invite_share_text, shareLink.orEmpty())
                val shareSubject = stringResource(R.string.settings_invite_share_subject)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = OrangeAccent.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFBF40), OrangeAccent)))
                        .clickable {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                // Oggetto per i client di posta; le app di
                                // messaggistica lo ignorano.
                                putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            // Stesso evento di InviteCodeScreen e QuickInviteSheet: senza,
                            // il wizard Android era cieco su chi condivide davvero
                            // (20/09: 16 inviti generati, 0 invite_shared).
                            AppAnalytics.inviteShared(context, "system_share_sheet")
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.onboarding_share_link)))
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(stringResource(R.string.onboarding_share_link), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedSoftButton(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = GraySubtitle.copy(alpha = 0.08f),
                    contentColor = if (didCopy) SuccessGreen else GraySubtitle,
                    icon = if (didCopy) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                    label = if (didCopy) stringResource(R.string.onboarding_copied) else stringResource(R.string.settings_invite_copy_link),
                    onClick = {
                        val value = shareLink.orEmpty()
                        if (value.isNotBlank()) {
                            runCatching {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("kidbox_invite_link", value))
                            }.onSuccess {
                                didCopy = true
                                AppAnalytics.inviteShared(context, "copy")
                            }
                        }
                    },
                )
                LaunchedEffect(didCopy) {
                    if (didCopy) { kotlinx.coroutines.delay(2000); didCopy = false }
                }
                // Il segreto viaggia dentro il link: chi lo riceve entra.
                Text(
                    stringResource(R.string.invite_link_caution),
                    fontSize = 11.sp,
                    color = GrayCaption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                )
            }
            errorMessage != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(28.dp))
                    Text(errorMessage.orEmpty(), color = GraySubtitle, fontSize = 14.sp, textAlign = TextAlign.Center)
                    TextButton(onClick = viewModel::generateInviteCode) {
                        Text(stringResource(R.string.onboarding_retry), color = OrangeAccent)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── QR collassabile (secondario) ──
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showQr = !showQr }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.onboarding_show_qr),
                    fontSize = 14.sp, color = GraySubtitle,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.invite_qr_safer_badge),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Icon(
                    if (showQr) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null, tint = GrayCaption, modifier = Modifier.size(20.dp),
                )
            }
            if (showQr && qrPayload != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QRCodeView(payload = qrPayload.orEmpty(), modifier = Modifier.size(140.dp))
                    Text(stringResource(R.string.onboarding_valid_7d), fontSize = 12.sp, color = GrayCaption)
                    Text(
                        stringResource(R.string.invite_qr_safer),
                        fontSize = 11.sp,
                        color = GrayCaption,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.HorizontalDivider(color = GrayFieldBorder)
        Spacer(modifier = Modifier.height(16.dp))

        // ── Bottoni di completamento ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFFBF40), OrangeAccent)))
                .clickable { onFinish() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.onboarding_did_share), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(
            onClick = {
                AppAnalytics.onboardingInviteStepSkipped(context)
                onFinish()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_skip_invite), fontSize = 15.sp, color = GrayCaption)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun OutlinedSoftButton(
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = contentColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun PageIndicators(
    pageCount: Int,
    currentPage: Int,
    accent: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { i ->
            val active = i == currentPage
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (active) 24.dp else 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (active) accent else Color(0xFFCCCCCC).copy(alpha = 0.45f),
                    ),
            )
        }
    }
}

@Composable
private fun MainCtaButton(
    label: String,
    accent: Color,
    iconTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = if (enabled) {
        Brush.horizontalGradient(colors = listOf(iconTint, accent))
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                GrayDisabled.copy(alpha = 0.45f),
                GrayDisabled.copy(alpha = 0.45f),
            ),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = accent.copy(alpha = 0.45f),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$label →",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

// MARK: - Pagina 3: scelta fra "Crea" / "Entra con QR"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinFamilyPageContent(
    onJoined: (familyId: String) -> Unit,
) {
    val viewModel: JoinFamilyViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showScanner by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showScanner = true
    }

    // Quando il join riesce, completa l'onboarding passando il familyId raggiunto.
    LaunchedEffect(state.didJoin, state.joinedFamilyId) {
        val joinedId = state.joinedFamilyId
        if (state.didJoin && joinedId != null) {
            onJoined(joinedId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PurpleAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = PurpleAccent,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_enter_family),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = BlackText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_join_subtitle),
            fontSize = 15.sp,
            color = GraySubtitle,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PurpleAccent.copy(alpha = 0.10f))
                .clickable(enabled = !state.didJoin) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
                .padding(vertical = 14.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = PurpleAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.onboarding_scan_qr),
                color = PurpleAccent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }

        if (state.isBusy) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = PurpleAccent,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.onboarding_joining),
                    color = GraySubtitle,
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.didJoin) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.onboarding_joined),
                    color = SuccessGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showScanner) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showScanner = false },
            sheetState = sheetState,
            containerColor = Color.Black,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                contentAlignment = Alignment.Center,
            ) {
                QRScannerView(
                    onQRDetected = { payload ->
                        showScanner = false
                        viewModel.onQRScanned(payload) { /* stato osservato dal LaunchedEffect */ }
                    },
                )
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Transparent),
                ) {
                    CornerBrackets()
                }
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.onboarding_close),
                        tint = Color.White,
                    )
                }
                Text(
                    stringResource(R.string.onboarding_scanner_hint),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Colori delle caselle di testo dell'onboarding.
 *
 * Il contenitore e' bianco fisso perche' questa schermata ha un suo aspetto
 * chiaro a prescindere dal tema. Ma senza dichiarare anche il colore del TESTO,
 * Compose usa `onSurface` del tema: in dark mode e' quasi bianco, e il nome che
 * si sta scrivendo diventa invisibile. Bianco su bianco.
 *
 * Definirli qui, e non in sei punti, e' cio' che impedisce di correggerne cinque
 * e dimenticare il sesto.
 */
@Composable
private fun onboardingFieldColors(focusedBorder: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = focusedBorder,
    unfocusedBorderColor = GrayFieldBorder,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    focusedTextColor = BlackText,
    unfocusedTextColor = BlackText,
    disabledTextColor = GrayDisabled,
    cursorColor = focusedBorder,
)
