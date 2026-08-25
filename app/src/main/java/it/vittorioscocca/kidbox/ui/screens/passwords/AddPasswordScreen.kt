@file:OptIn(ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.passwords

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.feature.passwords.PasswordGenerator
import it.vittorioscocca.kidbox.feature.passwords.PasswordGeneratorOptions
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrength
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrengthLevel
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog

private val PasswordsAccentPurple = Color(0xFF9973D9)

@Composable
fun AddPasswordScreen(
    familyId: String,
    editingEntryId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddPasswordViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId, editingEntryId) {
        viewModel.bind(familyId, editingEntryId)
    }

    val editDraft by viewModel.editDraft.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    /** Dopo "Genera password" la password è mostrata in chiaro. */
    var showPasswordPlain by remember { mutableStateOf(false) }
    var website by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var visibilityScope by remember { mutableStateOf(KBVisibilityScope.FAMILY) }
    var visibilityMemberIds by remember { mutableStateOf(setOf<String>()) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var hasExpiry by remember { mutableStateOf(false) }
    var expiryMillis by remember {
        mutableStateOf(
            Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis,
        )
    }
    var genExpanded by remember { mutableStateOf(false) }
    var genOptions by remember { mutableStateOf(PasswordGeneratorOptions()) }

    val isEditMode = editingEntryId != null

    LaunchedEffect(editingEntryId) {
        if (editingEntryId == null) {
            title = ""
            username = ""
            password = ""
            showPasswordPlain = false
            website = ""
            notes = ""
            visibilityScope = KBVisibilityScope.FAMILY
            visibilityMemberIds = emptySet()
            selectedGroupId = null
            hasExpiry = false
            expiryMillis = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
        }
    }

    LaunchedEffect(editDraft?.entryId) {
        val d = editDraft ?: return@LaunchedEffect
        title = d.title
        username = d.username
        password = d.password
        showPasswordPlain = false
        website = d.website
        notes = d.notes
        visibilityScope = d.visibilityScope
        visibilityMemberIds = d.visibilityMemberIds
        selectedGroupId = d.selectedGroupId
        hasExpiry = d.hasExpiry
        expiryMillis = d.expiryMillis
    }

    var showVisibilitySheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    var saving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val kb = MaterialTheme.kidBoxColors
    val pickerMembers by viewModel.pickerMembers.collectAsStateWithLifecycle()
    val pickerGroups by viewModel.pickerGroups.collectAsStateWithLifecycle()

    val strength = remember(password) { PasswordStrength.evaluate(password) }
    val canSave = title.isNotBlank() && password.isNotEmpty()

    val pageBg = kb.background
    val cardBg = kb.card
    val sectionMuted = kb.subtitle

    fun submit() {
        if (saving || !canSave) return
        saving = true
        viewModel.save(
            familyId = familyId,
            editingEntryId = editingEntryId,
            title = title,
            username = username,
            password = password,
            website = website,
            notes = notes,
            visibilityScope = visibilityScope,
            visibilityMemberIds = visibilityMemberIds.toList(),
            selectedGroupId = selectedGroupId,
            hasExpiry = hasExpiry,
            expiresAtEpochMillis = if (hasExpiry) expiryMillis else null,
            onDone = {
                saving = false
                onSaved()
            },
            onError = { msg ->
                saving = false
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
        )
    }

    val borderlessFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
    )

    Scaffold(
        containerColor = pageBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isEditMode) stringResource(R.string.passwords_edit_title) else stringResource(R.string.passwords_add_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = kb.title,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !saving) {
                        Text(stringResource(R.string.location_cancel_button), color = PasswordsAccentPurple)
                    }
                },
                actions = {
                    TextButton(onClick = { submit() }, enabled = !saving && canSave) {
                        Text(
                            stringResource(R.string.location_save_content_description),
                            fontWeight = FontWeight.SemiBold,
                            color = if (canSave) PasswordsAccentPurple else kb.subtitle,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = kb.background),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            PasswordIosSectionLabel(stringResource(R.string.passwords_visibility_section_label), sectionMuted)
            IosFormCard(cardBg) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !saving) { showVisibilitySheet = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        KBVisibilityScope.chipLabel(visibilityScope),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = kb.title,
                    )
                    IconArrow()
                }
            }

            PasswordIosSectionLabel(stringResource(R.string.passwords_credentials_section_label), sectionMuted)
            IosFormCard(cardBg) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.passwords_title_placeholder), color = kb.subtitle) },
                    singleLine = true,
                    enabled = !saving,
                    colors = borderlessFieldColors,
                )
                HorizontalDivider(color = kb.divider.copy(alpha = 0.5f))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.passwords_username_placeholder), color = kb.subtitle) },
                    singleLine = true,
                    enabled = !saving,
                    colors = borderlessFieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                )
                HorizontalDivider(color = kb.divider.copy(alpha = 0.5f))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (it.isEmpty()) showPasswordPlain = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.passwords_password_placeholder), color = kb.subtitle) },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = if (showPasswordPlain) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = borderlessFieldColors,
                )
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { strength.fillFraction.coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = strengthColor(strength.level),
                        trackColor = kb.divider.copy(alpha = 0.4f),
                    )
                    Text(
                        strength.level.labelIt(),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = strengthColor(strength.level),
                    )
                }
                HorizontalDivider(color = kb.divider.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !saving) { genExpanded = !genExpanded }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.passwords_generator_label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                    IconArrow()
                }
                if (genExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.passwords_generator_length_label, genOptions.length), style = MaterialTheme.typography.bodyMedium, color = kb.title)
                            Row {
                                TextButton(onClick = { genOptions = genOptions.copy(length = (genOptions.length - 1).coerceAtLeast(8)) }) { Text("−") }
                                TextButton(onClick = { genOptions = genOptions.copy(length = (genOptions.length + 1).coerceAtMost(64)) }) { Text("+") }
                            }
                        }
                        PasswordGenToggle(stringResource(R.string.passwords_generator_uppercase), genOptions.includeUppercase) { v ->
                            genOptions = genOptions.copy(includeUppercase = v)
                        }
                        PasswordGenToggle(stringResource(R.string.passwords_generator_lowercase), genOptions.includeLowercase) { v ->
                            genOptions = genOptions.copy(includeLowercase = v)
                        }
                        PasswordGenToggle(stringResource(R.string.passwords_generator_numbers), genOptions.includeNumbers) { v ->
                            genOptions = genOptions.copy(includeNumbers = v)
                        }
                        PasswordGenToggle(stringResource(R.string.passwords_generator_symbols), genOptions.includeSymbols) { v ->
                            genOptions = genOptions.copy(includeSymbols = v)
                        }
                        PasswordGenToggle(stringResource(R.string.passwords_generator_exclude_ambiguous), genOptions.excludeAmbiguous) { v ->
                            genOptions = genOptions.copy(excludeAmbiguous = v)
                        }
                        TextButton(
                            onClick = {
                                password = PasswordGenerator.generate(genOptions)
                                showPasswordPlain = true
                            },
                            enabled = !saving,
                        ) {
                            Text(stringResource(R.string.passwords_generate_button), color = PasswordsAccentPurple, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            PasswordIosSectionLabel(stringResource(R.string.passwords_website_section_label), sectionMuted)
            IosFormCard(cardBg) {
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.passwords_website_placeholder), color = kb.subtitle) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    colors = borderlessFieldColors,
                )
            }

            PasswordIosSectionLabel(stringResource(R.string.passwords_group_section_label), sectionMuted)
            IosFormCard(cardBg) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !saving) { showGroupSheet = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        groupLabel(selectedGroupId, pickerGroups, familyId),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = kb.title,
                    )
                    IconArrow()
                }
            }

            PasswordIosSectionLabel(stringResource(R.string.passwords_notes_section_label), sectionMuted)
            IosFormCard(cardBg) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .height(120.dp),
                    placeholder = { Text(stringResource(R.string.passwords_notes_placeholder), color = kb.subtitle) },
                    minLines = 3,
                    enabled = !saving,
                    colors = borderlessFieldColors,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            IosFormCard(cardBg) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.passwords_expiry_date_label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                    Switch(
                        checked = hasExpiry,
                        onCheckedChange = { v ->
                            hasExpiry = v
                            if (v && expiryMillis <= 0L) {
                                expiryMillis = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
                            }
                        },
                        enabled = !saving,
                    )
                }
                if (hasExpiry) {
                    HorizontalDivider(color = kb.divider.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !saving) { showDatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.passwords_expires_on_label), style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            DateFormat.getDateInstance(DateFormat.MEDIUM, KBLocale.current()).format(expiryMillis),
                            style = MaterialTheme.typography.bodyLarge,
                            color = kb.title,
                        )
                        IconArrow()
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showVisibilitySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showVisibilitySheet = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.passwords_visibility_sheet_title),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                VisibilityRadioRow(
                    label = KBVisibilityScope.chipLabel(KBVisibilityScope.FAMILY),
                    selected = visibilityScope == KBVisibilityScope.FAMILY,
                ) {
                    visibilityScope = KBVisibilityScope.FAMILY
                    visibilityMemberIds = emptySet()
                }
                VisibilityRadioRow(
                    label = KBVisibilityScope.chipLabel(KBVisibilityScope.MEMBERS),
                    selected = visibilityScope == KBVisibilityScope.MEMBERS,
                ) {
                    visibilityScope = KBVisibilityScope.MEMBERS
                }
                VisibilityRadioRow(
                    label = KBVisibilityScope.chipLabel(KBVisibilityScope.ONLY_CREATOR),
                    selected = visibilityScope == KBVisibilityScope.ONLY_CREATOR,
                ) {
                    visibilityScope = KBVisibilityScope.ONLY_CREATOR
                    visibilityMemberIds = emptySet()
                }
                if (visibilityScope == KBVisibilityScope.MEMBERS) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.location_members_label), style = MaterialTheme.typography.labelLarge, color = kb.subtitle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    pickerMembers.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    visibilityMemberIds = visibilityMemberIds.toMutableSet().apply {
                                        if (!add(m.userId)) remove(m.userId)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = visibilityMemberIds.contains(m.userId), onCheckedChange = null)
                            Text(m.label, style = MaterialTheme.typography.bodyLarge, color = kb.title)
                        }
                    }
                }
                TextButton(
                    onClick = { showVisibilitySheet = false },
                    modifier = Modifier.align(Alignment.End).padding(16.dp),
                ) { Text(stringResource(R.string.location_done_button), color = PasswordsAccentPurple, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showGroupSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showGroupSheet = false },
            sheetState = sheetState,
        ) {
            LazyColumn(Modifier.padding(bottom = 32.dp)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedGroupId = null
                                showGroupSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedGroupId == null, onClick = null)
                        Icon(
                            imageVector = passwordGroupIcon("tray"),
                            contentDescription = null,
                            tint = parsePasswordGroupColor("#8E8E93"),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.passwords_unassigned_group_label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                items(pickerGroups, key = { it.id }) { g ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedGroupId = g.id
                                showGroupSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedGroupId == g.id, onClick = null)
                        Icon(
                            imageVector = passwordGroupIcon(g.icon),
                            contentDescription = null,
                            tint = parsePasswordGroupColor(g.colorHex),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(g.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                // Creare il gruppo senza uscire dal form: prima, se mancava, si
                // doveva abbandonare la compilazione e ricominciare da capo.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showGroupSheet = false
                                newGroupName = ""
                                showNewGroupDialog = true
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.passwords_new_group_action),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (showNewGroupDialog) {
        AlertDialog(
            onDismissRequest = { showNewGroupDialog = false },
            title = { Text(stringResource(R.string.passwords_new_group_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.passwords_new_group_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newGroupName.isNotBlank(),
                    onClick = {
                        val name = newGroupName
                        showNewGroupDialog = false
                        scope.launch {
                            // Il gruppo appena creato viene anche selezionato:
                            // averlo creato da qui significa volerlo usare adesso.
                            viewModel.createGroup(name)?.let { selectedGroupId = it }
                        }
                    },
                ) { Text(stringResource(R.string.passwords_ok_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewGroupDialog = false }) {
                    Text(stringResource(R.string.location_cancel_button))
                }
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = expiryMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { expiryMillis = it }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.passwords_ok_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.location_cancel_button)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun PasswordIosSectionLabel(text: String, color: Color) {
    if (text.isEmpty()) return
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun IosFormCard(cardBg: Color, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun IconArrow() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.kidBoxColors.subtitle,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun VisibilityRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PasswordGenToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kidBoxColors.title)
    }
}

private fun strengthColor(level: PasswordStrengthLevel): Color = when (level) {
    PasswordStrengthLevel.VERY_WEAK, PasswordStrengthLevel.WEAK -> Color(0xFFFF3B30)
    PasswordStrengthLevel.FAIR -> Color(0xFFFF9500)
    PasswordStrengthLevel.STRONG, PasswordStrengthLevel.VERY_STRONG -> Color(0xFF34C759)
}

@Composable
private fun groupLabel(
    selectedGroupId: String?,
    groups: List<AddPasswordPickerGroup>,
    familyId: String,
): String {
    if (selectedGroupId == null) return stringResource(R.string.passwords_unassigned_group_label)
    val unassigned = PasswordGroupIds.id(familyId, PasswordGroupIds.UNASSIGNED_SLUG)
    if (selectedGroupId == unassigned) return stringResource(R.string.passwords_unassigned_group_label)
    return groups.find { it.id == selectedGroupId }?.label ?: stringResource(R.string.passwords_group_fallback_label)
}

/**
 * Traduce il nome icona in stile SF Symbol scritto da iOS nell'equivalente
 * Material. I gruppi sono condivisi via Firestore, quindi l'icona arriva sempre
 * nella forma Apple anche quando il gruppo è nato su Android.
 */
private fun passwordGroupIcon(raw: String?): ImageVector = when {
    raw == null -> Icons.Default.Folder
    raw.startsWith("tray") -> Icons.Default.Inbox
    raw.startsWith("briefcase") -> Icons.Default.Work
    raw.startsWith("person") -> Icons.Default.Person
    raw.startsWith("bubble") -> Icons.Default.Forum
    raw.startsWith("creditcard") -> Icons.Default.CreditCard
    raw.startsWith("house") -> Icons.Default.Home
    else -> Icons.Default.Folder
}

/** Colore del gruppo; ricade sul viola di default se il valore non è leggibile. */
private fun parsePasswordGroupColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex ?: "#7C6FDE")) }
        .getOrDefault(Color(0xFF7C6FDE))
