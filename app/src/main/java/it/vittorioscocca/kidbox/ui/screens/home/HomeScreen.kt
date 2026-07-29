package it.vittorioscocca.kidbox.ui.screens.home

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import it.vittorioscocca.kidbox.ai.AskAiButton
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.sqrt
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KBLocale
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.ui.components.KidBoxAvatar
import it.vittorioscocca.kidbox.ui.family.FamilySwitcherBottomSheet
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.KidBoxDarkColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.ui.util.rememberSingleImagePicker
import it.vittorioscocca.kidbox.ui.util.singleImageRequest

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onReloadHome: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homeViewMode by viewModel.homeViewMode.collectAsStateWithLifecycle()
    var showFamilySwitcher by remember { mutableStateOf(false) }
    val familySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingUri by viewModel.pendingHeroUri.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenVisible()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val photoPicker = rememberSingleImagePicker { uri: Uri? ->
        uri?.let { viewModel.onHeroPhotoSelected(it, context) }
    }

    // ── Cropper overlay (come iOS showHeroCropper sheet) ─────────────────────
    // Se c'è una URI pending, mostriamo il cropper a tutto schermo sopra la Home
    if (pendingUri != null) {
        HeroPhotoCropperScreen(
            imageUri = pendingUri!!,
            initialCrop = HeroCrop(
                scale = state.heroPhotoScale,
                offsetX = state.heroPhotoOffsetX,
                offsetY = state.heroPhotoOffsetY,
            ),
            isSaving = state.isUploadingHero,
            onCancel = { viewModel.onHeroCropCancelled() },
            onSave = { crop ->
                viewModel.onHeroCropSaved(pendingUri!!, crop, context)
            },
        )
        return // non renderizzare la Home sotto
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background),
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF6B00))
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(10f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KidBoxAvatar(
                    imageUrl = state.avatarUrl,
                    name = "",
                    size = 44.dp,
                    modifier = Modifier
                        .shadow(6.dp, CircleShape)
                        .border(2.dp, MaterialTheme.kidBoxColors.card, CircleShape)
                        .clip(CircleShape)
                        .clickable { onNavigate(AppDestination.Profile.route) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onNavigate(AppDestination.Suggestions.route) }) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.kidBoxColors.title,
                        )
                    }
                    IconButton(onClick = { onNavigate(AppDestination.Settings.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "KidBox",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.kidBoxColors.title,
                    )
                    if (state.familyName.isNotBlank()) {
                        Text(
                            text = state.familyName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.kidBoxColors.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HomeHeaderControls(
                    viewMode = homeViewMode,
                    onViewModeChange = { viewModel.setHomeViewMode(it) },
                    onSwitchFamily = { showFamilySwitcher = true },
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            HomeHeroCarousel(
                state = state,
                onNavigate = onNavigate,
                onChangeHeroPhoto = {
                    photoPicker.launch(singleImageRequest())
                },
            )
            if (state.isMembersSyncing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFF6B00),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_members_syncing),
                        color = MaterialTheme.kidBoxColors.subtitle,
                        fontSize = 12.sp,
                    )
                }
            }
            state.membersSyncWarning?.let { warning ->
                Text(
                    text = warning,
                    color = Color(0xFFD9822B),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            if (state.isUploadingHero) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                Text(err, color = Color(0xFFE53E3E), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.size(16.dp))

            if (homeViewMode == it.vittorioscocca.kidbox.data.local.HomeViewMode.GRID) {
                HomeFreeGridSection(
                    state = state,
                    onNavigate = onNavigate,
                    onFeatureOpened = { field -> viewModel.onFeatureOpened(field) },
                    onRecordFeatureUsage = { id -> viewModel.recordFeatureUsage(id) },
                    initialOrder = viewModel.getGridOrder(),
                    onOrderChanged = { ids -> viewModel.setGridOrder(ids) },
                )
            } else {
                HomeCategorySection(
                    state = state,
                    onNavigate = onNavigate,
                    onFeatureOpened = { field -> viewModel.onFeatureOpened(field) },
                    onRecordFeatureUsage = { id -> viewModel.recordFeatureUsage(id) },
                )
            }
            // Evita che l’ultima riga resti sotto al FAB (overlay in basso a destra).
            Spacer(Modifier.height(88.dp))
        }

        // Stesso pulsante AI della card Salute (AskAiButton condiviso).
        AskAiButton(
            contentDescription = stringResource(R.string.home_ai_assistant_desc),
            onTap = { onNavigate(AppDestination.AiChat.createRoute(state.familyId)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    if (showFamilySwitcher) {
        FamilySwitcherBottomSheet(
            sheetState = familySheetState,
            snackbarHostState = snackbarHostState,
            onDismiss = { showFamilySwitcher = false },
            onSwitchComplete = {
                showFamilySwitcher = false
                onReloadHome()
            },
            onJoinFamily = { onNavigate(AppDestination.JoinFamily.route) },
        )
    }
}

// ── HomeHeaderControls ────────────────────────────────────────────────────────
// Lista/griglia sono un vero switch segmentato (una sola pillola, due stati
// mutuamente esclusivi). Il cambio famiglia è un'azione diversa, quindi resta un
// pulsante separato, staccato dallo switch.

@Composable
private fun HomeHeaderControls(
    viewMode: it.vittorioscocca.kidbox.data.local.HomeViewMode,
    onViewModeChange: (it.vittorioscocca.kidbox.data.local.HomeViewMode) -> Unit,
    onSwitchFamily: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val listMode = it.vittorioscocca.kidbox.data.local.HomeViewMode.LIST
    val gridMode = it.vittorioscocca.kidbox.data.local.HomeViewMode.GRID

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = kb.card,
            border = BorderStroke(1.dp, kb.divider),
        ) {
            Row(
                modifier = Modifier.height(36.dp).padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentIcon(
                    icon = Icons.AutoMirrored.Filled.List,
                    selected = viewMode == listMode,
                    contentDescription = stringResource(R.string.home_view_mode_list_desc),
                    onClick = { onViewModeChange(listMode) },
                )
                SegmentIcon(
                    icon = Icons.Filled.GridView,
                    selected = viewMode == gridMode,
                    contentDescription = stringResource(R.string.home_view_mode_grid_desc),
                    onClick = { onViewModeChange(gridMode) },
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(
            onClick = onSwitchFamily,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(R.string.home_change_family_desc),
                tint = kb.title,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SegmentIcon(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFFFF6B00) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) Color.White else kb.subtitle,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── FamilyHeroCard ─────────────────────────────────────────────────────────────
// Ora applica scale/offset dal crop — identico alla logica di HomeHeroCard iOS

@Composable
private fun FamilyHeroCard(
    familyName: String,
    dateLabel: String,
    members: Int,
    photoLocalPath: String?,
    photoUrl: String?,
    heroScale: Float = 1f,
    heroOffsetX: Float = 0f,
    heroOffsetY: Float = 0f,
    onChangePhoto: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Immagine con crop applicato tramite graphicsLayer
            val localModel = photoLocalPath?.let { path ->
                val f = File(path)
                if (f.exists()) f else null
            }
            val imageModel = localModel ?: photoUrl

            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = heroScale,
                            scaleY = heroScale,
                            translationX = heroOffsetX,
                            translationY = heroOffsetY,
                        ),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.kidBoxColors.divider))
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                    ),
            )

            // Top row: data + badge membri
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(dateLabel, color = Color.White, fontSize = 12.sp)
                Surface(
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        stringResource(R.string.home_hero_members_count, members),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
            }

            // Bottom: nome famiglia + bottone cambio foto
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    familyName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onChangePhoto)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(
                                if (photoUrl != null) R.string.home_hero_change_photo
                                else R.string.home_hero_add_photo,
                            ),
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── HomeHeroCarousel: foto famiglia + slide promozionali circolari ───────────
// Slide 1 = FamilyHeroCard esistente (upload/tap invariato). Slide 2-10 = promo,
// mostrate solo se la famiglia esiste già. Loop infinito con la tecnica delle
// pagine duplicate ai bordi: [ultima] + reali + [prima], correzione silenziosa
// dell'indice quando l'utente atterra su una delle due copie.

private data class PromoSlideSpec(
    val imageRes: Int,
    val titleRes: Int,
    val subtitleRes: Int? = null,
    val onClick: () -> Unit,
)

@Composable
private fun HomeHeroCarousel(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    onChangeHeroPhoto: () -> Unit,
) {
    val familyId = state.familyId
    val hasFamily = familyId.isNotBlank()
    val defaultFamilyName = stringResource(R.string.home_hero_default_family_name)

    // La data va formattata qui, non nel ViewModel: il ViewModel sopravvive alla
    // ricreazione dell'Activity, quindi un'etichetta calcolata lì resterebbe nella
    // lingua iniziale dopo un cambio lingua. Il key su LocalConfiguration la
    // ricalcola a ogni cambio di configurazione (locale incluso).
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val todayLabel = remember(configuration) {
        java.text.SimpleDateFormat("EEEE, d MMMM", KBLocale.current())
            .format(java.util.Date())
    }

    val photoSlide: @Composable () -> Unit = {
        FamilyHeroCard(
            familyName = state.familyName.ifBlank { defaultFamilyName },
            dateLabel = todayLabel,
            members = state.memberCount,
            photoLocalPath = state.heroPhotoLocalPath,
            photoUrl = state.heroPhotoUrl,
            heroScale = state.heroPhotoScale,
            heroOffsetX = state.heroPhotoOffsetX,
            heroOffsetY = state.heroPhotoOffsetY,
            onChangePhoto = onChangeHeroPhoto,
        )
    }

    if (!hasFamily) {
        photoSlide()
        return
    }

    val promoSlides = remember(familyId) {
        listOf(
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_invite,
                titleRes = R.string.home_promo_invite_title,
                onClick = { onNavigate(AppDestination.InviteCode.route) },
            ),
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_password,
                titleRes = R.string.home_promo_password_title,
                subtitleRes = R.string.home_promo_password_subtitle,
                onClick = { onNavigate(AppDestination.PasswordsHome.createRoute(familyId)) },
            ),
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_ai,
                titleRes = R.string.home_promo_ai_title,
                subtitleRes = R.string.home_promo_ai_subtitle,
                onClick = { onNavigate(AppDestination.AiChat.createRoute(familyId)) },
            ),
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_grocery,
                titleRes = R.string.home_promo_grocery_title,
                subtitleRes = R.string.home_promo_grocery_subtitle,
                onClick = { onNavigate(AppDestination.ShoppingList.createRoute(familyId)) },
            ),
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_calendar,
                titleRes = R.string.home_promo_calendar_title,
                onClick = { onNavigate(AppDestination.Calendar.createRoute(familyId)) },
            ),
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_expenses,
                titleRes = R.string.home_promo_expenses_title,
                subtitleRes = R.string.home_promo_expenses_subtitle,
                onClick = { onNavigate(AppDestination.ExpensesHome.createRoute(familyId)) },
            ),
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_documents1,
                titleRes = R.string.home_promo_documents1_title,
                subtitleRes = R.string.home_promo_documents1_subtitle,
                onClick = { onNavigate(AppDestination.DocumentsHome.createRoute(familyId)) },
            ),
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_documents2,
                titleRes = R.string.home_promo_documents2_title,
                subtitleRes = R.string.home_promo_documents2_subtitle,
                onClick = { onNavigate(AppDestination.DocumentsHome.createRoute(familyId)) },
            ),
            PromoSlideSpec(
                imageRes = it.vittorioscocca.kidbox.R.drawable.home_promo_health,
                titleRes = R.string.home_promo_health_title,
                subtitleRes = R.string.home_promo_health_subtitle,
                onClick = { onNavigate(AppDestination.PediatricChildSelector.createRoute(familyId)) },
            ),
        )
    }

    val slideCount = 1 + promoSlides.size
    val pagerCount = slideCount + 2
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1) { pagerCount }

    LaunchedEffect(pagerState, slideCount) {
        androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    when (pagerState.currentPage) {
                        0 -> pagerState.scrollToPage(slideCount)
                        pagerCount - 1 -> pagerState.scrollToPage(1)
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val realIndex = (((page - 1) % slideCount) + slideCount) % slideCount
            if (realIndex == 0) {
                photoSlide()
            } else {
                PromoSlide(promoSlides[realIndex - 1])
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val activeDot = (((pagerState.currentPage - 1) % slideCount) + slideCount) % slideCount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(slideCount) { index ->
                val isActive = index == activeDot
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isActive) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) Color(0xFFFF6B00) else MaterialTheme.kidBoxColors.divider,
                        ),
                )
            }
        }
    }
}

@Composable
private fun PromoSlide(spec: PromoSlideSpec) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clickable { spec.onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = spec.imageRes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(spec.titleRes),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                )
                spec.subtitleRes?.let { subtitleRes ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(subtitleRes),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

// ── FeatureCard, HomeFab, helpers ─────────────────────────────────────────────

internal data class FeatureItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val cardColor: Color,
    val iconColor: Color,
    val badgeCount: Int = 0,
    val counterField: CounterField? = null,
    val isPulsing: Boolean = false,
    /** Piano Free: card Assistente bloccata → tap apre schermata Piani (come iOS). */
    val locked: Boolean = false,
)

@Composable
private fun FeatureCard(item: FeatureItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val kidBox = MaterialTheme.kidBoxColors
    val isDark = kidBox === KidBoxDarkColorScheme
    val containerColor = when {
        item.locked && isDark -> Color(0xFF2C2C30)
        item.locked -> Color(0xFFF3F4F6)
        isDark -> kidBox.card
        else -> item.cardColor
    }
    val titleColor = when {
        item.locked && isDark -> Color(0xFFD1D5DB)
        item.locked -> Color(0xFF111827)
        else -> kidBox.title
    }
    val subtitleColor = when {
        item.locked -> Color(0xFF9CA3AF)
        else -> kidBox.subtitle
    }
    val iconTint = if (item.locked) Color(0xFF9CA3AF) else item.iconColor
    val borderColor = when {
        !item.locked -> Color.Transparent
        isDark -> Color.White.copy(alpha = 0.08f)
        else -> Color(0xFFE5E7EB)
    }
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .then(modifier),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
                Text(
                    item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.subtitle,
                    color = subtitleColor,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.locked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(26.dp),
                shape = CircleShape,
                color = Color(0xFF9CA3AF),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (item.badgeCount > 0) {
            HomeCardBadge(
                count = item.badgeCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
            )
        }
        if (item.isPulsing) {
            LocationPulseIndicator(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp),
            )
        }
    }
}

internal fun featureItems(context: Context, familyId: String, state: HomeUiState): List<FeatureItem> = listOf(
    FeatureItem("notes", context.getString(R.string.home_feature_notes_title), context.getString(R.string.home_feature_notes_subtitle), AppDestination.NotesHome.createRoute(familyId), Icons.AutoMirrored.Filled.Note, Color(0xFFFFF9E6), Color(0xFFF5A623), state.badgeNotes, CounterField.NOTES),
    FeatureItem("todo", context.getString(R.string.home_feature_todo_title), context.getString(R.string.home_feature_todo_subtitle), AppDestination.Todo.route, Icons.Filled.CheckCircle, Color(0xFFEBF3FF), Color(0xFF2E86FF), state.badgeTodos, CounterField.TODOS),
    FeatureItem("shopping", context.getString(R.string.home_feature_shopping_title), context.getString(R.string.home_feature_shopping_subtitle), AppDestination.ShoppingList.createRoute(familyId), Icons.Filled.LocalGroceryStore, Color(0xFFEDFAF3), Color(0xFF27AE60), state.badgeShopping, CounterField.SHOPPING),
    FeatureItem("calendar", context.getString(R.string.home_feature_calendar_title), context.getString(R.string.home_feature_calendar_subtitle), AppDestination.Calendar.createRoute(familyId), Icons.Filled.CalendarMonth, Color(0xFFF3EEFF), Color(0xFF8B5CF6), state.badgeCalendar, CounterField.CALENDAR),
    FeatureItem("health", context.getString(R.string.home_feature_health_title), context.getString(R.string.home_feature_health_subtitle), AppDestination.PediatricChildSelector.createRoute(familyId), Icons.Filled.Favorite, Color(0xFFFFEAEA), Color(0xFFE53E3E)),
    FeatureItem("chat", context.getString(R.string.home_feature_chat_title), context.getString(R.string.home_feature_chat_subtitle), AppDestination.Chat.route, Icons.AutoMirrored.Filled.Chat, Color(0xFFEDFAF3), Color(0xFF27AE60), state.badgeChat, CounterField.CHAT),
    FeatureItem("expenses", context.getString(R.string.home_feature_expenses_title), context.getString(R.string.home_feature_expenses_subtitle), AppDestination.ExpensesHome.createRoute(familyId), Icons.Filled.Euro, Color(0xFFFFF3E6), Color(0xFFFF6B00), state.badgeExpenses, CounterField.EXPENSES),
    FeatureItem("documents", context.getString(R.string.home_feature_documents_title), context.getString(R.string.home_feature_documents_subtitle), AppDestination.DocumentsHome.createRoute(familyId), Icons.Filled.Description, Color(0xFFEBF3FF), Color(0xFF2E86FF), state.badgeDocuments, CounterField.DOCUMENTS),
    FeatureItem(
        "wallet",
        context.getString(R.string.home_feature_wallet_title),
        context.getString(R.string.home_feature_wallet_subtitle),
        AppDestination.WalletHome.createRoute(familyId),
        Icons.Filled.ConfirmationNumber,
        Color(0xFFE8F4FF),
        Color(0xFF007AFF),
        state.badgeWallet,
        CounterField.WALLET,
    ),
    FeatureItem(
        "location",
        context.getString(R.string.home_feature_location_title),
        context.getString(R.string.home_feature_location_subtitle),
        AppDestination.FamilyLocation.createRoute(familyId),
        Icons.Filled.Place,
        Color(0xFFE6FAF8),
        Color(0xFF00BFA5),
        state.badgeLocation,
        CounterField.LOCATION,
        isPulsing = state.isLocationSharing,
    ),
    FeatureItem(
        "photos",
        context.getString(R.string.home_feature_photos_title),
        context.getString(R.string.home_feature_photos_subtitle),
        AppDestination.FamilyPhotos.createRoute(familyId),
        Icons.Filled.PhotoLibrary,
        Color(0xFFFFF0F5),
        Color(0xFFE91E8C),
        state.badgePhotos,
        CounterField.PHOTOS,
    ),
    FeatureItem(
        "pets",
        context.getString(R.string.home_feature_pets_title),
        context.getString(R.string.home_feature_pets_subtitle),
        AppDestination.Pets.createRoute(familyId),
        Icons.Filled.Pets,
        Color(0xFFFFF6ED),
        Color(0xFFFF9500),
    ),
    FeatureItem(
        "home_items",
        context.getString(R.string.home_feature_home_items_title),
        context.getString(R.string.home_feature_home_items_subtitle),
        AppDestination.HomeItems.createRoute(familyId),
        Icons.Filled.Home,
        Color(0xFFF8F3E6),
        Color(0xFF8B6914),
    ),
    FeatureItem(
        "vehicles",
        context.getString(R.string.home_feature_vehicles_title),
        context.getString(R.string.home_feature_vehicles_subtitle),
        AppDestination.Vehicles.createRoute(familyId),
        Icons.Filled.DirectionsCar,
        Color(0xFFF0F0F0),
        Color(0xFF1A1A1A),
    ),
    FeatureItem(
        id = "travel",
        title = context.getString(R.string.home_feature_travel_title),
        subtitle = if (state.familyPlan == KBPlan.FREE) {
            context.getString(R.string.home_feature_travel_subtitle_locked)
        } else {
            context.getString(R.string.home_feature_travel_subtitle_unlocked)
        },
        route = AppDestination.TravelList.createRoute(familyId),
        icon = if (state.familyPlan == KBPlan.FREE) Icons.Filled.Lock else Icons.Filled.Luggage,
        cardColor = Color(0xFFE0F7FA),
        iconColor = Color(0xFF00838F),
        locked = state.familyPlan == KBPlan.FREE,
    ),
    FeatureItem(
        id = "ai",
        title = if (state.familyPlan == KBPlan.FREE) {
            context.getString(R.string.home_feature_ai_title_locked)
        } else {
            context.getString(R.string.home_feature_ai_title_unlocked)
        },
        subtitle = if (state.familyPlan == KBPlan.FREE) {
            context.getString(R.string.home_feature_ai_subtitle_locked)
        } else {
            context.getString(R.string.home_feature_ai_subtitle_unlocked)
        },
        route = if (state.familyPlan == KBPlan.FREE) {
            AppDestination.Plans.route
        } else {
            AppDestination.AiChat.createRoute(familyId)
        },
        icon = if (state.familyPlan == KBPlan.FREE) Icons.Filled.Lock else Icons.Filled.Psychology,
        cardColor = Color(0xFFEEF0FF),
        iconColor = Color(0xFF5C6BC0),
        locked = state.familyPlan == KBPlan.FREE,
    ),
    FeatureItem(
        "passwords",
        context.getString(R.string.home_feature_passwords_title),
        context.getString(R.string.home_feature_passwords_subtitle),
        AppDestination.PasswordsHome.createRoute(familyId),
        Icons.Filled.Lock,
        Color(0xFFEFF6FF),
        Color(0xFF2563EB),
        state.badgePasswords,
    ),
    FeatureItem("family", context.getString(R.string.home_feature_family_title), context.getString(R.string.home_feature_family_subtitle), AppDestination.FamilySettings.route, Icons.Filled.Person, Color(0xFFFFF3E6), Color(0xFFFF6B00)),
)

// ── Variante C: scorciatoie + gruppi tematici ────────────────────────────────

@Composable
private fun HomeCategorySection(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    onFeatureOpened: (CounterField?) -> Unit,
    onRecordFeatureUsage: (String) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val features = featureItems(context, state.familyId, state)
    val byId = remember(features) { features.associateBy { it.id } }

    // Assistente ("ai") escluso: è il bottone AI flottante.
    val groups = listOf(
        stringResource(R.string.home_group_organization) to listOf("notes", "todo", "shopping", "calendar"),
        stringResource(R.string.home_group_family_health) to listOf("health", "family", "chat"),
        stringResource(R.string.home_group_documents_money) to listOf("documents", "expenses", "wallet", "passwords"),
        stringResource(R.string.home_group_daily_life) to listOf("location", "photos", "travel", "pets", "home_items", "vehicles"),
    )

    // Scorciatoie: top-4 per utilizzo su TUTTE le categorie (ordinamento stabile → a parità
    // conta l'ordine naturale di featureItems). "ai" escluso: è il FAB.
    val shortcuts = remember(features, state.featureUsage) {
        features
            .filter { it.id != "ai" }
            .sortedByDescending { state.featureUsage[it.id] ?: 0 }
            .take(4)
    }

    val openFeature: (FeatureItem) -> Unit = { feat ->
        onRecordFeatureUsage(feat.id)
        onFeatureOpened(feat.counterField)
        onNavigate(feat.route)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(stringResource(R.string.home_shortcuts_eyebrow))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            shortcuts.forEach { feat ->
                ShortcutItem(feat) { openFeature(feat) }
            }
        }

        groups.forEach { (name, ids) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow(name)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = kb.card,
                    border = BorderStroke(1.dp, kb.divider),
                ) {
                    Column {
                        val items = ids.mapNotNull { byId[it] }
                        items.forEachIndexed { index, feat ->
                            GroupRow(feat) { openFeature(feat) }
                            if (index != items.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 48.dp)
                                        .height(1.dp)
                                        .background(kb.divider),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Griglia libera con drag&drop (Home in modalità GRID) ─────────────────────
// Nessun gruppo/scorciatoia: solo le card riordinabili a piacere (tranne "ai",
// che resta il FAB). L'ordine è persistito per-device via HomeViewModePreference.

@Composable
private fun HomeFreeGridSection(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    onFeatureOpened: (CounterField?) -> Unit,
    onRecordFeatureUsage: (String) -> Unit,
    initialOrder: List<String>?,
    onOrderChanged: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val features = remember(context, state.familyId, state) {
        featureItems(context, state.familyId, state).filter { it.id != "ai" }
    }
    val byId = remember(features) { features.associateBy { it.id } }

    var orderedIds by remember(features) {
        val saved = initialOrder?.filter { byId.containsKey(it) }.orEmpty()
        val missing = features.map { it.id }.filterNot { it in saved }
        mutableStateOf(saved + missing)
    }

    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current

    val openFeature: (FeatureItem) -> Unit = { feat ->
        onRecordFeatureUsage(feat.id)
        onFeatureOpened(feat.counterField)
        onNavigate(feat.route)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        orderedIds.chunked(2).forEach { rowIds ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowIds.forEach { id ->
                    val feat = byId[id] ?: return@forEach
                    val isDragged = id == draggedId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .zIndex(if (isDragged) 20f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    shadowElevation = 12f
                                }
                            }
                            .onGloballyPositioned { coords ->
                                itemBounds[id] = coords.boundsInRoot()
                            }
                            .pointerInput(id, orderedIds) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedId = id
                                        dragOffset = Offset.Zero
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                    },
                                    onDragEnd = {
                                        val origin = itemBounds[id]
                                        if (origin != null) {
                                            val dragCenter = Offset(
                                                origin.center.x + dragOffset.x,
                                                origin.center.y + dragOffset.y,
                                            )
                                            val targetId = itemBounds.entries
                                                .minByOrNull { (_, rect) ->
                                                    (rect.center - dragCenter).getDistanceSquared()
                                                }
                                                ?.key
                                            if (targetId != null && targetId != id) {
                                                val fromIndex = orderedIds.indexOf(id)
                                                val toIndex = orderedIds.indexOf(targetId)
                                                if (fromIndex != -1 && toIndex != -1) {
                                                    val mutable = orderedIds.toMutableList()
                                                    mutable.removeAt(fromIndex)
                                                    mutable.add(toIndex, id)
                                                    orderedIds = mutable
                                                    onOrderChanged(mutable)
                                                }
                                            }
                                        }
                                        draggedId = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggedId = null
                                        dragOffset = Offset.Zero
                                    },
                                )
                            },
                    ) {
                        FeatureCard(item = feat, modifier = Modifier.fillMaxSize()) { openFeature(feat) }
                    }
                }
                if (rowIds.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.kidBoxColors.subtitle,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun ShortcutItem(item: FeatureItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(item.iconColor.copy(alpha = 0.14f))
                .border(1.dp, item.iconColor.copy(alpha = 0.24f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = item.iconColor, modifier = Modifier.size(22.dp))
        }
        Text(
            item.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.kidBoxColors.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GroupRow(item: FeatureItem, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (item.locked) Icons.Filled.Lock else item.icon,
            contentDescription = null,
            tint = if (item.locked) Color(0xFF9CA3AF) else item.iconColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            item.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = kb.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.isPulsing) {
            LocationPulseIndicator()
            Spacer(Modifier.width(8.dp))
        }
        if (item.badgeCount > 0) {
            HomeCardBadge(count = item.badgeCount)
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = kb.subtitle,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LocationPulseIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "home_location_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home_location_pulse_scale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home_location_pulse_alpha",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF34C759).copy(alpha = pulseAlpha))
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
        )
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFF30C659)),
        )
    }
}

@Composable
private fun HomeCardBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val text = if (count > 99) "99+" else count.toString()
    val isCircle = count < 10
    Surface(
        modifier = modifier,
        shape = if (isCircle) CircleShape else RoundedCornerShape(999.dp),
        color = Color(0xFFE53935),
        shadowElevation = 3.dp,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = if (isCircle) {
                Modifier.size(18.dp).wrapContentSize(Alignment.Center)
            } else {
                Modifier.height(18.dp).padding(horizontal = 7.dp).wrapContentSize(Alignment.Center)
            },
        )
    }
}

