package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelDetailScreen(
    tripId: String,
    familyId: String,
    navController: NavHostController,
    viewModel: TravelDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(tripId) { viewModel.setTripId(tripId) }
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val dayPlans by viewModel.dayPlans.collectAsStateWithLifecycle()
    val legs by viewModel.legs.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val children by viewModel.children.collectAsStateWithLifecycle()
    val extras by viewModel.extrasState.collectAsStateWithLifecycle()
    val regeneratingDayId by viewModel.regeneratingDayId.collectAsStateWithLifecycle()
    val dayRegenerateError by viewModel.dayRegenerateError.collectAsStateWithLifecycle()
    val dayRegenerateSuccess by viewModel.dayRegenerateSuccess.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun openTripPhotoAlbum(tripEntity: KBTripEntity) {
        scope.launch {
            val albumId = viewModel.ensureAlbumForTrip(tripEntity) ?: run {
                Toast.makeText(
                    context,
                    context.getString(R.string.travel_login_album),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            navController.navigate(
                AppDestination.PhotoAlbumDetail.createRoute(
                    familyId = familyId,
                    albumId = albumId,
                    albumTitle = viewModel.tripAlbumTitle(tripEntity.name),
                    isTripAlbum = true,
                ),
            )
        }
    }
    val userName = remember(members, uid) {
        members.firstOrNull { it.userId == uid }?.displayName.orEmpty()
    }

    LaunchedEffect(trip?.id, uid) {
        val current = trip ?: return@LaunchedEffect
        if (uid.isNotBlank()) {
            viewModel.ensureTripExtras(current, userName)
        }
    }

    LaunchedEffect(dayRegenerateError) {
        dayRegenerateError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val deleteTripError by viewModel.deleteTripError.collectAsStateWithLifecycle()
    LaunchedEffect(deleteTripError) {
        deleteTripError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(dayRegenerateSuccess) {
        dayRegenerateSuccess?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearDayRegenerateSuccess()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: stringResource(R.string.travel_trip), color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.travel_delete_trip), tint = kb.title)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
    ) { padding ->
        val currentTrip = trip
        val itineraryOverview = currentTrip?.let {
            viewModel.itineraryOverview(it, dayPlans, legs, members, children)
        }
        val hasItinerary = itineraryOverview?.days?.isNotEmpty() == true
        when {
            currentTrip == null -> {
                Column(Modifier.padding(padding).padding(16.dp)) {
                    Text(stringResource(R.string.travel_loading), color = kb.subtitle)
                }
            }
            !hasItinerary -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(stringResource(R.string.travel_no_days), color = kb.subtitle)
                    }
                    item {
                        TravelTripExtrasSection(
                            photoCount = extras.photoCount,
                            noteTitle = currentTrip.name,
                            noteHasContent = extras.noteHasContent,
                            todoListName = currentTrip.name,
                            openTodoCount = extras.openTodoCount,
                            onPhotosClick = { openTripPhotoAlbum(currentTrip) },
                            onNotesClick = {
                                scope.launch {
                                    val noteId = viewModel.ensureNoteForTrip(currentTrip, userName) ?: return@launch
                                    navController.navigate(
                                        AppDestination.NoteDetail.createRoute(familyId, noteId),
                                    )
                                }
                            },
                            onTodosClick = {
                                children.firstOrNull()?.id?.let { childId ->
                                    scope.launch {
                                        val listId = viewModel.ensureTodoListForTrip(currentTrip, childId) ?: return@launch
                                        navController.navigate(
                                            AppDestination.TodoList.createRoute(familyId, childId, listId),
                                        )
                                    }
                                }
                            },
                            onExpensesClick = {
                                navController.navigate(
                                    AppDestination.ExpensesHome.createRoute(
                                        familyId = familyId,
                                        initialCategoryId = viewModel.travelExpenseCategoryId(familyId),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            else -> {
                val overview = itineraryOverview!!
                val hotels = TravelItineraryBuilder.collectHotels(dayPlans, overview)
                val restaurants = TravelItineraryBuilder.collectRestaurants(
                    dayPlans,
                    overview,
                    currentTrip.aiProposalJson,
                )
                val activities = TravelItineraryBuilder.collectActivities(
                    dayPlans,
                    overview,
                    currentTrip.aiProposalJson,
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        TravelItineraryDetailContent(
                            overview = overview,
                            legs = legs,
                            heroImageDestination = TravelItineraryBuilder.destinationTitle(currentTrip.name),
                            hotelsCount = hotels.size,
                            restaurantsCount = restaurants.size,
                            activitiesCount = activities.size,
                            onHotelsClick = {
                                navController.navigate(
                                    AppDestination.TravelCategoryResults.createRoute(familyId, tripId, "hotels"),
                                )
                            },
                            onRestaurantsClick = {
                                navController.navigate(
                                    AppDestination.TravelCategoryResults.createRoute(familyId, tripId, "restaurants"),
                                )
                            },
                            onActivitiesClick = {
                                navController.navigate(
                                    AppDestination.TravelCategoryResults.createRoute(familyId, tripId, "activities"),
                                )
                            },
                            onPlaceInfoClick = {
                                navController.navigate(
                                    AppDestination.TravelPlaceInfo.createRoute(familyId, overview.destinationTitle),
                                )
                            },
                            onStopClick = { stopContext ->
                                navController.navigate(stopContext.toPlaceDetailRoute(familyId))
                            },
                            onRegenerateDayClick = { day -> viewModel.regenerateDayPlan(day) },
                            regeneratingDayIndex = overview.days.firstOrNull { it.id == regeneratingDayId }?.dayIndex,
                        )
                    }
                    item {
                        TravelTripExtrasSection(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            photoCount = extras.photoCount,
                            noteTitle = currentTrip.name,
                            noteHasContent = extras.noteHasContent,
                            todoListName = currentTrip.name,
                            openTodoCount = extras.openTodoCount,
                            onPhotosClick = { openTripPhotoAlbum(currentTrip) },
                            onNotesClick = {
                                scope.launch {
                                    val noteId = viewModel.ensureNoteForTrip(currentTrip, userName) ?: return@launch
                                    navController.navigate(
                                        AppDestination.NoteDetail.createRoute(familyId, noteId),
                                    )
                                }
                            },
                            onTodosClick = {
                                children.firstOrNull()?.id?.let { childId ->
                                    scope.launch {
                                        val listId = viewModel.ensureTodoListForTrip(currentTrip, childId) ?: return@launch
                                        navController.navigate(
                                            AppDestination.TodoList.createRoute(familyId, childId, listId),
                                        )
                                    }
                                }
                            },
                            onExpensesClick = {
                                navController.navigate(
                                    AppDestination.ExpensesHome.createRoute(
                                        familyId = familyId,
                                        initialCategoryId = viewModel.travelExpenseCategoryId(familyId),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

        if (regeneratingDayId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                    Text(stringResource(R.string.travel_regenerating_day), color = Color.White)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.travel_delete_trip_q)) },
            text = { Text(stringResource(R.string.travel_delete_trip_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        trip?.let { entity ->
                            viewModel.deleteTrip(entity) {
                                navController.popBackStack()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.travel_delete))
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
