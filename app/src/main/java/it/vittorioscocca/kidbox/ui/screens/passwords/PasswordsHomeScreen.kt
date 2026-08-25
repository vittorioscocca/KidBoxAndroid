@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.passwords

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.launch
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsEntryPoint
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsOrigin
import it.vittorioscocca.kidbox.ui.components.KBEmptyState
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Lock

/** Tint allineato a iOS `KBTheme` per chip attivi e FAB password. */
private val PasswordsAccentPurple = Color(0xFF9973D9)

/** Blu del pulsante «Nuova Password» dell'empty state (KBEmptyState ACCENT): il «+» usa lo stesso. */
private val PasswordsAddButtonBlue = Color(0xFF007AFF)

/** FAB 56dp + margine: senza questo il + copre l’ultima riga in fondo alla lista. */
private val PasswordsHomeListFabClearance = 72.dp

@Composable
fun PasswordsHomeScreen(
    familyId: String,
    onBack: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onAddPassword: (() -> Unit)? = null,
    onManageGroups: () -> Unit = {},
    onOpenPassword: ((String) -> Unit)? = null,
    viewModel: PasswordsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addComingSoonMessage = stringResource(R.string.passwords_add_coming_soon_snackbar)
    val kb = MaterialTheme.kidBoxColors
    val isLight = !isSystemInDarkTheme()
    val pageBg = kb.background
    val surfaceCard = if (isLight) Color.White else kb.card
    val chipBorder = if (isLight) Color(0xFFE5E5EA) else kb.divider
    val rowDivider = if (isLight) Color(0xFFE5E5EA) else kb.divider

    LaunchedEffect(familyId) {
        viewModel.bind(familyId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = surfaceCard,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.location_back_content_description),
                            tint = kb.title,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = surfaceCard,
                    shadowElevation = 1.dp,
                    onClick = { viewModel.setSelecting(!state.isSelecting) },
                ) {
                    Text(
                        if (state.isSelecting) stringResource(R.string.location_cancel_button) else stringResource(R.string.passwords_select_button),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.title,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = surfaceCard,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(40.dp),
                ) {
                    IconButton(
                        onClick = onOpenSecurity,
                        modifier = Modifier.size(40.dp),
                    ) {
                        BadgedBox(
                            badge = {
                                if (state.securityIssuesCount > 0) {
                                    Badge { Text(if (state.securityIssuesCount > 99) "99+" else state.securityIssuesCount.toString()) }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.VerifiedUser,
                                contentDescription = stringResource(R.string.passwords_security_section_title),
                                tint = kb.title,
                            )
                        }
                    }
                }
                Box {
                    Surface(
                        shape = CircleShape,
                        color = surfaceCard,
                        shadowElevation = 1.dp,
                        modifier = Modifier.size(40.dp),
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.passwords_menu_content_description), tint = kb.title)
                        }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.passwords_manage_groups_title)) },
                            onClick = {
                                showMenu = false
                                onManageGroups()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.passwords_import_export_menu_label)) },
                            onClick = {
                                showMenu = false
                                onOpenImportExport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.passwords_settings_menu_label)) },
                            onClick = {
                                showMenu = false
                                onOpenSettings()
                            },
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.passwords_password_placeholder),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                ),
                color = kb.title,
            )

            // Senza nemmeno una password i filtri non filtrano nulla: nella schermata
            // iniziale restano solo testo, icona e pulsante.
            if (state.hasAnyPassword) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PasswordFilterChip(
                    title = stringResource(R.string.passwords_filter_all),
                    selected = state.filter is PasswordHomeFilter.All,
                    surfaceCard = surfaceCard,
                    chipBorder = chipBorder,
                    onClick = { viewModel.setFilter(PasswordHomeFilter.All) },
                )
                PasswordFilterChip(
                    title = stringResource(R.string.passwords_filter_favorites),
                    selected = state.filter is PasswordHomeFilter.Favorites,
                    surfaceCard = surfaceCard,
                    chipBorder = chipBorder,
                    onClick = { viewModel.setFilter(PasswordHomeFilter.Favorites) },
                )
                PasswordFilterChip(
                    title = stringResource(R.string.passwords_filter_family),
                    selected = state.filter is PasswordHomeFilter.FamilyShared,
                    surfaceCard = surfaceCard,
                    chipBorder = chipBorder,
                    onClick = { viewModel.setFilter(PasswordHomeFilter.FamilyShared) },
                )
                PasswordFilterChip(
                    title = stringResource(R.string.passwords_filter_only_me),
                    selected = state.filter is PasswordHomeFilter.OnlyMineVisibility,
                    surfaceCard = surfaceCard,
                    chipBorder = chipBorder,
                    onClick = { viewModel.setFilter(PasswordHomeFilter.OnlyMineVisibility) },
                )
                for (chip in state.groupChips) {
                    val selected = when (val f = state.filter) {
                        is PasswordHomeFilter.ByGroup -> f.groupId == chip.id
                        else -> false
                    }
                    PasswordFilterChip(
                        title = chip.label,
                        selected = selected,
                        surfaceCard = surfaceCard,
                        chipBorder = chipBorder,
                        onClick = { viewModel.setFilter(PasswordHomeFilter.ByGroup(chip.id)) },
                    )
                }
            }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val hasNoRows = state.sections.isEmpty() ||
                    state.sections.all { it.rows.isEmpty() }
                when {
                    hasNoRows -> PasswordsEmptyState(
                        filter = state.filter,
                        searchQuery = state.searchQuery,
                        kb = kb,
                        onAddPassword = { onAddPassword?.invoke() },
                    )
                    else -> {
                        val listBottomExtra = if (state.isSelecting) 0.dp else PasswordsHomeListFabClearance
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp + listBottomExtra,
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(state.sections, key = { it.id }) { section ->
                                PasswordHomeSection(
                                    section = section,
                                    surfaceCard = surfaceCard,
                                    rowDivider = rowDivider,
                                    kb = kb,
                                    isSelecting = state.isSelecting,
                                    selectedIds = state.selectedIds,
                                    onToggleSelected = viewModel::toggleSelected,
                                    onToggleSectionExpanded = viewModel::toggleSectionExpanded,
                                    // Distingue "trovato cercando" da "trovato
                                    // sfogliando": è la sola differenza che dice
                                    // se il contenuto è davvero a portata di click.
                                    onOpenPassword = onOpenPassword?.let { open ->
                                        { id: String ->
                                            KBAnalyticsOrigin.set(
                                                if (state.searchQuery.isBlank()) {
                                                    KBAnalyticsEntryPoint.LIST
                                                } else {
                                                    KBAnalyticsEntryPoint.SEARCH
                                                }
                                            )
                                            open(id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (state.isSelecting && state.selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceCard)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(
                            stringResource(R.string.passwords_delete_count_button, state.selectedIds.size),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(24.dp),
                color = surfaceCard,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(22.dp),
                        tint = kb.subtitle,
                    )
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = kb.title,
                            fontSize = 16.sp,
                            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                innerTextField()
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        stringResource(R.string.passwords_search_placeholder),
                                        modifier = Modifier.align(Alignment.CenterStart),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = kb.subtitle,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        if (!state.isSelecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 88.dp)
                    .navigationBarsPadding(),
            ) {
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(10.dp, CircleShape, ambientColor = PasswordsAddButtonBlue.copy(alpha = 0.35f)),
                    shape = CircleShape,
                    color = PasswordsAddButtonBlue,
                    onClick = {
                        val add = onAddPassword
                        if (add != null) {
                            add()
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(addComingSoonMessage)
                            }
                        }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.passwords_add_password_content_description),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.passwords_delete_confirm_title, state.selectedIds.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.softDeleteSelected(familyId)
                    },
                ) { Text(stringResource(R.string.location_delete_content_description)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.location_cancel_button)) }
            },
        )
    }
}

@Composable
private fun PasswordHomeSection(
    section: PasswordSectionUi,
    surfaceCard: Color,
    rowDivider: Color,
    kb: KidBoxColorScheme,
    isSelecting: Boolean,
    selectedIds: Set<String>,
    onToggleSelected: (String) -> Unit,
    onToggleSectionExpanded: (String) -> Unit,
    onOpenPassword: ((String) -> Unit)?,
) {
    val rowClickEnabled = isSelecting || onOpenPassword != null
    val headerTint = parseHexColor(section.headerColorHex)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleSectionExpanded(section.id) }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = groupHeaderIconVector(section.headerIconRaw),
                contentDescription = null,
                tint = headerTint,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(headerTint, CircleShape),
            )
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = kb.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${section.rows.size}",
                style = MaterialTheme.typography.labelMedium,
                color = kb.subtitle,
            )
            Icon(
                imageVector = if (section.isExpanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight
                },
                contentDescription = if (section.isExpanded) stringResource(R.string.passwords_collapse_group_content_description) else stringResource(R.string.passwords_expand_group_content_description),
                tint = kb.subtitle,
                modifier = Modifier.size(20.dp),
            )
        }
        if (section.isExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = surfaceCard,
                shadowElevation = 1.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    section.rows.forEachIndexed { index, row ->
                        val isLast = index == section.rows.lastIndex
                        PasswordSectionRow(
                            row = row,
                            isSelecting = isSelecting,
                            isSelected = selectedIds.contains(row.id),
                            kb = kb,
                            showDividerBelow = !isLast,
                            rowDivider = rowDivider,
                            rowClickEnabled = rowClickEnabled,
                            onClick = {
                                when {
                                    isSelecting -> onToggleSelected(row.id)
                                    onOpenPassword != null -> onOpenPassword.invoke(row.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordSectionRow(
    row: PasswordRowUi,
    isSelecting: Boolean,
    isSelected: Boolean,
    kb: KidBoxColorScheme,
    showDividerBelow: Boolean,
    rowDivider: Color,
    rowClickEnabled: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = rowClickEnabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelecting) {
                Text(
                    if (isSelected) "☑" else "☐",
                    modifier = Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            PasswordEntryLeadingIcon(
                iconURL = row.iconURL,
                title = row.title,
                placeholderBg = kb.subtitle.copy(alpha = 0.12f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = kb.title,
                )
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = kb.subtitle,
                )
            }
            if (row.hasWarning) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(20.dp)
                        .background(Color(0xFFFF3B30), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "!",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = kb.subtitle.copy(alpha = 0.45f),
                modifier = Modifier.size(22.dp),
            )
        }
        if (showDividerBelow) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                color = rowDivider,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun PasswordEntryLeadingIcon(
    iconURL: String?,
    title: String,
    placeholderBg: Color,
) {
    val squareShape = RoundedCornerShape(8.dp)
    val iconMod = Modifier.size(40.dp).clip(squareShape)
    val initialsMod = Modifier.size(40.dp).clip(CircleShape)
    val initials = title
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { title.trim().take(1).uppercase().ifEmpty { "?" } }
    if (!iconURL.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = iconURL,
            contentDescription = null,
            modifier = iconMod,
            contentScale = ContentScale.Crop,
            loading = {
                Surface(
                    modifier = initialsMod,
                    shape = CircleShape,
                    color = placeholderBg,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            initials,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.kidBoxColors.subtitle,
                        )
                    }
                }
            },
            error = {
                Surface(
                    modifier = initialsMod,
                    shape = CircleShape,
                    color = placeholderBg,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            initials,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.kidBoxColors.subtitle,
                        )
                    }
                }
            },
        )
    } else {
        Surface(
            modifier = initialsMod,
            shape = CircleShape,
            color = placeholderBg,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.kidBoxColors.subtitle,
                )
            }
        }
    }
}

private fun groupHeaderIconVector(raw: String): ImageVector {
    val s = raw.lowercase()
    return when {
        "briefcase" in s -> Icons.Outlined.WorkOutline
        "person" in s -> Icons.Outlined.PersonOutline
        "bubble" in s || "message" in s -> Icons.Outlined.ChatBubbleOutline
        "creditcard" in s || "card" in s -> Icons.Outlined.CreditCard
        "house" in s || "home" in s -> Icons.Outlined.Home
        "tray" in s || "inbox" in s -> Icons.Outlined.Inbox
        else -> Icons.Outlined.Folder
    }
}

private fun parseHexColor(hex: String): Color {
    val h = hex.trim().removePrefix("#")
    return runCatching {
        when (h.length) {
            6 -> {
                val rgb = h.toLong(16).toInt() and 0xFFFFFF
                Color(0xFF000000.toInt() or rgb)
            }
            8 -> Color(h.toLong(16).toInt())
            else -> Color(0xFF5E5CE6)
        }
    }.getOrElse { Color(0xFF5E5CE6) }
}

@Composable
private fun PasswordFilterChip(
    title: String,
    selected: Boolean,
    surfaceCard: Color,
    chipBorder: Color,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) PasswordsAccentPurple.copy(alpha = 0.18f) else surfaceCard,
        border = if (selected) null else BorderStroke(1.dp, chipBorder),
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = kb.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PasswordsEmptyState(
    filter: PasswordHomeFilter,
    searchQuery: String,
    kb: KidBoxColorScheme,
    onAddPassword: () -> Unit,
) {
    // Solo la sezione davvero vuota merita il messaggio di benvenuto col
    // pulsante: filtri e ricerca senza risultati sono un'altra cosa e tengono
    // il messaggio breve di prima.
    if (searchQuery.isBlank() && filter !is PasswordHomeFilter.Favorites &&
        filter !is PasswordHomeFilter.FamilyShared && filter !is PasswordHomeFilter.OnlyMineVisibility
    ) {
        KBEmptyState(
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Filled.Lock,
            title = stringResource(R.string.empty_passwords_title),
            body = stringResource(R.string.empty_passwords_body),
            primaryIcon = Icons.Filled.AddCircle,
            primaryLabel = stringResource(R.string.empty_passwords_action),
            onPrimary = onAddPassword,
        )
        return
    }
    val title = when {
        searchQuery.isNotBlank() -> stringResource(R.string.passwords_empty_no_results)
        filter is PasswordHomeFilter.Favorites -> stringResource(R.string.passwords_empty_no_favorites)
        filter is PasswordHomeFilter.FamilyShared -> stringResource(R.string.passwords_empty_no_family_shared)
        filter is PasswordHomeFilter.OnlyMineVisibility -> stringResource(R.string.passwords_empty_no_only_mine)
        else -> stringResource(R.string.passwords_empty_no_passwords)
    }
    val subtitle = when {
        searchQuery.isNotBlank() ->
            stringResource(R.string.passwords_empty_try_other_keywords)
        filter is PasswordHomeFilter.Favorites ->
            stringResource(R.string.passwords_empty_favorites_hint)
        filter is PasswordHomeFilter.FamilyShared ->
            stringResource(R.string.passwords_empty_family_hint)
        filter is PasswordHomeFilter.OnlyMineVisibility ->
            stringResource(R.string.passwords_empty_only_mine_hint)
        else ->
            stringResource(R.string.passwords_empty_add_hint)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Key,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = kb.subtitle.copy(alpha = 0.55f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = kb.title,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = kb.subtitle,
        )
    }
}
