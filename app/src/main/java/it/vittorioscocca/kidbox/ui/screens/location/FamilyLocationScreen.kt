package it.vittorioscocca.kidbox.ui.screens.location

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.runtime.DisposableEffect
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
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import androidx.core.view.WindowCompat
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBSharedLocationEntity
import it.vittorioscocca.kidbox.data.repository.LocationShareMode
import it.vittorioscocca.kidbox.ui.permissions.RuntimePermissions
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsFeature
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsOrigin

@Composable
fun FamilyLocationScreen(
    familyId: String,
    onBack: () -> Unit,
    onGeofences: () -> Unit = {},
    viewModel: FamilyLocationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val window = (context as? ComponentActivity)?.window
    val isDarkTheme = isSystemInDarkTheme()
    val darkMapStyle = remember(isDarkTheme, context) {
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

    // Mappa a tutto schermo sotto status/navigation bar (ripristina uscendo dalla schermata).
    DisposableEffect(window, isDarkTheme) {
        if (window == null) {
            onDispose { }
        } else {
            val prevStatusColor = window.statusBarColor
            val prevNavColor = window.navigationBarColor
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            onDispose {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val restored = if (isDarkTheme) 0xFF1C1C1E.toInt() else 0xFFF5F3EE.toInt()
                window.statusBarColor = prevStatusColor.takeIf { it != AndroidColor.TRANSPARENT } ?: restored
                window.navigationBarColor = prevNavColor.takeIf { it != AndroidColor.TRANSPARENT } ?: restored
            }
        }
    }
    var showShareOptions by remember { mutableStateOf(false) }
    var showTemporaryDialog by remember { mutableStateOf(false) }
    var temporaryHours by remember { mutableIntStateOf(2) }
    var followingUserId by remember { mutableStateOf<String?>(null) }
    var hasAutoCenteredOnDevice by remember(familyId) { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState()

    var requestLocationForSharing by remember { mutableStateOf(false) }
    // Prominent disclosure obbligatorio (Google Play User Data policy): deve precedere
    // immediatamente ogni richiesta di permesso posizione, sia in primo piano sia in
    // background. L'utente deve sapere che la posizione viene raccolta e per farci cosa.
    var showForegroundDisclosure by remember { mutableStateOf(false) }
    var showBackgroundDisclosure by remember { mutableStateOf(false) }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        showShareOptions = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationPermissionGranted(granted)
        if (granted) {
            if (requestLocationForSharing) {
                requestLocationForSharing = false
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !RuntimePermissions.hasBackgroundLocationAccess(context)
                ) {
                    showBackgroundDisclosure = true
                } else {
                    showShareOptions = true
                }
            }
        } else {
            requestLocationForSharing = false
            Toast.makeText(context, "Permesso posizione necessario", Toast.LENGTH_LONG).show()
        }
    }

    // Guardare dov'è un familiare è cross-member per definizione: non c'è un
    // `createdBy` da confrontare, si sta guardando la posizione altrui.
    LaunchedEffect(Unit) {
        KBAnalytics.logRetrieval(
            feature = KBAnalyticsFeature.FAMILY_LOCATION,
            uploaderIsSelf = false,
            createdAtEpochMillis = null,
            entryPoint = KBAnalyticsOrigin.consume(),
        )
    }

    LaunchedEffect(familyId) {
        viewModel.startObservingActiveFamily(routeFamilyId = familyId)
        viewModel.bindFamily(familyId)
        viewModel.setLocationPermissionGranted(viewModel.hasLocationPermissionNow())
    }
    LaunchedEffect(Unit) {
        if (!viewModel.hasLocationPermissionNow()) {
            requestLocationForSharing = false
            showForegroundDisclosure = true
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = viewModel.hasLocationPermissionNow(),
                mapStyleOptions = darkMapStyle,
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapClick = {
                followingUserId = null
            },
        ) {
            state.sharedUsers.forEach { user ->
                val avatarDescriptor = rememberAvatarMarkerDescriptor(user.avatarUrl)
                Marker(
                    state = MarkerState(position = LatLng(user.latitude, user.longitude)),
                    title = user.name,
                    snippet = user.statusSnippet(),
                    icon = avatarDescriptor,
                    anchor = if (avatarDescriptor != null) androidx.compose.ui.geometry.Offset(0.5f, 0.5f) else androidx.compose.ui.geometry.Offset(0.5f, 1.0f),
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

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Card(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onGeofences),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Zone di arrivo",
                        tint = Color(0xFFFF6B00),
                    )
                }
            }
            val currentFollowed = followingUserId?.let { followId ->
                state.sharedUsers.firstOrNull { it.id == followId }
            }
            if (currentFollowed != null) {
                Surface(
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
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFFF6B00),
            )
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
                        requestLocationForSharing = true
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            !RuntimePermissions.hasBackgroundLocationAccess(context)
                        ) {
                            showBackgroundDisclosure = true
                        } else {
                            showShareOptions = true
                        }
                    } else {
                        requestLocationForSharing = true
                        showForegroundDisclosure = true
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

        if (showForegroundDisclosure) {
            ForegroundLocationDisclosureOverlay(
                onAccept = {
                    showForegroundDisclosure = false
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                onDecline = {
                    showForegroundDisclosure = false
                    requestLocationForSharing = false
                },
            )
        }

        if (showBackgroundDisclosure) {
            BackgroundLocationDisclosureOverlay(
                onAccept = {
                    showBackgroundDisclosure = false
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                },
                onDecline = {
                    showBackgroundDisclosure = false
                    // L'utente può comunque condividere in primo piano: niente background.
                    showShareOptions = true
                },
            )
        }

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

/**
 * Carica l'avatar dell'utente e lo rende come icona circolare per il marker sulla mappa,
 * come fa iOS (AvatarMarker). Ritorna `null` finché l'immagine non è pronta o se non c'è
 * un avatar: in quel caso il marker usa il pin di default.
 */
@Composable
private fun rememberAvatarMarkerDescriptor(avatarUrl: String?): BitmapDescriptor? {
    val context = LocalContext.current
    var descriptor by remember(avatarUrl) { mutableStateOf<BitmapDescriptor?>(null) }
    LaunchedEffect(avatarUrl) {
        descriptor = null
        val url = avatarUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val sizePx = (44 * context.resources.displayMetrics.density).toInt()
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(sizePx)
            .build()
        val result = (loader.execute(request) as? SuccessResult)?.drawable ?: return@LaunchedEffect
        val source = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val circular = circularAvatarBitmap(source, sizePx)
        descriptor = BitmapDescriptorFactory.fromBitmap(circular)
    }
    return descriptor
}

/** Crea un bitmap circolare con bordo bianco, dimensione `sizePx`. */
private fun circularAvatarBitmap(source: android.graphics.Bitmap, sizePx: Int): android.graphics.Bitmap {
    val borderPx = sizePx * 0.08f
    val output = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val radius = sizePx / 2f
    // Bordo bianco
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(radius, radius, radius, borderPaint)
    // Avatar ritagliato in cerchio, scalato a riempire l'area interna
    val inner = (sizePx - 2 * borderPx).toInt().coerceAtLeast(1)
    val scaled = android.graphics.Bitmap.createScaledBitmap(source, inner, inner, true)
    val shader = android.graphics.BitmapShader(
        scaled,
        android.graphics.Shader.TileMode.CLAMP,
        android.graphics.Shader.TileMode.CLAMP,
    )
    val matrix = android.graphics.Matrix().apply { postTranslate(borderPx, borderPx) }
    shader.setLocalMatrix(matrix)
    val avatarPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = shader
    }
    canvas.drawCircle(radius, radius, radius - borderPx, avatarPaint)
    return output
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
private fun ForegroundLocationDisclosureOverlay(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val dialogColor = if (isDarkTheme) Color(0xE6212B36) else Color(0xFFE7F6D8)
    val scrim = if (isDarkTheme) Color.Black.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.28f)
    val titleColor = if (isDarkTheme) Color(0xFFEAF0F7) else Color.Black
    val bodyColor = if (isDarkTheme) Color(0xFFC3CCD8) else Color(0xFF3A3A3A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim),
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
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "KidBox usa la tua posizione",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "KidBox raccoglie i dati di posizione del tuo dispositivo per " +
                        "mostrarti sulla mappa della famiglia e per condividere la tua " +
                        "posizione con i membri della tua famiglia che scegli tu.\n\n" +
                        "La posizione viene raccolta solo quando attivi la condivisione e " +
                        "viene usata esclusivamente per questa funzione: non la vendiamo e " +
                        "non la usiamo per pubblicità. Puoi interrompere la condivisione in " +
                        "qualsiasi momento dall'app.\n\n" +
                        "Nella schermata successiva Android ti chiederà di consentire " +
                        "l'accesso alla posizione.",
                    fontSize = 14.sp,
                    color = bodyColor,
                    lineHeight = 20.sp,
                )
                ShareActionButton(label = "Continua", onClick = onAccept, isDarkTheme = isDarkTheme)
                ShareActionButton(label = "No, grazie", onClick = onDecline, isDarkTheme = isDarkTheme)
            }
        }
    }
}

@Composable
private fun BackgroundLocationDisclosureOverlay(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val dialogColor = if (isDarkTheme) Color(0xE6212B36) else Color(0xFFE7F6D8)
    val scrim = if (isDarkTheme) Color.Black.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.28f)
    val titleColor = if (isDarkTheme) Color(0xFFEAF0F7) else Color.Black
    val bodyColor = if (isDarkTheme) Color(0xFFC3CCD8) else Color(0xFF3A3A3A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim),
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
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Accesso alla posizione in background",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Per condividere la tua posizione con la tua famiglia e per le " +
                        "notifiche delle zone (geofence) di arrivo/partenza, KidBox ha bisogno " +
                        "di accedere alla tua posizione anche quando l'app è chiusa o non in uso " +
                        "(in background).\n\n" +
                        "La posizione viene usata solo per mostrarla ai membri della tua famiglia " +
                        "e per le zone che configuri. Puoi interrompere la condivisione in " +
                        "qualsiasi momento dall'app.\n\n" +
                        "Nella schermata successiva seleziona \"Consenti sempre\" per attivare " +
                        "questa funzione.",
                    fontSize = 14.sp,
                    color = bodyColor,
                    lineHeight = 20.sp,
                )
                ShareActionButton(label = "Accetto", onClick = onAccept, isDarkTheme = isDarkTheme)
                ShareActionButton(label = "No, grazie", onClick = onDecline, isDarkTheme = isDarkTheme)
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
