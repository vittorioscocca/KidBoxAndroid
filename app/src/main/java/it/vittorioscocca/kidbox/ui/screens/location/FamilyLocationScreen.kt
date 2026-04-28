package it.vittorioscocca.kidbox.ui.screens.location

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.interaction.MutableInteractionSource
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBSharedLocationEntity
import it.vittorioscocca.kidbox.data.repository.LocationShareMode
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun FamilyLocationScreen(
    familyId: String,
    onBack: () -> Unit,
    viewModel: FamilyLocationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val darkMapStyle = remember(isDarkTheme) {
        if (!isDarkTheme) {
            null
        } else {
            runCatching {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.google_map_dark_style)
            }.getOrNull()
        }
    }
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    var showShareOptions by remember { mutableStateOf(false) }
    var showTemporaryDialog by remember { mutableStateOf(false) }
    var temporaryHours by remember { mutableIntStateOf(2) }
    var followingUserId by remember { mutableStateOf<String?>(null) }
    var hasAutoCenteredOnDevice by remember(familyId) { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationPermissionGranted(granted)
        if (granted) {
            showShareOptions = true
        } else {
            Toast.makeText(context, "Permesso posizione necessario", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(familyId) {
        viewModel.bindFamily(familyId)
        viewModel.setLocationPermissionGranted(viewModel.hasLocationPermissionNow())
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.deviceLatitude, state.deviceLongitude, followingUserId) {
        val lat = state.deviceLatitude ?: return@LaunchedEffect
        val lon = state.deviceLongitude ?: return@LaunchedEffect
        if (!hasAutoCenteredOnDevice && followingUserId == null) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15f),
                durationMs = 550,
            )
            hasAutoCenteredOnDevice = true
        }
    }
    LaunchedEffect(state.sharedUsers, followingUserId, hasAutoCenteredOnDevice) {
        val first = state.sharedUsers.firstOrNull() ?: return@LaunchedEffect
        if (!hasAutoCenteredOnDevice && followingUserId == null) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 14f),
                durationMs = 550,
            )
            hasAutoCenteredOnDevice = true
        }
    }
    LaunchedEffect(state.sharedUsers, followingUserId) {
        val followId = followingUserId ?: return@LaunchedEffect
        val user = state.sharedUsers.firstOrNull { it.id == followId }
        if (user == null) {
            followingUserId = null
            return@LaunchedEffect
        }
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngZoom(LatLng(user.latitude, user.longitude), 16f),
            durationMs = 550,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = viewModel.hasLocationPermissionNow(),
                mapStyleOptions = darkMapStyle,
            ),
            onMapClick = {
                followingUserId = null
            },
        ) {
            state.sharedUsers.forEach { user ->
                Marker(
                    state = MarkerState(position = LatLng(user.latitude, user.longitude)),
                    title = user.name,
                    snippet = user.statusSnippet(),
                    onClick = {
                        followingUserId = user.id
                        false
                    },
                )
            }
        }

        Card(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 14.dp, top = 10.dp)
                .size(44.dp)
                .clickable(onClick = onBack),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black)
            }
        }

        Text(
            text = "Posizione",
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            color = Color.Black,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFFF6B00),
            )
        }

        val currentFollowed = followingUserId?.let { followId ->
            state.sharedUsers.firstOrNull { it.id == followId }
        }
        if (currentFollowed != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 14.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.9f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Seguo ${currentFollowed.name}",
                        fontSize = 13.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { followingUserId = null },
                    )
                }
            }
        }

        val otherUsers = state.sharedUsers.filter { it.id != myUid }
        if (otherUsers.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 12.dp, bottom = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                otherUsers.forEach { user ->
                    FollowUserPill(
                        user = user,
                        isActive = followingUserId == user.id,
                        onClick = { followingUserId = user.id },
                    )
                }
            }
        }

        LocationBottomCard(
            state = state,
            isDarkTheme = isDarkTheme,
            onToggle = { enabled ->
                if (enabled) {
                    if (viewModel.hasLocationPermissionNow()) {
                        viewModel.setLocationPermissionGranted(true)
                        showShareOptions = true
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                } else {
                    viewModel.stopSharing()
                }
            },
            onMyLocationTap = {
                val lat = state.deviceLatitude ?: return@LocationBottomCard
                val lon = state.deviceLongitude ?: return@LocationBottomCard
                followingUserId = null
                scope.launch {
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f),
                        durationMs = 500,
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )

        if (showShareOptions) {
            ShareModeOverlay(
                onDismiss = { showShareOptions = false },
                onRealtime = {
                    showShareOptions = false
                    viewModel.startRealtime()
                },
                onTemporary = {
                    showShareOptions = false
                    showTemporaryDialog = true
                },
            )
        }

        if (showTemporaryDialog) {
            TemporaryDurationOverlay(
                selectedHours = temporaryHours,
                onSelectedHours = { temporaryHours = it },
                onDismiss = { showTemporaryDialog = false },
                onConfirm = {
                    showTemporaryDialog = false
                    viewModel.startTemporary(temporaryHours)
                },
            )
        }
    }
}

@Composable
private fun FollowUserPill(
    user: KBSharedLocationEntity,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (isActive) Color(0xFFFF8C00) else Color.White.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!user.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFFE8EDF5), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = user.name.firstOrNull()?.uppercase() ?: "?",
                        color = Color(0xFF374151),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
            Text(
                text = user.name.substringBefore(" ").ifBlank { user.name },
                color = if (isActive) Color.White else Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LocationBottomCard(
    state: FamilyLocationUiState,
    isDarkTheme: Boolean,
    onToggle: (Boolean) -> Unit,
    onMyLocationTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isDarkTheme) Color(0xD11C2733) else Color(0xE6EFF6E5)
    val innerCardColor = if (isDarkTheme) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.62f)
    val dangerColor = if (isDarkTheme) Color(0xFFFF7A7A) else Color(0xFFD54A4A)
    val secondaryTextColor = if (isDarkTheme) Color(0xFFB9C2CF) else Color(0xFF7B7B7B)
    val tertiaryTextColor = if (isDarkTheme) Color(0xFF9FA9B8) else Color(0xFF8B8B8B)
    val locationChipColor = if (isDarkTheme) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.82f)
    val chevronColor = if (isDarkTheme) Color(0xFFB7C0CD) else Color.Gray

    val myStatusText = when (state.myMode) {
        LocationShareMode.REALTIME -> "Stai condividendo la tua posizione"
        LocationShareMode.TEMPORARY -> {
            val expires = state.myExpiresAtEpochMillis
            if (expires != null) {
                "Stai condividendo temporaneamente fino alle ${formatHour(expires)}"
            } else {
                "Stai condividendo temporaneamente"
            }
        }
        null -> null
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Io",
                    fontSize = 34.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.kidBoxColors.title,
                )
                if (!state.isSharing) {
                    Text(
                        text = "Nessuna posizione condivisa",
                        color = dangerColor,
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    state.myCurrentAddress?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.kidBoxColors.title,
                            fontSize = 14.sp,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = innerCardColor,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = state.deviceLatitude != null,
                                    onClick = onMyLocationTap,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = locationChipColor,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (state.isSharing) Color(0xFF2E86FF) else chevronColor,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "La mia posizione",
                                color = MaterialTheme.kidBoxColors.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 19.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = if (state.deviceLatitude != null) Color(0xFF2E86FF) else chevronColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Condividi la mia posizione",
                                color = MaterialTheme.kidBoxColors.title,
                                fontSize = 18.sp,
                                lineHeight = 20.sp,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(
                                checked = state.isSharing,
                                onCheckedChange = onToggle,
                                thumbContent = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF30C659),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = if (isDarkTheme) Color(0xFF616872) else Color(0xFFD0D3D7),
                                ),
                            )
                        }

                        myStatusText?.let { status ->
                            Text(
                                text = status,
                                color = secondaryTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Condivisa da", color = tertiaryTextColor, fontSize = 11.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Questo Android", color = tertiaryTextColor, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareModeOverlay(
    onDismiss: () -> Unit,
    onRealtime: () -> Unit,
    onTemporary: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val dialogColor = if (isDarkTheme) Color(0xE6212B36) else Color(0xFFE7F6D8)
    val scrim = if (isDarkTheme) Color.Black.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.28f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = dialogColor,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Condividi la tua posizione",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) Color(0xFFEAF0F7) else Color.Black,
                )
                ShareActionButton(label = "Tempo reale", onClick = onRealtime, isDarkTheme = isDarkTheme)
                ShareActionButton(label = "Temporaneamente", onClick = onTemporary, isDarkTheme = isDarkTheme)
                ShareActionButton(label = "Annulla", onClick = onDismiss, isDarkTheme = isDarkTheme)
            }
        }
    }
}

@Composable
private fun TemporaryDurationOverlay(
    selectedHours: Int,
    onSelectedHours: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val dialogColor = if (isDarkTheme) Color(0xE6212B36) else Color(0xE8F4F8EF)
    val segmentedBg = if (isDarkTheme) Color(0xFF3A4654) else Color(0xFFDDE5D7)
    val scrim = if (isDarkTheme) Color.Black.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.28f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = dialogColor,
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Condividi temporaneamente",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    color = if (isDarkTheme) Color(0xFFEAF0F7) else Color.Black,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(segmentedBg, RoundedCornerShape(999.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(2, 3, 8).forEach { hours ->
                        val isSelected = selectedHours == hours
                        Surface(
                            onClick = { onSelectedHours(hours) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp),
                            color = when {
                                isSelected && isDarkTheme -> Color(0xFF566274)
                                isSelected -> Color.White
                                else -> Color.Transparent
                            },
                        ) {
                            Text(
                                text = "$hours ore",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    isSelected && isDarkTheme -> Color(0xFFEAF0F7)
                                    isSelected -> Color.Black
                                    isDarkTheme -> Color(0xFFC7D0DD)
                                    else -> Color.Black
                                },
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Surface(
                    onClick = onConfirm,
                    color = Color(0xFF0A84FF),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = "Conferma",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        color = Color.White,
                        fontSize = 22.sp,
                    )
                }
                Text(
                    text = "Annulla",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(onClick = onDismiss),
                    color = Color(0xFF0A84FF),
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun ShareActionButton(
    label: String,
    onClick: () -> Unit,
    isDarkTheme: Boolean,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (isDarkTheme) Color(0xCC34404D) else Color(0xD8D9E2D1),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDarkTheme) Color(0xFFEAF0F7) else Color.Black,
            textAlign = TextAlign.Center,
        )
    }
}

private fun KBSharedLocationEntity.statusSnippet(): String {
    if (modeRaw == LocationShareMode.TEMPORARY.raw && expiresAtEpochMillis != null) {
        return "Temporaneamente fino alle ${formatHour(expiresAtEpochMillis)}"
    }
    return "Tempo reale"
}

private fun formatHour(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(epochMillis))
