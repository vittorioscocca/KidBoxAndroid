package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import it.vittorioscocca.kidbox.ui.screens.settings.CornerBrackets
import it.vittorioscocca.kidbox.ui.screens.settings.JoinFamilyViewModel
import it.vittorioscocca.kidbox.ui.screens.settings.QRScannerView
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

private data class IntroSlide(
    val icon: ImageVector,
    val iconTint: Color,
    val accent: Color,
    val glow: Color,
    val title: String,
    val subtitle: String,
)

private val introSlides = listOf(
    IntroSlide(
        icon = Icons.Filled.Favorite,
        iconTint = Color(0xFFFFBF40),
        accent = OrangeAccent,
        glow = OrangeAccent.copy(alpha = 0.35f),
        title = "La tua famiglia,\nin un'unica app.",
        subtitle = "Tutto quello che riguarda i tuoi figli — organizzato, condiviso e sempre a portata di mano.",
    ),
    IntroSlide(
        icon = Icons.Filled.PhotoLibrary,
        iconTint = Color(0xFF9B7BC9),
        accent = PurpleAccent,
        glow = PurpleAccent.copy(alpha = 0.35f),
        title = "Ricordi condivisi\ncon il tuo partner.",
        subtitle = "Foto, video e momenti speciali in una galleria privata, cifrata e sincronizzata in tempo reale.",
    ),
    IntroSlide(
        icon = Icons.Filled.MedicalServices,
        iconTint = Color(0xFF4CAF74),
        accent = GreenAccent,
        glow = GreenAccent.copy(alpha = 0.35f),
        title = "Salute, spese\ne molto altro.",
        subtitle = "Visite mediche, vaccini, spese di famiglia e lista della spesa. Tutto aggiornato tra voi due.",
    ),
)

private enum class FamilyPath { Create, Join }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFamilyCreated: (familyId: String) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var familyPath by remember { mutableStateOf<FamilyPath?>(null) }

    val pagerState = rememberPagerState(
        pageCount = { if (familyPath == FamilyPath.Join) 5 else 6 },
    )
    val scope = rememberCoroutineScope()

    val createdFamilyId by viewModel.createdFamilyId.collectAsStateWithLifecycle()
    val isCreatingFamily by viewModel.isCreatingFamily.collectAsStateWithLifecycle()
    val createFamilyError by viewModel.createFamilyError.collectAsStateWithLifecycle()

    val currentPage = pagerState.currentPage
    val accent = pageAccent(currentPage, familyPath)
    val iconTint = pageIconTint(currentPage, familyPath)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBeige),
    ) {
        TopAccentGradient(accent = accent, pageIndex = currentPage)

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = false,
            ) { page ->
                when {
                    page in 0..2 -> IntroPageContent(slide = introSlides[page])
                    page == 3 -> FamilyPathPickerPageContent(
                        selected = familyPath,
                        onSelect = { familyPath = it },
                    )
                    page == 4 && familyPath == FamilyPath.Create -> CreateFamilyPageContent(
                        isCreating = isCreatingFamily,
                        errorText = createFamilyError,
                        familyCreated = createdFamilyId != null,
                        onClearError = viewModel::clearCreateError,
                        onCreateFamily = { fam, child, birth ->
                            viewModel.createFamily(fam, child, birth)
                        },
                    )
                    page == 4 && familyPath == FamilyPath.Join -> JoinFamilyPageContent(
                        onJoined = { familyId -> onFamilyCreated(familyId) },
                    )
                    // Fallback: familyPath == null (non dovrebbe succedere, il CTA pag.3 è disabled)
                    page == 4 -> FamilyPathPickerPageContent(
                        selected = familyPath,
                        onSelect = { familyPath = it },
                    )
                    else -> InvitePartnerPageContent(
                        familyId = createdFamilyId.orEmpty(),
                    )
                }
            }

            val totalPages = if (familyPath == FamilyPath.Join) 5 else 6

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

            val isJoinPage = currentPage == 4 && familyPath == FamilyPath.Join
            val ctaEnabled = when (currentPage) {
                3 -> familyPath != null
                4 -> when (familyPath) {
                    FamilyPath.Create -> createdFamilyId != null
                    FamilyPath.Join -> false
                    null -> false
                }
                5 -> createdFamilyId != null
                else -> true
            }
            val ctaLabel = if (currentPage == totalPages - 1) "Inizia" else "Continua"

            if (isJoinPage) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 28.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                )
            } else {
                MainCtaButton(
                    label = ctaLabel,
                    accent = accent,
                    iconTint = iconTint,
                    enabled = ctaEnabled,
                    onClick = {
                        when (currentPage) {
                            3 -> if (familyPath != null) {
                                scope.launch { pagerState.animateScrollToPage(4) }
                            }
                            4 -> when (familyPath) {
                                FamilyPath.Create -> if (createdFamilyId != null) {
                                    scope.launch { pagerState.animateScrollToPage(5) }
                                }
                                FamilyPath.Join -> Unit
                                null -> Unit
                            }
                            5 -> createdFamilyId?.let { onFamilyCreated(it) }
                            else -> scope.launch {
                                pagerState.animateScrollToPage(currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                )
            }
        }
    }
}

private fun pageAccent(page: Int, familyPath: FamilyPath?): Color = when (page) {
    1 -> PurpleAccent
    2 -> GreenAccent
    4 -> if (familyPath == FamilyPath.Join) PurpleAccent else OrangeAccent
    else -> OrangeAccent
}

private fun pageIconTint(page: Int, familyPath: FamilyPath?): Color = when (page) {
    1 -> Color(0xFF9B7BC9)
    2 -> Color(0xFF4CAF74)
    4 -> if (familyPath == FamilyPath.Join) Color(0xFF9B7BC9) else Color(0xFFFFBF40)
    else -> Color(0xFFFFBF40)
}

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
private fun IntroPageContent(slide: IntroSlide) {
    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(24f) }
    LaunchedEffect(slide.title) {
        textAlpha.snapTo(0f)
        textOffset.snapTo(24f)
        coroutineScope {
            launch { textAlpha.animateTo(1f, tween(450)) }
            launch { textOffset.animateTo(0f, tween(450)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        IconHeroCard(
            icon = slide.icon,
            iconTint = slide.iconTint,
            accent = slide.accent,
            glow = slide.glow,
        )
        Spacer(modifier = Modifier.height(40.dp))
        AnimatedContent(
            targetState = slide.title to slide.subtitle,
            transitionSpec = {
                (fadeIn(tween(280, delayMillis = 60)) +
                    slideInVertically(tween(380)) { it / 10 })
                    .togetherWith(
                        fadeOut(tween(120)) +
                            slideOutVertically(tween(200)) { -it / 12 },
                    )
            },
            label = "intro_text",
        ) { (title, subtitle) ->
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = textAlpha.value
                        translationY = textOffset.value
                    }
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlackText,
                    textAlign = TextAlign.Center,
                    lineHeight = 38.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = subtitle,
                    fontSize = 17.sp,
                    color = GraySubtitle,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFamilyPageContent(
    isCreating: Boolean,
    errorText: String?,
    familyCreated: Boolean,
    onClearError: () -> Unit,
    onCreateFamily: (familyName: String, childName: String, birthMillis: Long?) -> Unit,
) {
    var familyName by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var birthMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val canCreate = familyName.isNotBlank() && childName.isNotBlank() && !isCreating && !familyCreated

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
                Icons.Filled.Home,
                contentDescription = null,
                tint = OrangeAccent,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Crea la tua famiglia",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = BlackText,
            textAlign = TextAlign.Center,
        )
        Text(
            "Dai un nome alla famiglia e aggiungi il primo figlio. Potrai modificare tutto in seguito.",
            fontSize = 15.sp,
            color = GraySubtitle,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))

        FormFieldLabel("Nome famiglia")
        OutlinedTextField(
            value = familyName,
            onValueChange = {
                familyName = it
                onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !familyCreated,
            singleLine = true,
            placeholder = { Text("Es. Famiglia Rossi", color = GrayCaption) },
            leadingIcon = {
                Text("👨‍👩‍👧", fontSize = 20.sp)
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrangeAccent,
                unfocusedBorderColor = GrayFieldBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))

        FormFieldLabel("Primo figlio")
        OutlinedTextField(
            value = childName,
            onValueChange = {
                childName = it
                onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !familyCreated,
            singleLine = true,
            placeholder = { Text("Nome del bambino/a", color = GrayCaption) },
            leadingIcon = {
                Text("🚶", fontSize = 20.sp)
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrangeAccent,
                unfocusedBorderColor = GrayFieldBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GrayFieldBorder, RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable(enabled = !familyCreated) { showDatePicker = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = OrangeAccent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = birthMillis?.let { formatBirthLabel(it) }
                    ?: "Data di nascita (opzionale)",
                color = if (birthMillis != null) BlackText else GrayCaption,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (showDatePicker) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = GrayCaption,
                modifier = Modifier.size(20.dp),
            )
        }

        if (showDatePicker && !familyCreated) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { birthMillis = it }
                            showDatePicker = false
                        },
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (familyCreated) {
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
                    "Famiglia creata! Continua per invitare il partner.",
                    color = SuccessGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Button(
                onClick = {
                    onCreateFamily(familyName, childName, birthMillis)
                },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeAccent,
                    disabledContainerColor = GrayDisabled.copy(alpha = 0.4f),
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Crea famiglia", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        errorText?.takeIf { it.isNotBlank() }?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            "Potrai creare la famiglia anche dopo da Impostazioni",
            fontSize = 12.sp,
            color = GrayCaption.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
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

private fun formatBirthLabel(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.ITALY)
    return sdf.format(java.util.Date(millis))
}

@Composable
private fun InvitePartnerPageContent(familyId: String) {
    val context = LocalContext.current
    val viewModel: InviteCodeViewModel = hiltViewModel()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val qrPayload by viewModel.qrPayload.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    var didCopy by remember { mutableStateOf(false) }

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
                Icons.Filled.People,
                contentDescription = null,
                tint = OrangeAccent,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Aggiungi il tuo partner",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = BlackText,
            textAlign = TextAlign.Center,
        )
        Text(
            "Fallo scansionare al tuo partner per unirsi alla famiglia — riceverà tutto automaticamente.",
            fontSize = 17.sp,
            color = GraySubtitle,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = OrangeAccent.copy(alpha = 0.12f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isBusy -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = OrangeAccent)
                        Text("Generazione QR…", color = GraySubtitle, fontSize = 14.sp)
                    }
                }
                qrPayload != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QRCodeView(
                            payload = qrPayload.orEmpty(),
                            modifier = Modifier.size(156.dp),
                        )
                        Text("Valido 24 ore", fontSize = 13.sp, color = GrayCaption)
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = errorMessage.orEmpty(),
                            color = GraySubtitle,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = viewModel::generateInviteCode) {
                            Text("Riprova", color = OrangeAccent)
                        }
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.height(180.dp))
                }
            }
        }

        if (qrPayload != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedSoftButton(
                    modifier = Modifier.weight(1f),
                    containerColor = OrangeAccent.copy(alpha = 0.10f),
                    contentColor = OrangeAccent,
                    icon = Icons.Filled.Share,
                    label = "Condividi",
                    onClick = {
                        val shareText = "KidBox — codice invito: ${code.orEmpty()}"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, "Condividi"))
                    },
                )
                OutlinedSoftButton(
                    modifier = Modifier.weight(1f),
                    containerColor = GraySubtitle.copy(alpha = 0.08f),
                    contentColor = if (didCopy) SuccessGreen else GraySubtitle,
                    icon = if (didCopy) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                    label = if (didCopy) "Copiato!" else "Copia codice",
                    onClick = {
                        val value = code.orEmpty()
                        if (value.isNotBlank()) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("kidbox_invite_code", value))
                            didCopy = true
                        }
                    },
                )
            }
            LaunchedEffect(didCopy) {
                if (didCopy) {
                    kotlinx.coroutines.delay(2000)
                    didCopy = false
                }
            }
        }
        Text(
            "Puoi farlo anche dopo da Impostazioni → Invita partner",
            fontSize = 12.sp,
            color = GrayCaption,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
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

@Composable
private fun FamilyPathPickerPageContent(
    selected: FamilyPath?,
    onSelect: (FamilyPath) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Come vuoi iniziare?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = BlackText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Scegli se creare una nuova famiglia o unirti a una esistente.",
            fontSize = 15.sp,
            color = GraySubtitle,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))

        PathOptionCard(
            path = FamilyPath.Create,
            isSelected = selected == FamilyPath.Create,
            icon = Icons.Filled.Home,
            accent = OrangeAccent,
            title = "Crea la tua famiglia",
            subtitle = "Sarai il creatore e potrai invitare il tuo partner.",
            onSelect = onSelect,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PathOptionCard(
            path = FamilyPath.Join,
            isSelected = selected == FamilyPath.Join,
            icon = Icons.Filled.QrCodeScanner,
            accent = PurpleAccent,
            title = "Entra in una famiglia",
            subtitle = "Hai un codice QR o un codice testuale? Usalo per unirti.",
            onSelect = onSelect,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PathOptionCard(
    path: FamilyPath,
    isSelected: Boolean,
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onSelect: (FamilyPath) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) accent.copy(alpha = 0.08f) else Color.White)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accent else GrayFieldBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onSelect(path) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (isSelected) 0.20f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BlackText)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 13.sp, color = GraySubtitle, lineHeight = 18.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = if (isSelected) accent else GrayCaption.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp),
        )
    }
}

// MARK: - Pagina 4 (percorso Join): codice + scansiona QR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinFamilyPageContent(
    onJoined: (familyId: String) -> Unit,
) {
    val viewModel: JoinFamilyViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
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
            "Entra nella famiglia",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = BlackText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Inserisci il codice invito ricevuto oppure scansiona il QR code.",
            fontSize = 15.sp,
            color = GraySubtitle,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))

        FormFieldLabel("Codice invito")
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase(Locale.ROOT) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.didJoin,
            singleLine = true,
            placeholder = { Text("Es. K7P4D2", color = GrayCaption) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PurpleAccent,
                unfocusedBorderColor = GrayFieldBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))

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
                "Scansiona QR code",
                color = PurpleAccent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (code.isNotBlank()) {
                    viewModel.joinWithCode(code) {
                        // Il LaunchedEffect sopra osserva didJoin + joinedFamilyId.
                    }
                }
            },
            enabled = !state.isBusy && !state.didJoin && code.isNotBlank(),
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
            if (state.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Entra", fontWeight = FontWeight.SemiBold)
            }
        }

        state.error?.takeIf { it.isNotBlank() }?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

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
                    "Sei entrato nella famiglia!",
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
                    Icon(Icons.Filled.Close, contentDescription = "Chiudi", tint = Color.White)
                }
                Text(
                    "Inquadra il QR code",
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
