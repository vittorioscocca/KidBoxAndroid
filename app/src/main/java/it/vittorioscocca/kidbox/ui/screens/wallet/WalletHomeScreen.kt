@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun WalletHomeScreen(
    familyId: String,
    onBack: () -> Unit,
    onTicketClick: (ticketId: String) -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddSheet by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.importPdf(it) }
    }

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
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        "Wallet",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Aggiungi biglietto")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
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
                                "PDF condiviso con KidBox",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.kidBoxColors.title,
                            )
                            Text(
                                "Tocca per salvare nel wallet famiglia",
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.ConfirmationNumber,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Nessun biglietto. Aggiungi il tuo primo PDF.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.kidBoxColors.subtitle,
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
                            modifier = Modifier.zIndex((state.tickets.size - index).toFloat()),
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
    val dateFmt = remember { SimpleDateFormat("EEE, d MMM", Locale.ITALIAN) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.ITALIAN) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
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
                    Text(
                        ticket.title.ifBlank { "Biglietto" },
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
                if (eventMs != null) {
                    Column {
                        Text(
                            dateFmt.format(Date(eventMs)),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                        )
                        Text(
                            timeFmt.format(Date(eventMs)),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (!ticket.bookingCode.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "CODICE",
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
