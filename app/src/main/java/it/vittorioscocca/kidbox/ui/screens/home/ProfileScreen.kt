package it.vittorioscocca.kidbox.ui.screens.home

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.fixBitmapOrientationFromBytes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.permissions.LocationDisclosureDialog
import it.vittorioscocca.kidbox.ui.screens.settings.toStorageString
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.ui.util.rememberSingleImagePicker
import it.vittorioscocca.kidbox.ui.util.singleImageRequest

private val AccentOrange = Color(0xFFF2611A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    onBack: (() -> Unit)? = null,
    onStorageUsage: () -> Unit = {},
    onOpenPlans: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestLocationPermission by viewModel.requestLocationPermission.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showRemoveAvatarDialog by remember { mutableStateOf(false) }
    var showDeleteSheet by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val kb = MaterialTheme.kidBoxColors

    // I pulsanti di abbonamento restano visibili a tutti: chi non gestisce
    // l'abbonamento riceve la spiegazione al tocco, non un pulsante mancante.
    var mostraAvvisoCreatore by remember { mutableStateOf(false) }
    if (mostraAvvisoCreatore) {
        AlertDialog(
            onDismissRequest = { mostraAvvisoCreatore = false },
            title = { Text(stringResource(R.string.subscription_owner_managed_title)) },
            text = { Text(stringResource(R.string.subscription_owner_managed_body)) },
            confirmButton = {
                TextButton(onClick = { mostraAvvisoCreatore = false }) {
                    Text(stringResource(R.string.subscription_ok))
                }
            },
        )
    }

    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    // URI of the image the user picked from the gallery — drives the crop overlay
    var avatarCropUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberSingleImagePicker { uri ->
        if (uri != null) avatarCropUri = uri
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onLocationPermissionResult(granted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenVisible()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Prominent disclosure obbligatoria (Google Play User Data policy): il permesso di
    // sistema può partire solo dopo che l'utente ha letto a cosa serve la posizione.
    var showLocationDisclosure by remember { mutableStateOf(false) }

    if (requestLocationPermission) {
        DisposableEffect(Unit) {
            showLocationDisclosure = true
            viewModel.consumeLocationPermissionRequest()
            onDispose { }
        }
    }

    LocationDisclosureDialog(
        visible = showLocationDisclosure,
        purpose = stringResource(R.string.home_profile_location_disclosure_purpose),
        onAccept = {
            showLocationDisclosure = false
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onDecline = { showLocationDisclosure = false },
    )

    if (showDeleteSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDeleteSheet = false },
            sheetState = deleteSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.home_profile_delete_account_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.kidBoxColors.title,
                )
                Text(
                    stringResource(R.string.home_profile_delete_account_confirm_hint),
                    color = kb.subtitle,
                )
                OutlinedTextField(
                    value = deleteConfirmText,
                    onValueChange = { deleteConfirmText = it },
                    label = { Text(stringResource(R.string.home_profile_delete_type_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = kb.card,
                        unfocusedContainerColor = kb.card,
                        focusedTextColor = kb.title,
                        unfocusedTextColor = kb.title,
                        focusedLabelColor = kb.subtitle,
                        unfocusedLabelColor = kb.subtitle,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { showDeleteSheet = false }) { Text(stringResource(R.string.home_profile_cancel)) }
                    Button(
                        onClick = { viewModel.deleteAccount(onLoggedOut) },
                        enabled = deleteConfirmText.trim().uppercase() == "ELIMINA" && !state.isDeleting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    ) {
                        if (state.isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.home_profile_delete_confirm))
                        }
                    }
                }
            }
        }
    }

    if (showRemoveAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveAvatarDialog = false },
            title = { Text(stringResource(R.string.home_profile_remove_photo_title)) },
            text = { Text(stringResource(R.string.home_profile_remove_photo_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveAvatarDialog = false
                    viewModel.removeAvatar()
                }) {
                    Text(
                        stringResource(R.string.home_profile_remove_photo),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAvatarDialog = false }) {
                    Text(stringResource(R.string.location_cancel_button))
                }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.home_profile_logout_title)) },
            text = { Text(stringResource(R.string.home_profile_logout_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout(onLoggedOut)
                    },
                ) { Text(stringResource(R.string.home_profile_logout_confirm), color = Color(0xFFD32F2F)) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(R.string.home_profile_cancel)) } },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(6.dp))

        if (onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(kb.card)
                        .clickable { onBack.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = kb.title,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            stringResource(R.string.home_profile_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp, lineHeight = 46.sp),
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentOrange)
            }
            return@Column
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = kb.card),
            shape = RoundedCornerShape(20.dp),
            elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 1.5.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .border(
                                    width = 2.4.dp,
                                    brush = Brush.linearGradient(listOf(AccentOrange.copy(alpha = .7f), AccentOrange.copy(alpha = .2f))),
                                    shape = CircleShape,
                                )
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(kb.rowBackground)
                                .shadow(8.dp, CircleShape),
                        ) {
                            when {
                                state.pickedAvatar != null -> {
                                    val rawBytes = state.pickedAvatar ?: byteArrayOf()
                                    val bitmap = BitmapFactory.decodeByteArray(
                                        rawBytes,
                                        0,
                                        rawBytes.size,
                                    )?.let { fixBitmapOrientationFromBytes(it, rawBytes) }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = AccentOrange.copy(alpha = 0.6f),
                                            modifier = Modifier.size(40.dp).align(Alignment.Center),
                                        )
                                    }
                                }
                                !state.avatarUrl.isNullOrBlank() -> {
                                    AsyncImage(
                                        model = state.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = AccentOrange.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp).align(Alignment.Center),
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(AccentOrange)
                                .clickable {
                                    photoPickerLauncher.launch(singleImageRequest())
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }

                // Compare solo se c'è davvero una foto da togliere: offrire
                // "Rimuovi" su un avatar già vuoto sarebbe un comando inerte.
                if (state.pickedAvatar != null || !state.avatarUrl.isNullOrBlank()) {
                    TextButton(
                        onClick = { showRemoveAvatarDialog = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            stringResource(R.string.home_profile_remove_photo),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                HorizontalDivider(color = kb.divider)
                ProfileInput(stringResource(R.string.home_profile_name_placeholder), state.firstName, viewModel::setFirstName)
                ProfileInput(stringResource(R.string.home_profile_surname_placeholder), state.lastName, viewModel::setLastName)

                if (state.isDirty) {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(stringResource(R.string.home_profile_save_changes), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        SectionCard(
            icon = Icons.Default.Home,
            iconColor = AccentOrange,
            title = stringResource(R.string.home_profile_address_section_title),
        ) {
            OutlinedTextField(
                value = state.addressQuery,
                onValueChange = viewModel::setAddressQuery,
                placeholder = { Text(stringResource(R.string.home_profile_address_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = kb.rowBackground,
                    unfocusedContainerColor = kb.rowBackground,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = kb.title,
                    unfocusedTextColor = kb.title,
                    focusedPlaceholderColor = kb.subtitle,
                    unfocusedPlaceholderColor = kb.subtitle,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            HorizontalDivider(color = kb.divider)

            if (state.addressSuggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(kb.rowBackground),
                ) {
                    state.addressSuggestions.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectAddressSuggestion(item) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    item.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = kb.title,
                                )
                                Text(item.subtitle, color = kb.subtitle, fontSize = 12.sp)
                            }
                        }
                        if (index < state.addressSuggestions.lastIndex) HorizontalDivider(color = kb.divider)
                    }
                }
            }

            TextButton(
                onClick = viewModel::requestCurrentLocation,
                enabled = !state.isResolvingLocation,
            ) {
                Text(
                    stringResource(
                        if (state.isResolvingLocation) R.string.home_profile_use_current_location_loading
                        else R.string.home_profile_use_current_location,
                    ),
                    color = AccentOrange,
                )
            }
        }

        SectionCard(icon = Icons.Default.Email, iconColor = Color(0xFF2F80ED), title = stringResource(R.string.home_profile_account_section_title)) {
            InfoRow(Icons.Default.Email, Color(0xFF5EA8E2), stringResource(R.string.home_profile_email_label), state.email.ifBlank { stringResource(R.string.home_profile_placeholder_dash) })
            HorizontalDivider(color = kb.divider)
            InfoRow(Icons.Default.Update, Color(0xFF67B96D), stringResource(R.string.home_profile_last_login_label), "13 Apr 2026 at 9:17")
        }

        SectionCard(icon = Icons.Default.Star, iconColor = Color(0xFF3DA668), title = stringResource(R.string.home_profile_subscription_section_title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(kb.rowBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(state.planLabel, fontWeight = FontWeight.SemiBold, color = kb.title)
                Spacer(Modifier.weight(1f))
                // Allineato a iOS: nessun upgrade da proporre se il piano è già il
                // massimo. Il pulsante c'è per tutti: a chi non gestisce
                // l'abbonamento risponde con il motivo, non con l'assenza.
                if (state.plan != KBPlan.MAX) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.kidBoxColors.rowBackground)
                            .clickable {
                                if (state.isFamilyOwner) onOpenPlans() else mostraAvvisoCreatore = true
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.home_profile_upgrade), color = Color(0xFF2F80ED), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            val progress = (state.storageUsedBytes.toFloat() / state.storageTotalBytes.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                color = Color(0xFF2F80ED),
                trackColor = kb.divider,
            )
            Text(
                stringResource(
                    R.string.home_profile_storage_used_of_total,
                    state.storageUsedBytes.toStorageString(),
                    state.storageTotalBytes.toStorageString(),
                ),
                fontSize = 12.sp,
                color = kb.subtitle,
            )
            HorizontalDivider(color = kb.divider, modifier = Modifier.padding(top = 2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable(onClick = onStorageUsage),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.home_profile_manage_storage_plans), color = kb.title)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = kb.subtitle)
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = kb.card),
            shape = RoundedCornerShape(20.dp),
        ) {
            ActionRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                tint = AccentOrange,
                label = stringResource(R.string.home_profile_logout_action),
                onClick = { showLogoutConfirm = true },
            )
            HorizontalDivider(color = kb.divider, modifier = Modifier.padding(start = 56.dp))
            ActionRow(
                icon = Icons.Default.Delete,
                tint = Color(0xFFD32F2F),
                label = stringResource(R.string.home_profile_delete_account_action),
                onClick = {
                    deleteConfirmText = ""
                    showDeleteSheet = true
                },
            )
        }

        state.saveError?.let {
            Text(it, color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
        }
        if (state.saveSucceeded && !state.isDirty) {
            Text(stringResource(R.string.home_profile_saved), color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(28.dp))
    }

    // ── Avatar cropper overlay ────────────────────────────────────────────────
    // Shown on top of the profile screen whenever the user picks a new photo.
    // It covers the entire screen (dark background + fillMaxSize), so no
    // additional scrim is needed.
    avatarCropUri?.let { uri ->
        AvatarCropperScreen(
            imageUri = uri,
            onCancel = { avatarCropUri = null },
            onSave = { croppedBytes ->
                viewModel.onAvatarPicked(croppedBytes)
                avatarCropUri = null
            },
        )
    }

    } // end Box
}

@Composable
private fun ProfileInput(label: String, value: String, onChange: (String) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        placeholder = { Text(label, color = kb.subtitle) },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = kb.subtitle) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = kb.rowBackground,
            unfocusedContainerColor = kb.rowBackground,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = kb.title,
            unfocusedTextColor = kb.title,
        ),
    )
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = kb.card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle)
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, label: String, value: String) {
    val kb = MaterialTheme.kidBoxColors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = kb.subtitle)
            Text(value, fontSize = 14.sp, color = kb.title)
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (tint == Color(0xFFD32F2F)) tint else kb.title,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = kb.subtitle)
    }
}
