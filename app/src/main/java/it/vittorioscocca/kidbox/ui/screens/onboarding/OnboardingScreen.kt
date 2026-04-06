package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFamilyCreated: (familyId: String) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    val createdFamilyId by viewModel.createdFamilyId.collectAsStateWithLifecycle()
    val isCreatingFamily by viewModel.isCreatingFamily.collectAsStateWithLifecycle()
    val createFamilyError by viewModel.createFamilyError.collectAsStateWithLifecycle()

    val currentPage = pagerState.currentPage
    val accent = pageAccent(currentPage)
    val iconTint = pageIconTint(currentPage)

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
                when (page) {
                    in 0..2 -> IntroPageContent(slide = introSlides[page])
                    3 -> CreateFamilyPageContent(
                        isCreating = isCreatingFamily,
                        errorText = createFamilyError,
                        familyCreated = createdFamilyId != null,
                        onClearError = viewModel::clearCreateError,
                        onCreateFamily = { fam, child, birth ->
                            viewModel.createFamily(fam, child, birth)
                        },
                    )
                    else -> InvitePartnerPageContent(
                        familyId = createdFamilyId.orEmpty(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                PageIndicators(
                    pageCount = 5,
                    currentPage = currentPage,
                    accent = accent,
                )
            }

            val ctaEnabled = when (currentPage) {
                3 -> createdFamilyId != null
                4 -> createdFamilyId != null
                else -> true
            }

            MainCtaButton(
                currentPage = currentPage,
                accent = accent,
                iconTint = iconTint,
                enabled = ctaEnabled,
                onClick = {
                    when (currentPage) {
                        3 -> {
                            if (createdFamilyId != null) {
                                scope.launch { pagerState.animateScrollToPage(4) }
                            }
                        }
                        4 -> createdFamilyId?.let { onFamilyCreated(it) }
                        else -> scope.launch {
                            pagerState.animateScrollToPage(currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 40.dp),
            )
        }
    }
}

private fun pageAccent(page: Int): Color = when (page) {
    1 -> PurpleAccent
    2 -> GreenAccent
    else -> OrangeAccent
}

private fun pageIconTint(page: Int): Color = when (page) {
    1 -> Color(0xFF9B7BC9)
    2 -> Color(0xFF4CAF74)
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
    val inviteSnippet = if (familyId.isNotEmpty()) {
        "Unisciti alla mia famiglia su KidBox — codice: $familyId"
    } else {
        "Unisciti alla mia famiglia su KidBox"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))
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
            fontSize = 32.sp,
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
                .size(240.dp)
                .shadow(12.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE8E8E8)),
                contentAlignment = Alignment.Center,
            ) {
                Text("QR", color = GrayCaption, fontSize = 14.sp)
            }
        }
        Text(
            "Valido 24 ore",
            fontSize = 13.sp,
            color = GrayCaption,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedSoftButton(
                modifier = Modifier.weight(1f),
                containerColor = OrangeAccent.copy(alpha = 0.12f),
                contentColor = OrangeAccent,
                icon = Icons.Filled.Share,
                label = "Condividi",
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, inviteSnippet)
                    }
                    context.startActivity(Intent.createChooser(send, "Condividi"))
                },
            )
            OutlinedSoftButton(
                modifier = Modifier.weight(1f),
                containerColor = Color(0xFFECECEC),
                contentColor = GraySubtitle,
                icon = Icons.Filled.ContentCopy,
                label = "Copia codice",
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("kidbox_invite", familyId.ifEmpty { inviteSnippet }))
                },
            )
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
    currentPage: Int,
    accent: Color,
    iconTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (currentPage == 4) "Inizia" else "Continua"
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
