@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.domain.model.LoyaltyCardBrand
import it.vittorioscocca.kidbox.domain.model.LoyaltyCardCatalog

private sealed interface AddLoyaltyCardStep {
    data object Picker : AddLoyaltyCardStep
    data class Scan(val brand: LoyaltyCardBrand?) : AddLoyaltyCardStep
    data class Form(val brand: LoyaltyCardBrand?, val prefilledNumber: String?, val prefilledFormat: String?) : AddLoyaltyCardStep
}

/**
 * Flusso completo "Aggiungi una carta": picker brand (ricerca + popolari +
 * indice A-Z) → scanner barcode/fallback manuale → form (numero + nota, e per
 * "carta non in elenco" anche nome negozio + colore). Mirror del flusso iOS
 * `AddLoyaltyCardBrandPickerView` → `LoyaltyCardBarcodeScannerView` →
 * `LoyaltyCardFormView`, come un unico full-screen Dialog con step interni
 * (pattern più semplice del NavGraph dedicato usato su iOS).
 */
@Composable
fun AddLoyaltyCardFlow(
    viewModel: LoyaltyCardsViewModel,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var step by remember { mutableStateOf<AddLoyaltyCardStep>(AddLoyaltyCardStep.Picker) }

    // `decorFitsSystemWindows = false` non è un dettaglio estetico: senza,
    // dentro un Dialog full-screen le WindowInsets arrivano a ZERO, quindi
    // `statusBarsPadding()`/`navigationBarsPadding()` negli step interni non
    // producono alcuno spazio e i contenuti finiscono sotto le system bar.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val s = step) {
                is AddLoyaltyCardStep.Picker -> BrandPickerStep(
                    onBrandSelected = { brand -> step = AddLoyaltyCardStep.Scan(brand) },
                    onManualEntry = { step = AddLoyaltyCardStep.Form(null, null, null) },
                    onClose = onDismiss,
                )
                is AddLoyaltyCardStep.Scan -> ScanStep(
                    brand = s.brand,
                    onBack = { step = AddLoyaltyCardStep.Picker },
                    onDetected = { text, format -> step = AddLoyaltyCardStep.Form(s.brand, text, format) },
                    onManualFallback = { step = AddLoyaltyCardStep.Form(s.brand, null, null) },
                )
                is AddLoyaltyCardStep.Form -> FormStep(
                    brand = s.brand,
                    prefilledNumber = s.prefilledNumber,
                    prefilledFormat = s.prefilledFormat,
                    viewModel = viewModel,
                    onBack = { step = if (s.brand == null) AddLoyaltyCardStep.Picker else AddLoyaltyCardStep.Scan(s.brand) },
                    onSaved = onSaved,
                )
            }
        }
    }
}

@Composable
private fun BrandPickerStep(
    onBrandSelected: (LoyaltyCardBrand) -> Unit,
    onManualEntry: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) { LoyaltyCardCatalog.search(context, query) }
    val grouped = remember(filtered) { LoyaltyCardCatalog.groupedByIndexLetter(filtered) }
    val popular = remember { LoyaltyCardCatalog.popular(context) }
    val isSearching = query.trim().isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.wallet_loyalty_picker_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.wallet_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.wallet_loyalty_search_store_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (!isSearching && popular.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.wallet_loyalty_popular_section)) }
                    items(popular, key = { "popular_${it.id}" }) { brand ->
                        BrandRow(brand, onClick = { onBrandSelected(brand) })
                    }
                }

                grouped.forEach { (letter, brands) ->
                    item(key = "header_$letter") { SectionHeader(letter) }
                    items(brands, key = { it.id }) { brand ->
                        BrandRow(brand, onClick = { onBrandSelected(brand) })
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onManualEntry)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.wallet_loyalty_manual_entry_cta),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BrandRow(brand: LoyaltyCardBrand, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Il gradiente del brand resta come sfondo/fallback: se il brand ha un
        // logo, il chip bianco lo copre; altrimenti si vede il quadrato colorato.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(parseLoyaltyCardColor(brand.primaryColorHex), parseLoyaltyCardColor(brand.secondaryColorHex)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            LoyaltyCardLogo(
                logoURL = brand.logoURL,
                brandName = brand.name,
                chipSize = 36.dp,
                logoSize = 24.dp,
                cornerRadius = 8.dp,
            )
        }
        Text(brand.name, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScanStep(
    brand: LoyaltyCardBrand?,
    onBack: () -> Unit,
    onDetected: (String, String) -> Unit,
    onManualFallback: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(brand?.name ?: stringResource(R.string.wallet_loyalty_default_brand_name), fontWeight = FontWeight.SemiBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.wallet_back), tint = Color.White)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LoyaltyCardBarcodeScanner(
                onDetect = onDetected,
                onManualFallback = onManualFallback,
            )
        }
    }
}

@Composable
private fun FormStep(
    brand: LoyaltyCardBrand?,
    prefilledNumber: String?,
    prefilledFormat: String?,
    viewModel: LoyaltyCardsViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var cardNumber by remember { mutableStateOf(prefilledNumber.orEmpty()) }
    var note by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#5856D6") }
    var isSaving by remember { mutableStateOf(false) }

    val isManualBrand = brand == null
    val canSave = cardNumber.trim().isNotEmpty() && (!isManualBrand || storeName.trim().isNotEmpty())

    val presetColors = listOf(
        "#5856D6", "#E30613", "#0058A3", "#00843D", "#F39200", "#000000", "#E4032E", "#0072BC",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(brand?.name ?: stringResource(R.string.wallet_loyalty_new_card_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.wallet_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (!canSave || isSaving) return@TextButton
                            isSaving = true
                            val resolvedName = brand?.name ?: storeName.trim()
                            val resolvedFormat = prefilledFormat ?: brand?.defaultBarcodeFormat ?: "code128"
                            val resolvedPrimary = brand?.primaryColorHex ?: selectedColorHex
                            val resolvedSecondary = brand?.secondaryColorHex ?: selectedColorHex
                            viewModel.addCard(
                                brandId = brand?.id,
                                brandName = resolvedName,
                                cardNumber = cardNumber.trim(),
                                barcodeFormat = resolvedFormat,
                                note = note.trim().ifBlank { null },
                                primaryColorHex = resolvedPrimary,
                                secondaryColorHex = resolvedSecondary,
                                logoURL = brand?.logoURL,
                                onSuccess = { cardId -> onSaved(cardId) },
                            )
                        },
                        enabled = canSave && !isSaving,
                    ) {
                        if (isSaving) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isManualBrand) {
                Text(stringResource(R.string.wallet_loyalty_store_name_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.wallet_loyalty_store_name_placeholder)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.wallet_loyalty_color_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presetColors.forEach { hex ->
                        val color = parseLoyaltyCardColor(hex)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selectedColorHex == hex) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                        }
                    }
                }
                Divider()
            }

            Text(stringResource(R.string.wallet_loyalty_card_number_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.wallet_loyalty_card_number_label)) },
                singleLine = true,
            )

            Text(stringResource(R.string.wallet_loyalty_description_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.wallet_loyalty_description_placeholder)) },
                minLines = 1,
                maxLines = 3,
            )
        }
    }
}
