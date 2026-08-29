package it.vittorioscocca.kidbox.ui.screens.pets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.ui.screens.life.speciesLabel
import it.vittorioscocca.kidbox.ui.screens.life.formatItDate
import it.vittorioscocca.kidbox.ui.screens.life.formatItDateTime
import it.vittorioscocca.kidbox.ui.screens.life.rememberLifeDatePicker
import it.vittorioscocca.kidbox.ui.screens.life.speciesEmoji
import it.vittorioscocca.kidbox.ui.components.IosFormDivider
import it.vittorioscocca.kidbox.ui.components.IosGroupedCard
import it.vittorioscocca.kidbox.ui.components.IosPlainTextFieldRow
import it.vittorioscocca.kidbox.ui.components.KidBoxIosFormTopBar
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import it.vittorioscocca.kidbox.ui.screens.life.petEventTypeLabel

private enum class PetAttachmentPickTarget { Pet, EventDraft }

/** Quanti allegati stanno nella scheda animale prima di "Vedi tutti". */
private const val PET_ATTACHMENTS_PREVIEW = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetDetailScreen(
    onNavigateBack: () -> Unit,
    onAddTreatment: () -> Unit,
    onOpenTreatment: (String) -> Unit,
    viewModel: PetDetailViewModel = hiltViewModel(),
) {
    val pet by viewModel.pet.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val treatments by viewModel.treatments.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var showEditPet by remember { mutableStateOf(false) }
    var showAddEvent by remember { mutableStateOf(false) }
    var confirmDeletePet by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val eventDraftAttachments by viewModel.eventDraftAttachments.collectAsStateWithLifecycle()
    val petAttachments by viewModel.petAttachments.collectAsStateWithLifecycle()
    val attachmentUploading by viewModel.attachmentUploading.collectAsStateWithLifecycle()
    val openFileEvent by viewModel.openFileEvent.collectAsStateWithLifecycle()
    val attachmentError by viewModel.attachmentError.collectAsStateWithLifecycle()
    // L'id dell'evento esiste prima dell'evento: gli allegati caricati mentre
    // compili vanno taggati con l'id che avrà una volta salvato.
    var pendingEventDraftId by remember { mutableStateOf<String?>(null) }
    // L'evento salvato che stai guardando: stessa scheda del nuovo evento, ma
    // compilata, e con gli allegati che sono già i suoi.
    var editingEvent by remember { mutableStateOf<PetEventEntity?>(null) }
    var confirmDeleteEvent by remember { mutableStateOf<PetEventEntity?>(null) }
    var showKidBoxPicker by remember { mutableStateOf(false) }
    var attachmentTarget by remember { mutableStateOf(PetAttachmentPickTarget.EventDraft) }
    var showAllAttachments by remember { mutableStateOf(false) }

    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val context = LocalContext.current

    LaunchedEffect(attachmentError) {
        attachmentError?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeAttachmentError()
        }
    }
    LaunchedEffect(openFileEvent) {
        openFileEvent?.let { (mime, file) ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
            viewModel.consumeOpenFileEvent()
        }
    }

    val petCamDir = remember { File(context.cacheDir, "pets-camera").apply { mkdirs() } }
    val petCameraFile = remember { File(petCamDir, "pet_attach_tmp.jpg") }
    val petCameraUri = remember(petCameraFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", petCameraFile)
    }

    // Le due sorgenti di allegati convivono: la scheda dell'animale e la bozza
    // dell'evento. Senza un bersaglio esplicito una foto scattata dalla scheda
    // finirebbe attaccata all'evento aperto per ultimo.
    fun uploadUri(uri: Uri) {
        when (attachmentTarget) {
            PetAttachmentPickTarget.Pet -> viewModel.uploadPetAttachment(uri)
            PetAttachmentPickTarget.EventDraft -> viewModel.uploadEventDraftAttachment(uri)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) uploadUri(petCameraUri)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            takePictureLauncher.launch(petCameraUri)
        } else {
            Toast.makeText(context, context.getString(R.string.life_camera_denied), Toast.LENGTH_SHORT).show()
        }
    }
    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { uploadUri(it) }
    }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { uploadUri(it) }
    }

    LaunchedEffect(pet?.id) {
        val p = pet ?: return@LaunchedEffect
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (p.createdBy.isNotBlank() && p.createdBy != currentUid) {
            AppAnalytics.contentSharedRead(context, "pets")
        }
    }

    if (showAllAttachments) {
        // Schermata vera e non foglio: qui si scorre, si apre e si elimina, e
        // una finestra sovrapposta stringerebbe di nuovo tutto.
        PetAttachmentsScreen(
            attachments = petAttachments,
            isUploading = attachmentUploading,
            onBack = { showAllAttachments = false },
            onOpen = { viewModel.openAttachment(it) },
            onDelete = { viewModel.deleteAttachment(it) },
            onAddFile = {
                attachmentTarget = PetAttachmentPickTarget.Pet
                pickFileLauncher.launch(arrayOf("*/*"))
            },
            onAddPhoto = {
                attachmentTarget = PetAttachmentPickTarget.Pet
                pickPhotoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onTakePhoto = {
                attachmentTarget = PetAttachmentPickTarget.Pet
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    takePictureLauncher.launch(petCameraUri)
                } else {
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onPickKidBox = {
                attachmentTarget = PetAttachmentPickTarget.Pet
                showKidBoxPicker = true
            },
            kb = kb,
            orange = orange,
        )
    } else {

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pet?.name ?: stringResource(R.string.pets_detail_fallback_title),
                        color = kb.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            pendingEventDraftId = UUID.randomUUID().toString()
                            viewModel.bindEventDraftAttachments(pendingEventDraftId)
                            showAddEvent = true
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pets_new_event_cd), tint = orange)
                    }
                    IconButton(onClick = { showEditPet = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.pets_edit_cd), tint = kb.title)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.pets_more_cd), tint = kb.title)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pets_new_treatment_item)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Medication, contentDescription = null, tint = orange)
                                },
                                onClick = {
                                    menuOpen = false
                                    onAddTreatment()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pets_delete_pet_item)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDeletePet = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kb.background,
                    titleContentColor = kb.title,
                    navigationIconContentColor = kb.title,
                    actionIconContentColor = kb.title,
                ),
            )
        },
    ) { padding ->
        val p = pet
        if (p == null) {
            Spacer(Modifier.fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                PetHeaderCard(p = p, kb = kb)
            }

            item {
                Text(
                    stringResource(R.string.pets_section_events_history),
                    color = kb.subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                if (events.isEmpty()) {
                    Text(
                        stringResource(R.string.pets_no_events),
                        color = kb.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column {
                            events.forEachIndexed { index, ev ->
                                PetEventRow(
                                    ev = ev,
                                    orange = orange,
                                    kb = kb,
                                    onClick = {
                                        editingEvent = ev
                                        viewModel.bindEventDraftAttachments(ev.id)
                                    },
                                )
                                if (index < events.lastIndex) {
                                    HorizontalDivider(color = kb.divider, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.pets_section_treatments),
                    color = kb.subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                val care = treatments.filter { !it.isDeleted }
                if (care.isEmpty()) {
                    Text(
                        stringResource(R.string.pets_no_treatments),
                        color = kb.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column {
                            care.forEachIndexed { index, t ->
                                PetTreatmentRow(
                                    t = t,
                                    orange = orange,
                                    kb = kb,
                                    onClick = { onOpenTreatment(t.id) },
                                )
                                if (index < care.lastIndex) {
                                    HorizontalDivider(color = kb.divider, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
            // Allegati dell'animale: libretto sanitario, pedigree, referti.
            // Finiscono in Documenti › Animali domestici, la stessa cartella
            // degli allegati degli eventi.
            item {
                Text(
                    stringResource(R.string.life_attachments),
                    color = kb.subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                HealthAttachmentsCard(
                    // In scheda solo i primi quattro: la lista intera farebbe
                    // scorrere la scheda dell'animale per minuti. Il resto sta
                    // dietro "Vedi tutti".
                    attachments = petAttachments.take(PET_ATTACHMENTS_PREVIEW),
                    tintColor = orange,
                    isUploading = attachmentUploading,
                    onPickFile = {
                        attachmentTarget = PetAttachmentPickTarget.Pet
                        pickFileLauncher.launch(arrayOf("*/*"))
                    },
                    onPickPhoto = {
                        attachmentTarget = PetAttachmentPickTarget.Pet
                        pickPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onTakePhoto = {
                        attachmentTarget = PetAttachmentPickTarget.Pet
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            takePictureLauncher.launch(petCameraUri)
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onOpenAttachment = { viewModel.openAttachment(it) },
                    onDeleteAttachment = { viewModel.deleteAttachment(it) },
                    onPickFromKidBoxDocuments = {
                        attachmentTarget = PetAttachmentPickTarget.Pet
                        showKidBoxPicker = true
                    },
                )
            }

            if (petAttachments.size > PET_ATTACHMENTS_PREVIEW) {
                item {
                    TextButton(
                        onClick = { showAllAttachments = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.pets_attachments_see_all, petAttachments.size),
                            color = orange,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    } // fine else: scheda animale

    if (showAddEvent && pendingEventDraftId != null) {
        PetEventDialog(
            initial = null,
            attachments = eventDraftAttachments,
            attachmentUploading = attachmentUploading,
            onDismiss = {
                // Chi annulla non lascia allegati appesi in Documenti: senza
                // evento a cui appartenere non li ritroverebbe più nessuno.
                pendingEventDraftId?.let { viewModel.discardDraftAttachments(it) }
                viewModel.bindEventDraftAttachments(null)
                pendingEventDraftId = null
                showAddEvent = false
            },
            onConfirm = { title, type, date, nextDue, vet, cost, notes, reminder ->
                viewModel.addPetEvent(
                    pendingEventDraftId, title, type, date, nextDue, vet, cost, notes, reminder,
                ) { err -> toast = err }
                viewModel.bindEventDraftAttachments(null)
                pendingEventDraftId = null
                showAddEvent = false
            },
            onTakePhoto = {
                attachmentTarget = PetAttachmentPickTarget.EventDraft
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    takePictureLauncher.launch(petCameraUri)
                } else {
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onPickPhoto = {
                attachmentTarget = PetAttachmentPickTarget.EventDraft
                pickPhotoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickFile = {
                attachmentTarget = PetAttachmentPickTarget.EventDraft
                pickFileLauncher.launch(arrayOf("*/*"))
            },
            onPickKidBox = {
                attachmentTarget = PetAttachmentPickTarget.EventDraft
                showKidBoxPicker = true
            },
            onOpenAttachment = { viewModel.openAttachment(it) },
            onDeleteAttachment = { viewModel.deleteAttachment(it) },
        )
    }

    editingEvent?.let { existing ->
        PetEventDialog(
            initial = existing,
            attachments = eventDraftAttachments,
            attachmentUploading = attachmentUploading,
            onDismiss = {
                // Niente pulizia degli allegati: qui appartengono a un evento
                // che esiste, non a una bozza abbandonata.
                viewModel.bindEventDraftAttachments(null)
                editingEvent = null
            },
            onConfirm = { title, type, date, nextDue, vet, cost, notes, reminder ->
                viewModel.updatePetEvent(
                    existing.copy(
                        title = title,
                        eventType = type,
                        date = date,
                        nextDueDate = nextDue,
                        vetName = vet,
                        cost = cost,
                        notes = notes,
                        reminderEnabled = reminder,
                    ),
                ) { err -> toast = err }
                viewModel.bindEventDraftAttachments(null)
                editingEvent = null
            },
            onDelete = { confirmDeleteEvent = existing },
            onTakePhoto = {
                attachmentTarget = PetAttachmentPickTarget.EventDraft
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    takePictureLauncher.launch(petCameraUri)
                } else {
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onPickPhoto = {
                attachmentTarget = PetAttachmentPickTarget.EventDraft
                pickPhotoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickFile = {
                attachmentTarget = PetAttachmentPickTarget.EventDraft
                pickFileLauncher.launch(arrayOf("*/*"))
            },
            onPickKidBox = {
                attachmentTarget = PetAttachmentPickTarget.EventDraft
                showKidBoxPicker = true
            },
            onOpenAttachment = { viewModel.openAttachment(it) },
            onDeleteAttachment = { viewModel.deleteAttachment(it) },
        )
    }

    confirmDeleteEvent?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteEvent = null },
            title = { Text(stringResource(R.string.pets_delete_event_confirm_title)) },
            text = { Text(stringResource(R.string.pets_delete_event_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePetEvent(target) { err -> toast = err }
                        confirmDeleteEvent = null
                        viewModel.bindEventDraftAttachments(null)
                        editingEvent = null
                    },
                ) { Text(stringResource(R.string.pets_action_delete), color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteEvent = null }) {
                    Text(stringResource(R.string.pets_action_cancel))
                }
            },
        )
    }

    val familyIdForPicker = pet?.familyId.orEmpty()
    if (showKidBoxPicker && familyIdForPicker.isNotBlank()) {
        KidBoxDocumentPickerSheet(
            familyId = familyIdForPicker,
            onDismiss = { showKidBoxPicker = false },
            onPickedUri = { uri ->
                uploadUri(uri)
                showKidBoxPicker = false
            },
        )
    }

    if (showEditPet && pet != null) {
        EditPetDialog(
            initial = pet!!,
            onDismiss = { showEditPet = false },
            onConfirm = { updated ->
                viewModel.updatePet(updated) { err -> toast = err }
                showEditPet = false
            },
        )
    }

    if (confirmDeletePet && pet != null) {
        AlertDialog(
            onDismissRequest = { confirmDeletePet = false },
            title = { Text(stringResource(R.string.pets_delete_pet_confirm_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pet?.let { viewModel.deletePet(it) { err -> toast = err }; onNavigateBack() }
                        confirmDeletePet = false
                    },
                ) { Text(stringResource(R.string.pets_action_delete), color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePet = false }) { Text(stringResource(R.string.pets_action_cancel)) } },
        )
    }

    toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { toast = null },
            confirmButton = { TextButton(onClick = { toast = null }) { Text(stringResource(R.string.pets_action_ok)) } },
            title = { Text(stringResource(R.string.pets_error_title)) },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun PetHeaderCard(
    p: PetEntity,
    kb: KidBoxColorScheme,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    speciesEmoji(p.species),
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = kb.title,
                    )
                    p.breed?.takeIf { it.isNotBlank() }?.let { breed ->
                        Text(breed, style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
                    }
                }
            }
            p.birthDate?.let { bd ->
                val years = petAgeYearsEuropeRome(bd)
                PetLabeledBlock(
                    label = stringResource(R.string.pets_field_birth_date),
                    value = stringResource(R.string.pets_birth_date_with_age, formatItDate(bd), years),
                    kb = kb,
                )
            }
            p.chipCode?.takeIf { it.isNotBlank() }?.let { chip ->
                PetLabeledBlock(label = stringResource(R.string.pets_field_microchip), value = chip, kb = kb)
            }
            p.color?.takeIf { it.isNotBlank() }?.let { col ->
                PetLabeledBlock(label = stringResource(R.string.pets_field_color), value = col, kb = kb)
            }
            p.notes?.takeIf { it.isNotBlank() }?.let { n ->
                Text(n, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
            }
        }
    }
}

@Composable
private fun PetLabeledBlock(label: String, value: String, kb: KidBoxColorScheme) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = kb.subtitle)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = kb.title)
    }
}

private fun petAgeYearsEuropeRome(birthMillis: Long): Int {
    val zone = ZoneId.of("Europe/Rome")
    val birth = Instant.ofEpochMilli(birthMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return ChronoUnit.YEARS.between(birth, today).toInt().coerceAtLeast(0)
}

@Composable
private fun PetTreatmentRow(
    t: KBTreatment,
    orange: Color,
    kb: KidBoxColorScheme,
    onClick: () -> Unit,
) {
    val reminderBg = orange.copy(alpha = 0.2f)
    val dosageStr = if (t.dosageValue % 1.0 == 0.0) "%.0f".format(t.dosageValue) else "%.1f".format(t.dosageValue)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Medication,
                contentDescription = null,
                tint = orange,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t.drugName,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = kb.title,
            )
            Text(
                stringResource(R.string.pets_dosage_per_day, dosageStr, t.dosageUnit, t.dailyFrequency),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            if (t.reminderEnabled) {
                Surface(
                    color = reminderBg,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        stringResource(R.string.pets_reminder_active),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = orange,
                    )
                }
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = kb.subtitle.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun petEventEmoji(type: String): String = when (type) {
    "vaccine" -> "💉"
    "vet_visit" -> "🩺"
    "medication" -> "💊"
    "grooming" -> "✂️"
    else -> "📅"
}

@Composable
private fun PetEventRow(
    ev: PetEventEntity,
    orange: Color,
    kb: KidBoxColorScheme,
    onClick: () -> Unit,
) {
    val nextDuePillBackground = orange.copy(alpha = 0.2f)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                petEventEmoji(ev.eventType),
                fontSize = 20.sp,
                color = orange,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                ev.title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = kb.title,
            )
            Text(
                formatItDateTime(ev.date),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            ev.nextDueDate?.let { nd ->
                Surface(
                    color = nextDuePillBackground,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        stringResource(R.string.pets_next_due_prefix, formatItDate(nd)),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = orange,
                    )
                }
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = kb.subtitle.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PetEventDialog(
    /** `null` per un evento nuovo; l'evento salvato quando lo apri dallo storico. */
    initial: PetEventEntity?,
    attachments: List<KBDocumentEntity>,
    attachmentUploading: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onPickKidBox: () -> Unit,
    onOpenAttachment: (KBDocumentEntity) -> Unit,
    onDeleteAttachment: (KBDocumentEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (
        title: String,
        type: String,
        dateMillis: Long,
        nextDue: Long?,
        vet: String?,
        cost: Double?,
        notes: String?,
        reminder: Boolean,
    ) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var type by remember { mutableStateOf(initial?.eventType ?: "vaccine") }
    var dateMillis by remember { mutableStateOf(initial?.date ?: System.currentTimeMillis()) }
    var nextDue by remember { mutableStateOf(initial?.nextDueDate) }
    var vet by remember { mutableStateOf(initial?.vetName.orEmpty()) }
    var costText by remember {
        mutableStateOf(initial?.cost?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    val types = listOf("vaccine", "vet_visit", "medication", "grooming", "other")
    val pickDate = rememberLifeDatePicker { dateMillis = it }
    val pickNext = rememberLifeDatePicker { picked -> nextDue = picked }
    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val canSave = title.trim().isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = kb.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                KidBoxIosFormTopBar(
                    title = stringResource(
                        if (initial == null) R.string.pets_new_event_dialog_title
                        else R.string.pets_edit_event_dialog_title,
                    ),
                    onCancel = onDismiss,
                    onSave = {
                        if (canSave) {
                            val cost = costText.replace(',', '.').toDoubleOrNull()
                            onConfirm(
                                title.trim(),
                                type,
                                dateMillis,
                                nextDue,
                                vet.trim().takeIf { it.isNotEmpty() },
                                cost,
                                notes.trim().takeIf { it.isNotEmpty() },
                                nextDue != null,
                            )
                        }
                    },
                    saveEnabled = canSave,
                    kb = kb,
                    orange = orange,
                )
                HorizontalDivider(color = kb.divider)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IosGroupedCard(kb) {
                        IosPlainTextFieldRow(title, { title = it }, stringResource(R.string.pets_field_title), kb = kb)
                        IosFormDivider(kb)
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { typeMenuOpen = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.pets_field_event_type), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(petEventTypeLabel(context, type), color = kb.subtitle)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = kb.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = typeMenuOpen,
                                onDismissRequest = { typeMenuOpen = false },
                            ) {
                                types.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(petEventTypeLabel(context, t)) },
                                        onClick = {
                                            type = t
                                            typeMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { pickDate(dateMillis) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.pets_field_date), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Text(formatItDate(dateMillis), color = orange, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    IosGroupedCard(kb) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.pets_field_next_due), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = nextDue != null,
                                onCheckedChange = { on ->
                                    if (on) nextDue = nextDue ?: dateMillis
                                    if (!on) nextDue = null
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.35f),
                                ),
                            )
                        }
                        if (nextDue != null) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pickNext(nextDue) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.pets_field_next_due), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(
                                    nextDue?.let { formatItDate(it) }.orEmpty(),
                                    color = orange,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(vet, { vet = it }, stringResource(R.string.pets_field_vet), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(costText, { costText = it }, stringResource(R.string.pets_field_cost), kb = kb)
                    }

                    Text(
                        stringResource(R.string.pets_field_notes),
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            placeholder = { Text(stringResource(R.string.pets_field_notes), color = kb.subtitle) },
                            singleLine = false,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = kb.title,
                                unfocusedTextColor = kb.title,
                                cursorColor = kb.title,
                                focusedPlaceholderColor = kb.subtitle,
                                unfocusedPlaceholderColor = kb.subtitle,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Text(
                        stringResource(R.string.life_attachments),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    HealthAttachmentsCard(
                        attachments = attachments,
                        tintColor = orange,
                        isUploading = attachmentUploading,
                        onPickFile = onPickFile,
                        onPickPhoto = onPickPhoto,
                        onTakePhoto = onTakePhoto,
                        onOpenAttachment = onOpenAttachment,
                        onDeleteAttachment = onDeleteAttachment,
                        onPickFromKidBoxDocuments = onPickKidBox,
                    )

                    if (onDelete != null) {
                        IosGroupedCard(kb) {
                            Text(
                                stringResource(R.string.pets_delete_event_item),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFE53935),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onDelete)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPetDialog(
    initial: PetEntity,
    onDismiss: () -> Unit,
    onConfirm: (PetEntity) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var species by remember(initial.id) { mutableStateOf(initial.species) }
    var breed by remember(initial.id) { mutableStateOf(initial.breed.orEmpty()) }
    var hasBirthDate by remember(initial.id) { mutableStateOf(initial.birthDate != null) }
    var birthDate by remember(initial.id) { mutableStateOf(initial.birthDate ?: System.currentTimeMillis()) }
    var color by remember(initial.id) { mutableStateOf(initial.color.orEmpty()) }
    var chipCode by remember(initial.id) { mutableStateOf(initial.chipCode.orEmpty()) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var speciesMenuOpen by remember { mutableStateOf(false) }

    val speciesOptions = listOf("cane", "gatto", "coniglio", "criceto", "uccello", "altro")
    val pickBirth = rememberLifeDatePicker { birthDate = it }
    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    val canSave = name.trim().isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = kb.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                KidBoxIosFormTopBar(
                    title = stringResource(R.string.pets_edit_pet_title),
                    onCancel = onDismiss,
                    onSave = {
                        if (canSave) {
                            val trimmed = name.trim()
                            onConfirm(
                                initial.copy(
                                    name = trimmed,
                                    species = species,
                                    breed = breed.trim().takeIf { it.isNotEmpty() },
                                    birthDate = if (hasBirthDate) birthDate else null,
                                    color = color.trim().takeIf { it.isNotEmpty() },
                                    chipCode = chipCode.trim().takeIf { it.isNotEmpty() },
                                    notes = notes.trim().takeIf { it.isNotEmpty() },
                                ),
                            )
                        }
                    },
                    saveEnabled = canSave,
                    kb = kb,
                    orange = orange,
                )
                HorizontalDivider(color = kb.divider)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IosGroupedCard(kb) {
                        IosPlainTextFieldRow(name, { name = it }, stringResource(R.string.pets_field_name), kb = kb)
                        IosFormDivider(kb)
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { speciesMenuOpen = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.pets_field_species), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(speciesLabel(context, species), color = kb.subtitle)
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = kb.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = speciesMenuOpen,
                                onDismissRequest = { speciesMenuOpen = false },
                            ) {
                                speciesOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(speciesLabel(context, opt)) },
                                        onClick = {
                                            species = opt
                                            speciesMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(breed, { breed = it }, stringResource(R.string.pets_field_breed_optional), kb = kb)
                    }

                    IosGroupedCard(kb) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.pets_field_birth_date), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                            Switch(
                                checked = hasBirthDate,
                                onCheckedChange = { on ->
                                    hasBirthDate = on
                                    if (on && initial.birthDate == null) {
                                        birthDate = System.currentTimeMillis()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = orange,
                                    checkedTrackColor = orange.copy(alpha = 0.35f),
                                ),
                            )
                        }
                        if (hasBirthDate) {
                            IosFormDivider(kb)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pickBirth(birthDate) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.pets_field_date), style = MaterialTheme.typography.bodyLarge, color = kb.title)
                                Text(formatItDate(birthDate), color = orange, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(color, { color = it }, stringResource(R.string.pets_field_color), kb = kb)
                        IosFormDivider(kb)
                        IosPlainTextFieldRow(chipCode, { chipCode = it }, stringResource(R.string.pets_field_microchip), kb = kb)
                    }

                    Text(
                        stringResource(R.string.pets_field_notes),
                        style = MaterialTheme.typography.labelLarge,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    IosGroupedCard(kb) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            placeholder = { Text(stringResource(R.string.pets_field_notes), color = kb.subtitle) },
                            singleLine = false,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = kb.title,
                                unfocusedTextColor = kb.title,
                                cursorColor = kb.title,
                                focusedPlaceholderColor = kb.subtitle,
                                unfocusedPlaceholderColor = kb.subtitle,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * L'elenco completo degli allegati dell'animale: si apre da "Vedi tutti".
 *
 * Riusa la stessa scheda della vista compatta, che sa già aprire ed eliminare
 * riga per riga — cambia solo che qui non c'è un tetto di quattro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PetAttachmentsScreen(
    attachments: List<KBDocumentEntity>,
    isUploading: Boolean,
    onBack: () -> Unit,
    onOpen: (KBDocumentEntity) -> Unit,
    onDelete: (KBDocumentEntity) -> Unit,
    onAddFile: () -> Unit,
    onAddPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickKidBox: () -> Unit,
    kb: KidBoxColorScheme,
    orange: Color,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.life_attachments),
                        color = kb.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kb.background,
                    titleContentColor = kb.title,
                    navigationIconContentColor = kb.title,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HealthAttachmentsCard(
                    attachments = attachments,
                    tintColor = orange,
                    isUploading = isUploading,
                    onPickFile = onAddFile,
                    onPickPhoto = onAddPhoto,
                    onTakePhoto = onTakePhoto,
                    onOpenAttachment = onOpen,
                    onDeleteAttachment = onDelete,
                    onPickFromKidBoxDocuments = onPickKidBox,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
