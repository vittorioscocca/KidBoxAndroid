@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.paymentcards

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBPaymentCardEntity
import it.vittorioscocca.kidbox.data.repository.LoyaltyCardPhotoSide
import it.vittorioscocca.kidbox.data.repository.PaymentCardPlain
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.components.KBEmptyState
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerFullscreenDialog
import it.vittorioscocca.kidbox.ui.screens.wallet.documents.rememberWalletDocumentScannerLauncher
import it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards.LoyaltyCardPhotoSlot
import it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards.LoyaltyCardPhotoViewer
import it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards.parseLoyaltyCardColor
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsFeature
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsOrigin

// MARK: - Carta disegnata

/**
 * La carta come una carta vera: nome e circuito in alto, numero mascherato,
 * intestatario e scadenza in basso. Il numero intero non compare mai qui.
 * Mirror di `PaymentCardTileView` (iOS).
 */
@Composable
fun PaymentCardTile(
    plain: PaymentCardPlain,
    colorHex: String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val base = parseLoyaltyCardColor(colorHex)
    val otherName = stringResource(R.string.wallet_payment_network_other)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = base, spotColor = base)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.78f)))),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    // Il circuito sta già a destra: senza nome non si ripete.
                    when {
                        plain.isUnreadable -> stringResource(R.string.wallet_payment_unreadable)
                        plain.label.isNotEmpty() -> plain.label
                        plain.network.displayName == null -> otherName
                        else -> ""
                    },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                plain.network.displayName?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
            Spacer(Modifier.weight(1f))
            if (plain.cardNumber.isNotEmpty()) {
                Text(
                    PaymentCardFormat.masked(plain.cardNumber),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    plain.holderName.uppercase(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (plain.expiry.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (PaymentCardFormat.isExpired(plain.expiry)) {
                            Text(
                                stringResource(R.string.wallet_payment_expired),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.Red.copy(alpha = 0.85f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(plain.expiry, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// MARK: - Sezione «Pagamento»

@Composable
fun PaymentCardsSectionContent(
    familyId: String,
    onCardClick: (String) -> Unit,
    onAdd: () -> Unit,
    viewModel: PaymentCardsViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(familyId) { viewModel.bind(familyId) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.forceRefresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KBEmptyState(
                    icon = Icons.Filled.CreditCard,
                    title = stringResource(R.string.wallet_payment_empty_title),
                    body = stringResource(R.string.wallet_payment_empty_body),
                    primaryIcon = Icons.Filled.AddCircle,
                    primaryLabel = stringResource(R.string.wallet_payment_add),
                    onPrimary = onAdd,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items, key = { it.entity.id }) { item ->
                    PaymentCardTile(
                        plain = item.plain,
                        colorHex = item.entity.colorHex,
                        modifier = Modifier.clickable { onCardClick(item.entity.id) },
                    )
                }
            }
        }
    }
}

// MARK: - Form (nuova / modifica)

/**
 * Inserimento solo a mano: niente AI, niente scansione del numero. Prende il
 * posto della schermata (come il form delle carte fedeltà) invece di aprirsi
 * in un Dialog, così tastiera e barre hanno gli inset veri.
 */
@Composable
fun PaymentCardFormScreen(
    cardId: String?,
    viewModel: PaymentCardsViewModel,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val existing = cardId?.let { id -> state.items.firstOrNull { it.entity.id == id } }

    SecureWindow()
    BackHandler { onDismiss() }

    var label by rememberSaveable { mutableStateOf(existing?.plain?.label.orEmpty()) }
    // Numero, IBAN e PIN con `remember`, non `rememberSaveable`: lo stato
    // salvato può finire su disco alla morte del processo.
    var number by remember { mutableStateOf(PaymentCardFormat.grouped(existing?.plain?.cardNumber.orEmpty())) }
    var holder by rememberSaveable { mutableStateOf(existing?.plain?.holderName.orEmpty()) }
    var expiry by rememberSaveable { mutableStateOf(existing?.plain?.expiry.orEmpty()) }
    var iban by remember { mutableStateOf(PaymentCardFormat.ibanGrouped(existing?.plain?.iban.orEmpty())) }
    var notes by rememberSaveable { mutableStateOf(existing?.plain?.notes.orEmpty()) }
    var pin by remember { mutableStateOf(existing?.plain?.pin.orEmpty()) }
    var colorHex by rememberSaveable { mutableStateOf(existing?.entity?.colorHex ?: PaymentCardPalette.DEFAULT_HEX) }
    var scope by rememberSaveable {
        mutableStateOf(KBVisibilityScope.normalizedWallet(existing?.entity?.visibilityScope))
    }
    var memberIds by remember {
        mutableStateOf(
            existing?.entity?.visibilityMemberIdsJson
                ?.let { it.removePrefix("[").removeSuffix("]").split(",").map { s -> s.trim().trim('"') }.filter { s -> s.isNotEmpty() } }
                ?: emptyList(),
        )
    }
    var showVisibility by remember { mutableStateOf(false) }

    val digits = PaymentCardFormat.digits(number)
    val canSave = digits.length >= 12 && !state.isSaving

    if (showVisibility) {
        VisibilityPickerFullscreenDialog(
            currentUid = FirebaseAuth.getInstance().currentUser?.uid,
            scopeSectionTitle = stringResource(R.string.wallet_payment_visibility_who),
            membersExcludingSelf = state.visibilityMembers,
            initialScope = scope,
            initialMemberIds = memberIds,
            onDismiss = { showVisibility = false },
            onConfirmed = { s, ids ->
                scope = s
                memberIds = ids
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.kidBoxColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(if (cardId == null) R.string.wallet_payment_new_title else R.string.wallet_payment_edit_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.wallet_cancel)) }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            viewModel.save(
                                cardId = cardId,
                                plain = PaymentCardPlain(
                                    label = label,
                                    cardNumber = digits,
                                    holderName = holder,
                                    iban = iban,
                                    expiry = expiry,
                                    notes = notes,
                                    pin = pin,
                                ),
                                colorHex = colorHex,
                                visibilityScope = scope,
                                visibilityMemberIds = memberIds,
                                onSuccess = onSaved,
                            )
                        },
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.wallet_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PaymentCardTile(
                plain = PaymentCardPlain(label = label.trim(), cardNumber = digits, holderName = holder.trim(), expiry = expiry),
                colorHex = colorHex,
                height = 190.dp,
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.wallet_payment_label_label)) },
                placeholder = { Text(stringResource(R.string.wallet_payment_label_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = number,
                onValueChange = { number = PaymentCardFormat.grouped(it) },
                label = { Text(stringResource(R.string.wallet_payment_number_label)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = digits.length >= 12 && !PaymentCardFormat.passesLuhn(digits),
                supportingText = if (digits.length >= 12 && !PaymentCardFormat.passesLuhn(digits)) {
                    { Text(stringResource(R.string.wallet_payment_number_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = holder,
                onValueChange = { holder = it },
                label = { Text(stringResource(R.string.wallet_payment_holder_label)) },
                placeholder = { Text(stringResource(R.string.wallet_payment_holder_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = expiry,
                onValueChange = { expiry = PaymentCardFormat.expiryInput(it) },
                label = { Text(stringResource(R.string.wallet_payment_expiry_label)) },
                placeholder = { Text("MM/AA") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = expiry.length == 5 && !PaymentCardFormat.isValidExpiry(expiry),
                supportingText = if (expiry.length == 5 && !PaymentCardFormat.isValidExpiry(expiry)) {
                    { Text(stringResource(R.string.wallet_payment_expiry_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val ibanInvalid = PaymentCardFormat.ibanCompact(iban).length >= 15 && !PaymentCardFormat.isValidIban(iban)
            OutlinedTextField(
                value = iban,
                onValueChange = { iban = PaymentCardFormat.ibanGrouped(it) },
                label = { Text(stringResource(R.string.wallet_payment_iban_label)) },
                placeholder = { Text("IT00 X000 0000 0000 0000 0000 000") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                isError = ibanInvalid,
                supportingText = if (ibanInvalid) {
                    { Text(stringResource(R.string.wallet_payment_iban_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text(stringResource(R.string.wallet_payment_pin_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                supportingText = { Text(stringResource(R.string.wallet_payment_pin_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.wallet_payment_color_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.kidBoxColors.subtitle,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaymentCardPalette.all.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseLoyaltyCardColor(hex))
                            .border(1.dp, MaterialTheme.kidBoxColors.title.copy(alpha = 0.15f), CircleShape)
                            .clickable { colorHex = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hex == colorHex) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
                modifier = Modifier.fillMaxWidth().clickable { showVisibility = true },
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.wallet_payment_visibility_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.kidBoxColors.subtitle,
                        )
                        Text(
                            stringResource(KBVisibilityScope.chipLabelRes(scope)),
                            color = MaterialTheme.kidBoxColors.title,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.kidBoxColors.subtitle)
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.wallet_payment_notes_label)) },
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.wallet_payment_security_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.kidBoxColors.subtitle,
            )

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// MARK: - Dettaglio

/**
 * Numero intero e PIN si vedono o si copiano solo dopo impronta/volto o codice, come le
 * password, e torna nascosto quando l'app va in background. La finestra è
 * `FLAG_SECURE`: niente screenshot né anteprima nelle app recenti.
 */
@Composable
fun PaymentCardDetailScreen(
    familyId: String,
    cardId: String,
    onBack: () -> Unit,
    viewModel: PaymentCardsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.entity.id == cardId }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var isUnlocked by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var fullScreenPhoto by remember { mutableStateOf<Bitmap?>(null) }

    SecureWindow()
    LaunchedEffect(familyId) { viewModel.bind(familyId) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) isUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(item?.entity?.id) {
        val c = item?.entity ?: return@LaunchedEffect
        KBAnalytics.logRetrieval(
            feature = KBAnalyticsFeature.WALLET,
            uploaderUid = c.createdBy,
            createdAtEpochMillis = c.createdAtEpochMillis,
            entryPoint = KBAnalyticsOrigin.consume(),
        )
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (c.createdBy.isNotBlank() && c.createdBy != uid) AppAnalytics.contentSharedRead(context, "payment_card")
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissMessage()
    }

    if (showEdit && item != null) {
        PaymentCardFormScreen(
            cardId = cardId,
            viewModel = viewModel,
            onDismiss = { showEdit = false },
            onSaved = { showEdit = false },
        )
        return
    }

    BackHandler { onBack() }

    fullScreenPhoto?.let { LoyaltyCardPhotoViewer(bitmap = it, onDismiss = { fullScreenPhoto = null }) }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.wallet_payment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.wallet_payment_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteCard(cardId)
                    onBack()
                }) { Text(stringResource(R.string.wallet_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.wallet_cancel)) }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.kidBoxColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wallet_payment_detail_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.wallet_back))
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.wallet_payment_edit_title))
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.wallet_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text(stringResource(R.string.wallet_payment_not_found), color = MaterialTheme.kidBoxColors.title)
            }
            return@Scaffold
        }
        val plain = item.plain
        val card = item.entity

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PaymentCardTile(plain = plain, colorHex = card.colorHex, height = 210.dp)

            if (plain.isUnreadable) {
                Text(stringResource(R.string.wallet_payment_unreadable_body), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                PaymentCardFieldsCard(
                    plain = plain,
                    isUnlocked = isUnlocked,
                    onToggleReveal = {
                        if (isUnlocked) isUnlocked = false else authenticateThen(context) { isUnlocked = true }
                    },
                    onCopySecret = { secret ->
                        val copySecret = {
                            isUnlocked = true
                            copySensitive(context, secret)
                        }
                        if (isUnlocked) copySecret() else authenticateThen(context, copySecret)
                    },
                    onCopy = { value -> copySensitive(context, value) },
                )
            }

            PaymentCardPhotosCard(
                card = card,
                photos = state.photos,
                busySide = state.busyPhotoSide,
                onRequestLoad = viewModel::loadCardPhoto,
                onCaptured = { side, bmp -> viewModel.setCardPhoto(cardId, side, bmp) },
                onRemove = { side -> viewModel.removeCardPhoto(cardId, side) },
                onOpenFullScreen = { fullScreenPhoto = it },
            )

            if (plain.notes.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.wallet_payment_note_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.kidBoxColors.subtitle)
                        Spacer(Modifier.height(6.dp))
                        Text(plain.notes, color = MaterialTheme.kidBoxColors.title)
                    }
                }
            }

            OutlinedButton(
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.wallet_payment_delete_button))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PaymentCardFieldsCard(
    plain: PaymentCardPlain,
    isUnlocked: Boolean,
    onToggleReveal: () -> Unit,
    /** Numero e PIN: si copiano solo dopo lo sblocco. */
    onCopySecret: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (plain.cardNumber.isNotEmpty()) {
                FieldRow(
                    title = stringResource(R.string.wallet_payment_number_label),
                    value = if (isUnlocked) PaymentCardFormat.grouped(plain.cardNumber) else PaymentCardFormat.masked(plain.cardNumber),
                    monospaced = true,
                ) {
                    IconButton(onClick = onToggleReveal) {
                        Icon(
                            if (isUnlocked) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (isUnlocked) R.string.wallet_payment_hide_cd else R.string.wallet_payment_show_cd),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onCopySecret(plain.cardNumber) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.wallet_payment_copy_cd), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (plain.pin.isNotEmpty()) {
                FieldRow(
                    title = stringResource(R.string.wallet_payment_pin_title),
                    value = if (isUnlocked) plain.pin else "••••",
                    monospaced = true,
                ) {
                    IconButton(onClick = onToggleReveal) {
                        Icon(
                            if (isUnlocked) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (isUnlocked) R.string.wallet_payment_hide_cd else R.string.wallet_payment_show_cd),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onCopySecret(plain.pin) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.wallet_payment_copy_cd), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (plain.holderName.isNotEmpty()) {
                FieldRow(stringResource(R.string.wallet_payment_holder_label), plain.holderName, monospaced = false) {
                    IconButton(onClick = { onCopy(plain.holderName) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.wallet_payment_copy_cd), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (plain.expiry.isNotEmpty()) {
                FieldRow(stringResource(R.string.wallet_payment_expiry_label), plain.expiry, monospaced = true) {
                    if (PaymentCardFormat.isExpired(plain.expiry)) {
                        Text(stringResource(R.string.wallet_payment_expired), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (plain.iban.isNotEmpty()) {
                FieldRow("IBAN", PaymentCardFormat.ibanGrouped(plain.iban), monospaced = true) {
                    IconButton(onClick = { onCopy(plain.iban) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.wallet_payment_copy_cd), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    title: String,
    value: String,
    monospaced: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.kidBoxColors.subtitle)
            Text(
                value,
                color = MaterialTheme.kidBoxColors.title,
                fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.Default,
                maxLines = 2,
            )
        }
        trailing()
    }
}

@Composable
private fun PaymentCardPhotosCard(
    card: KBPaymentCardEntity,
    photos: Map<String, Bitmap>,
    busySide: LoyaltyCardPhotoSide?,
    onRequestLoad: (String?) -> Unit,
    onCaptured: (LoyaltyCardPhotoSide, Bitmap) -> Unit,
    onRemove: (LoyaltyCardPhotoSide) -> Unit,
    onOpenFullScreen: (Bitmap) -> Unit,
) {
    var pendingSide by remember { mutableStateOf<LoyaltyCardPhotoSide?>(null) }
    val launchScanner = rememberWalletDocumentScannerLauncher(
        pageLimit = 1,
        onResult = { result ->
            val side = pendingSide
            val page = result.pages.firstOrNull()
            if (side != null && page != null) onCaptured(side, page)
            pendingSide = null
        },
        onCancelledOrFailed = { pendingSide = null },
    )

    LaunchedEffect(card.frontPhotoStoragePath, card.backPhotoStoragePath) {
        onRequestLoad(card.frontPhotoStoragePath)
        onRequestLoad(card.backPhotoStoragePath)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.wallet_payment_photos_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.kidBoxColors.subtitle)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple(LoyaltyCardPhotoSide.FRONT, card.frontPhotoStoragePath, R.string.wallet_loyalty_photo_front),
                    Triple(LoyaltyCardPhotoSide.BACK, card.backPhotoStoragePath, R.string.wallet_loyalty_photo_back),
                ).forEach { (side, path, labelRes) ->
                    LoyaltyCardPhotoSlot(
                        modifier = Modifier.weight(1f),
                        label = stringResource(labelRes),
                        bitmap = path?.let { photos[it] },
                        hasPhoto = !path.isNullOrBlank(),
                        isBusy = busySide == side,
                        onCapture = {
                            pendingSide = side
                            launchScanner()
                        },
                        onRemove = { onRemove(side) },
                        onOpenFullScreen = onOpenFullScreen,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.wallet_payment_photos_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kidBoxColors.subtitle)
        }
    }
}

// MARK: - Sicurezza

/** `FLAG_SECURE` finché la schermata è visibile: niente screenshot né anteprima nelle recenti. */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Biometria O credenziale del dispositivo, come per le password. Senza nessun
 * metodo di sblocco configurato non c'è barriera da opporre: si procede.
 */
private fun authenticateThen(context: Context, onSuccess: () -> Unit) {
    val activity = context.findActivity() as? FragmentActivity
    val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (activity == null || BiometricManager.from(activity).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
        onSuccess()
        return
    }
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    ).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.wallet_payment_biometric_title))
            .setSubtitle(activity.getString(R.string.wallet_payment_biometric_subtitle))
            .setAllowedAuthenticators(allowed)
            .build(),
    )
}

/**
 * Copia segnata come sensibile (Android 13+ non la mostra nell'anteprima
 * degli appunti) e svuotata dopo 60 secondi se nel frattempo non è cambiata.
 */
private fun copySensitive(context: Context, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("KidBox", value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    } else {
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    clipboard.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (current == value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
        }
    }, 60_000L)
}
