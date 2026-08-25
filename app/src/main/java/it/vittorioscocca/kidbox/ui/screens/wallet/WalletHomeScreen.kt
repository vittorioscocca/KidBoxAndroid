@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet

import it.vittorioscocca.kidbox.ui.components.FamilyKeyMissingGate
import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import it.vittorioscocca.kidbox.ui.screens.wallet.documents.WalletDocumentsSectionContent
import it.vittorioscocca.kidbox.ui.screens.wallet.documents.WalletDocumentsViewModel
import it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards.LoyaltyCardsSectionContent
import it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards.LoyaltyCardsViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.domain.model.WalletTicketKind
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale
import it.vittorioscocca.kidbox.notifications.AppSection
import it.vittorioscocca.kidbox.notifications.TrackSectionPresence
import it.vittorioscocca.kidbox.ui.components.KBEmptyState
import it.vittorioscocca.kidbox.ui.components.KBHeaderCircleButton
import androidx.compose.material.icons.filled.AddCircle

@Composable
fun WalletHomeScreen(
    familyId: String,
    onBack: () -> Unit,
    onTicketClick: (ticketId: String) -> Unit,
    onDocumentClick: (documentId: String) -> Unit = {},
    onLoyaltyCardClick: (cardId: String) -> Unit = {},
    onUpgrade: () -> Unit = {},
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrackSectionPresence(AppSection.WALLET, familyId)
    val snackbarHostState = remember { SnackbarHostState() }

    // Copre in un colpo solo le decifrature che qui falliscono in silenzio.
    FamilyKeyMissingGate(familyId)
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    val documentsViewModel: WalletDocumentsViewModel = hiltViewModel()
    val docsState by documentsViewModel.uiState.collectAsStateWithLifecycle()
    var showDocAddChoice by remember { mutableStateOf(false) }
    var showDocAddSheet by remember { mutableStateOf(false) }
    var showDocLinkSheet by remember { mutableStateOf(false) }
    var showLoyaltyCardAddFlow by remember { mutableStateOf(false) }

    // Stesso schema dei Documenti: il ViewModel della sezione vive qui, così i
    // suoi controlli (Seleziona / + / Elimina) possono stare nell'header
    // condivisa fra i tab invece che dentro il corpo della sezione.
    val loyaltyCardsViewModel: LoyaltyCardsViewModel = hiltViewModel()
    val loyaltyState by loyaltyCardsViewModel.uiState.collectAsStateWithLifecycle()
    var showLoyaltyDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(familyId) {
        viewModel.bind(familyId)
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissMessage()
    }

    if (showAddSheet) {
        AddWalletTicketSheet(
            familyId = familyId,
            viewModel = viewModel,
            onDismiss = { showAddSheet = false },
            onUpgrade = onUpgrade,
        )
    }

    if (showDocAddChoice) {
        AlertDialog(
            onDismissRequest = { showDocAddChoice = false },
            title = { Text(stringResource(R.string.wallet_new_document_title), fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.wallet_new_document_how)) },
            confirmButton = {
                TextButton(onClick = { showDocAddChoice = false; showDocAddSheet = true }) { Text(stringResource(R.string.wallet_scan_new_document)) }
            },
            dismissButton = {
                TextButton(onClick = { showDocAddChoice = false; showDocLinkSheet = true }) { Text(stringResource(R.string.wallet_link_existing_document)) }
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background),
        containerColor = MaterialTheme.kidBoxColors.background,
        topBar = {
            Column {
                // Stesso header delle altre sezioni (Calendario, Lista della spesa,
                // Garage, Animali, Casa): tondo «indietro» a sinistra, tondo «+» a
                // destra, titolo grande sotto. Le azioni di selezione dei tab
                // Documenti/Carte restano accanto al «+».
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KBHeaderCircleButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.wallet_back),
                                tint = MaterialTheme.kidBoxColors.title,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        when (selectedTab) {
                            0 -> KBHeaderCircleButton(onClick = { showAddSheet = true }) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.wallet_add_ticket_cd),
                                    tint = MaterialTheme.kidBoxColors.title,
                                )
                            }
                            2 -> if (loyaltyState.isSelecting) {
                                TextButton(
                                    onClick = { showLoyaltyDeleteConfirm = true },
                                    enabled = loyaltyState.selectedIds.isNotEmpty(),
                                ) {
                                    Text(
                                        stringResource(R.string.passwords_delete_count_button, loyaltyState.selectedIds.size),
                                        color = if (loyaltyState.selectedIds.isEmpty()) {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                                TextButton(onClick = { loyaltyCardsViewModel.setSelecting(false) }) {
                                    Text(stringResource(R.string.location_cancel_button))
                                }
                            } else {
                                if (loyaltyState.cards.isNotEmpty()) {
                                    TextButton(onClick = { loyaltyCardsViewModel.setSelecting(true) }) {
                                        Text(stringResource(R.string.passwords_select_button))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                }
                                KBHeaderCircleButton(onClick = { showLoyaltyCardAddFlow = true }) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.wallet_loyalty_add_cd),
                                        tint = MaterialTheme.kidBoxColors.title,
                                    )
                                }
                            }
                            else -> if (docsState.isSelecting) {
                                TextButton(onClick = { documentsViewModel.exitSelectionMode() }) { Text(stringResource(R.string.wallet_cancel)) }
                                IconButton(
                                    onClick = { documentsViewModel.deleteSelected() },
                                    enabled = docsState.selectedIds.isNotEmpty() && !docsState.isDeleting,
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.wallet_delete_selected_cd), tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                if (docsState.items.isNotEmpty()) {
                                    TextButton(onClick = { documentsViewModel.enterSelectionMode() }) { Text(stringResource(R.string.wallet_select)) }
                                    Spacer(Modifier.width(4.dp))
                                }
                                KBHeaderCircleButton(onClick = { showDocAddChoice = true }) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.wallet_add_document_cd),
                                        tint = MaterialTheme.kidBoxColors.title,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.wallet_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp,
                        ),
                        color = MaterialTheme.kidBoxColors.title,
                    )
                }
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.kidBoxColors.background,
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.wallet_tab_tickets)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.wallet_tab_documents)) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.wallet_tab_loyalty_cards)) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (selectedTab == 2) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LoyaltyCardsSectionContent(
                    familyId = familyId,
                    onCardClick = onLoyaltyCardClick,
                    showAddFlow = showLoyaltyCardAddFlow,
                    onShowAddFlowChange = { showLoyaltyCardAddFlow = it },
                    showDeleteConfirm = showLoyaltyDeleteConfirm,
                    onShowDeleteConfirmChange = { showLoyaltyDeleteConfirm = it },
                    viewModel = loyaltyCardsViewModel,
                )
            }
            return@Scaffold
        }

        if (selectedTab == 1) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                WalletDocumentsSectionContent(
                    familyId = familyId,
                    onDocumentClick = onDocumentClick,
                    onUpgrade = onUpgrade,
                    showAddSheet = showDocAddSheet,
                    onShowAddSheetChange = { showDocAddSheet = it },
                    showLinkSheet = showDocLinkSheet,
                    onShowLinkSheetChange = { showDocLinkSheet = it },
                    viewModel = documentsViewModel,
                )
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            if (state.hasQueuedSharePdf) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable(enabled = !state.isImporting) { viewModel.importQueuedShare() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.wallet_shared_pdf_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.kidBoxColors.title,
                            )
                            Text(
                                stringResource(R.string.wallet_shared_pdf_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.kidBoxColors.subtitle,
                            )
                        }
                        if (state.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(start = 8.dp).size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }

            if (state.tickets.isEmpty() && !state.hasQueuedSharePdf) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KBEmptyState(
                        icon = Icons.Filled.ConfirmationNumber,
                        title = stringResource(R.string.empty_wallet_tickets_title),
                        body = stringResource(R.string.empty_wallet_tickets_body),
                        primaryIcon = Icons.Filled.AddCircle,
                        primaryLabel = stringResource(R.string.empty_wallet_tickets_action),
                        onPrimary = { showAddSheet = true },
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = if (state.hasQueuedSharePdf) 0.dp else 16.dp,
                        bottom = 120.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy((-90).dp),
                ) {
                    itemsIndexed(state.tickets, key = { _, t -> t.id }) { index, ticket ->
                        WalletTicketCard(
                            ticket = ticket,
                            modifier = Modifier.zIndex(index.toFloat()),
                            onClick = { onTicketClick(ticket.id) },
                        )
                    }
                }
            }

            if (state.isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun WalletTicketCard(
    ticket: KBWalletTicketEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val kind = WalletTicketKind.from(ticket.kindRaw)
    val gradientStart = Color(kind.gradientStartHex)
    val gradientEnd = Color(kind.gradientEndHex)
    val dateFmt = remember { SimpleDateFormat("EEE, d MMM", KBLocale.current()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", KBLocale.current()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = gradientEnd,
                spotColor = gradientEnd,
            )
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(listOf(gradientStart, gradientEnd)),
            )
            .clickable(onClick = onClick),
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = (-40).dp, y = (-40).dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 40.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape),
        )

        // White overlay border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = kindIcon(kind, ticket.emitter),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        kind.displayName.uppercase(),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                    )
                    val defaultTicketTitle = stringResource(R.string.wallet_default_ticket_title)
                    Text(
                        ticket.title.ifBlank { defaultTicketTitle },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                val eventMs = ticket.eventDateEpochMillis
                val arrivalMs = ticket.eventEndDateEpochMillis
                val hasJourney = eventMs != null || arrivalMs != null ||
                    !ticket.location.isNullOrBlank() || !ticket.arrivalLocation.isNullOrBlank()

                Column(modifier = Modifier.weight(1f)) {
                    if (hasJourney) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            JourneyPoint(
                                timeText = eventMs?.let { timeFmt.format(Date(it)) },
                                location = ticket.location,
                                dateText = eventMs?.let { dateFmt.format(Date(it)) },
                            )
                            if (arrivalMs != null || !ticket.arrivalLocation.isNullOrBlank()) {
                                Text(
                                    "  →  ",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                                JourneyPoint(
                                    timeText = arrivalMs?.let { timeFmt.format(Date(it)) },
                                    location = ticket.arrivalLocation,
                                    dateText = null,
                                )
                            }
                        }
                    }
                    if (!ticket.holderName.isNullOrBlank()) {
                        Text(
                            ticket.holderName,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(top = if (hasJourney) 4.dp else 0.dp),
                        )
                    }
                }

                if (!ticket.bookingCode.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.wallet_code_label),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            ticket.bookingCode,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

/** Un estremo del viaggio (orario + luogo) mostrato nel footer della card biglietto. */
@Composable
private fun JourneyPoint(timeText: String?, location: String?, dateText: String?) {
    Column {
        if (dateText != null) {
            Text(dateText, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        if (timeText != null) {
            Text(
                timeText,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!location.isNullOrBlank()) {
            Text(
                location,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

private fun kindIcon(kind: WalletTicketKind, emitter: String?): ImageVector {
    val lower = emitter?.lowercase(Locale.ROOT) ?: ""
    if (lower.isNotBlank()) {
        if (lower.contains("ryanair") || lower.contains("easyjet") || lower.contains("wizz") ||
            lower.contains("volotea") || lower.contains("ita") || lower.contains("alitalia")
        ) return Icons.Filled.Flight
        if (lower.contains("trenitalia") || lower.contains("italo") || lower.contains("frecciarossa")) return Icons.Filled.Train
        if (lower.contains("flixbus") || lower.contains("itabus")) return Icons.Filled.DirectionsBus
        if (lower.contains("moby") || lower.contains("grimaldi") || lower.contains("tirrenia") || lower.contains("medmar")) return Icons.Filled.DirectionsBoat
    }
    return when (kind) {
        WalletTicketKind.FLIGHT -> Icons.Filled.Flight
        WalletTicketKind.TRAIN -> Icons.Filled.Train
        WalletTicketKind.FERRY -> Icons.Filled.DirectionsBoat
        WalletTicketKind.BUS -> Icons.Filled.DirectionsBus
        WalletTicketKind.CONCERT -> Icons.Filled.MusicNote
        WalletTicketKind.CINEMA -> Icons.Filled.Movie
        WalletTicketKind.PARKING -> Icons.Filled.LocalParking
        WalletTicketKind.MUSEUM -> Icons.Filled.Museum
        WalletTicketKind.OTHER -> Icons.Filled.ConfirmationNumber
    }
}
