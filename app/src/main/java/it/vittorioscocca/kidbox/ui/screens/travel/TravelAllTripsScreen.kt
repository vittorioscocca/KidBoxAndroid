package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TravelAllTripsScreen(
    familyId: String,
    onNavigateBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    viewModel: TravelListViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId) { viewModel.setFamilyId(familyId) }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearSearchAndSelection() }
    }

    val allTrips by viewModel.trips.collectAsStateWithLifecycle()
    val filteredTrips by viewModel.filteredTrips.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val legsByTripId by viewModel.legsByTripId.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedTripIds.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) stringResource(R.string.travel_select_trips) else stringResource(R.string.travel_all_trips),
                        color = kb.title,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    if (allTrips.isNotEmpty()) {
                        if (isSelectionMode) {
                            TextButton(
                                onClick = {
                                    if (selectedIds.size == filteredTrips.size) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAllTrips(filteredTrips.map { it.id })
                                    }
                                },
                            ) {
                                Text(
                                    if (selectedIds.size == filteredTrips.size) stringResource(R.string.travel_deselect) else stringResource(R.string.travel_all),
                                    color = kb.title,
                                )
                            }
                            TextButton(
                                onClick = { showDeleteDialog = true },
                                enabled = selectedIds.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.travel_delete), color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { viewModel.setSelectionMode(false) }) {
                                Text(stringResource(R.string.travel_cancel), color = kb.title)
                            }
                        } else {
                            TextButton(onClick = { viewModel.setSelectionMode(true) }) {
                                Text(stringResource(R.string.travel_select), color = kb.title)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
    ) { padding ->
        if (allTrips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.travel_none), color = kb.subtitle)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.travel_search_hint)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        singleLine = true,
                    )
                }
                if (filteredTrips.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.travel_no_results_dot),
                            color = kb.subtitle,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(filteredTrips, key = { it.id }) { trip ->
                        val selected = trip.id in selectedIds
                        val tripLegs = legsByTripId[trip.id].orEmpty()
                        TravelTripCard(
                            trip = trip,
                            legs = tripLegs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleTripSelection(trip.id)
                                        } else {
                                            onOpenTrip(trip.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            viewModel.setSelectionMode(true)
                                            viewModel.toggleTripSelection(trip.id)
                                        }
                                    },
                                ),
                            isSelected = selected,
                            showsSelectionBadge = isSelectionMode,
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminare ${selectedIds.size} viaggi?") },
            text = { Text(stringResource(R.string.travel_cannot_undo)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedTrips(allTrips)
                        showDeleteDialog = false
                    },
                ) {
                    Text(stringResource(R.string.travel_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }
}
