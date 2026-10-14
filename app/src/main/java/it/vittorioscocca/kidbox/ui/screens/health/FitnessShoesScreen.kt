package it.vittorioscocca.kidbox.ui.screens.health

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.fitness.FitnessShoe
import it.vittorioscocca.kidbox.data.health.fitness.FitnessShoeStore
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class FitnessShoesViewModel @Inject constructor(
    private val store: FitnessShoeStore,
) : ViewModel() {

    private val _shoes = MutableStateFlow<List<FitnessShoe>>(emptyList())
    val shoes: StateFlow<List<FitnessShoe>> = _shoes

    fun load(childId: String) {
        _shoes.value = store.load(childId)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.pull(childId)
                store.syncFromHealth(childId)
            }
            _shoes.value = store.load(childId)
        }
    }

    fun save(shoe: FitnessShoe) {
        store.upsert(shoe)
        _shoes.value = store.load(shoe.childId)
        // Una scarpa nuova o con la data cambiata va riempita subito con le
        // corse di Health Connect, senza aspettare la prossima apertura.
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.syncFromHealth(shoe.childId) }
            _shoes.value = store.load(shoe.childId)
        }
    }

    fun delete(shoe: FitnessShoe) {
        store.delete(shoe)
        _shoes.value = store.load(shoe.childId)
    }
}

private fun formatKm(value: Double): String = String.format(Locale.getDefault(), "%.1f km", value)

private fun decodePhoto(base64: String): Bitmap? {
    if (base64.isEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/**
 * Lato lungo 480 px, JPEG 75%: sta nel documento Firestore (qualche decina di
 * KB). L'orientamento EXIF si applica qui, perché la fotocamera lo scrive nei
 * metadati invece di ruotare i pixel.
 */
private fun compressPhoto(context: android.content.Context, uri: Uri): String? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 960) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching null
    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val longest = maxOf(decoded.width, decoded.height).toFloat()
    val scale = if (longest > 480f) 480f / longest else 1f
    val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
    val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    val stream = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 75, stream)
    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

@Composable
private fun ShoeThumbnail(photoBase64: String, size: Int) {
    val kb = MaterialTheme.kidBoxColors
    val bitmap = remember(photoBase64) { decodePhoto(photoBase64) }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FITNESS_TINT.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.DirectionsRun,
                contentDescription = null,
                tint = FITNESS_TINT,
                modifier = Modifier.size((size * 0.45f).dp),
            )
        }
    }
}

/**
 * Impostazioni delle sessioni del Piano Fitness: oggi contengono le scarpe da
 * corsa (marca, data d'acquisto, km massimi, foto) e i km che restano a ogni
 * paio. Si raggiungono dall'ingranaggio dell'elenco Sessioni.
 */
@Composable
fun FitnessSessionsSettingsScreen(
    childId: String,
    onBack: () -> Unit,
    viewModel: FitnessShoesViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val shoes by viewModel.shoes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FitnessShoe?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var shoeToDelete by remember { mutableStateOf<FitnessShoe?>(null) }

    LaunchedEffect(childId) { viewModel.load(childId) }

    // Il form prende il posto della schermata invece di aprirsi in un Dialog:
    // gli inset sono quelli veri dell'Activity e la tastiera non copre il fondo.
    editing?.let { shoe ->
        FitnessShoeEditScreen(
            shoe = shoe,
            isNew = isNew,
            onSave = { viewModel.save(it); editing = null },
            onDelete = { viewModel.delete(shoe); editing = null },
            onBack = { editing = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidBoxHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.health_back),
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.fitness_settings_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = kb.title,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.fitness_shoes_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = kb.title,
            )
            Text(stringResource(R.string.fitness_shoes_hint), fontSize = 13.sp, color = kb.subtitle)

            if (shoes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.DirectionsRun, null, tint = FITNESS_TINT, modifier = Modifier.size(40.dp))
                    Text(
                        stringResource(R.string.fitness_shoes_empty_title),
                        fontWeight = FontWeight.Bold,
                        color = kb.title,
                    )
                    Text(
                        stringResource(R.string.fitness_shoes_empty_body),
                        fontSize = 14.sp,
                        color = kb.subtitle,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                shoes.forEach { shoe ->
                    ShoeCard(
                        shoe = shoe,
                        onClick = {
                            isNew = false
                            editing = shoe
                        },
                        onToggleRetired = {
                            viewModel.save(
                                shoe.copy(
                                    retiredAtEpochMillis = if (shoe.isRetired) null else System.currentTimeMillis(),
                                ),
                            )
                        },
                        onDelete = { shoeToDelete = shoe },
                    )
                }
            }

            Button(
                onClick = {
                    isNew = true
                    editing = FitnessShoe(
                        childId = childId,
                        brand = "",
                        purchaseDateEpochMillis = System.currentTimeMillis(),
                        maxKm = 0.0,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FITNESS_TINT, contentColor = Color.White),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fitness_shoes_add), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    shoeToDelete?.let { shoe ->
        AlertDialog(
            onDismissRequest = { shoeToDelete = null },
            title = { Text(stringResource(R.string.fitness_shoe_delete_title)) },
            text = { Text(stringResource(R.string.fitness_shoe_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(shoe); shoeToDelete = null }) {
                    Text(stringResource(R.string.fitness_copilot_delete_confirm), color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { shoeToDelete = null }) { Text(stringResource(R.string.fitness_undo)) }
            },
        )
    }
}

@Composable
private fun ShoeCard(
    shoe: FitnessShoe,
    onClick: () -> Unit,
    onToggleRetired: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val kb = MaterialTheme.kidBoxColors
    val barColor = when {
        shoe.isWornOut -> Color(0xFFE53935)
        shoe.progress >= 0.85f -> Color(0xFFFF9800)
        else -> FITNESS_TINT
    }
    val dateText = remember(shoe.purchaseDateEpochMillis) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(shoe.purchaseDateEpochMillis))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(kb.subtitle.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShoeThumbnail(shoe.photoBase64, 64)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    shoe.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = kb.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (shoe.isRetired) {
                    Text(
                        stringResource(R.string.fitness_shoe_retired),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.subtitle,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(kb.subtitle.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.fitness_shoe_purchased, dateText),
                fontSize = 13.sp,
                color = kb.subtitle,
            )
            LinearProgressIndicator(
                progress = { shoe.progress },
                modifier = Modifier.fillMaxWidth(),
                color = barColor,
                trackColor = kb.subtitle.copy(alpha = 0.15f),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.fitness_shoe_used, formatKm(shoe.usedKm)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.subtitle,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (shoe.isWornOut) {
                        stringResource(R.string.fitness_shoe_replace)
                    } else {
                        stringResource(R.string.fitness_shoe_left, formatKm(shoe.remainingKm))
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = barColor,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.fitness_shoe_more),
                    tint = kb.subtitle,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (shoe.isRetired) R.string.fitness_shoe_unretire else R.string.fitness_shoe_retire,
                            ),
                        )
                    },
                    onClick = { menuOpen = false; onToggleRetired() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.fitness_shoe_delete), color = Color(0xFFE53935)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
        // Andamento: come nella scheda delle scarpe di una app di corsa.
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ShoeStat("${shoe.runCount}", stringResource(R.string.fitness_shoe_stat_runs))
            ShoeStat(formatPace(shoe.averagePaceSecondsPerKm), stringResource(R.string.fitness_shoe_stat_pace))
            ShoeStat(durationText(shoe.totalMinutes), stringResource(R.string.fitness_shoe_stat_time))
        }
    }
}

@Composable
private fun ShoeStat(value: String, label: String) {
    val kb = MaterialTheme.kidBoxColors
    Column {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = kb.title)
        Text(label, fontSize = 12.sp, color = kb.subtitle)
    }
}

/** Andatura come «6'55"/km»; «—» quando nessuna corsa ha durata e distanza. */
private fun formatPace(secondsPerKm: Double?): String {
    if (secondsPerKm == null || !secondsPerKm.isFinite()) return "—"
    val total = Math.round(secondsPerKm).toInt()
    return String.format(Locale.US, "%d'%02d\"/km", total / 60, total % 60)
}

/** «45 min» sotto l'ora, «6h 57min» sopra; «—» per zero. */
@Composable
private fun durationText(minutes: Int): String = when {
    minutes <= 0 -> "—"
    minutes < 60 -> stringResource(R.string.fitness_session_minutes, minutes)
    else -> stringResource(R.string.fitness_sessions_hours, minutes / 60, minutes % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FitnessShoeEditScreen(
    shoe: FitnessShoe,
    isNew: Boolean,
    onSave: (FitnessShoe) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current

    var brand by remember { mutableStateOf(shoe.brand) }
    var model by remember { mutableStateOf(shoe.model) }
    var purchaseMillis by remember { mutableStateOf(shoe.purchaseDateEpochMillis) }
    var maxKmText by remember {
        mutableStateOf(
            when {
                shoe.maxKm <= 0 -> ""
                shoe.maxKm % 1.0 == 0.0 -> shoe.maxKm.toInt().toString()
                else -> shoe.maxKm.toString()
            },
        )
    }
    // Km percorsi in totale, modificabili a mano. Vuoto = nessuna correzione.
    val initialUsedKmText = remember { if (isNew) "" else String.format(Locale.US, "%.1f", shoe.usedKm) }
    var usedKmText by remember { mutableStateOf(initialUsedKmText) }
    var photo by remember { mutableStateOf(shoe.photoBase64) }
    var retired by remember { mutableStateOf(shoe.isRetired) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val maxKm = maxKmText.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }
    // Il campo vuoto vale zero; `null` solo se non è un numero valido.
    val usedKm = usedKmText.replace(',', '.').trim().let { text ->
        if (text.isEmpty()) 0.0 else text.toDoubleOrNull()?.takeIf { it >= 0 }
    }
    val canSave = brand.isNotBlank() && maxKm != null && usedKm != null

    val cameraDir = remember { File(context.cacheDir, "fitness-shoes-camera").apply { mkdirs() } }
    val cameraFile = remember { File(cameraDir, "shoe_tmp.jpg") }
    val cameraUri = remember(cameraFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) compressPhoto(context, cameraUri)?.let { photo = it }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            takePicture.launch(cameraUri)
        } else {
            Toast.makeText(context, context.getString(R.string.life_camera_denied), Toast.LENGTH_SHORT).show()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { compressPhoto(context, it)?.let { encoded -> photo = encoded } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidBoxHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.health_back),
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(if (isNew) R.string.fitness_shoe_new else R.string.fitness_shoe_edit),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = kb.title,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        shoe.copy(
                            brand = brand.trim(),
                            model = model.trim(),
                            purchaseDateEpochMillis = purchaseMillis,
                            maxKm = maxKm ?: return@TextButton,
                            photoBase64 = photo,
                            // Solo se il campo è stato toccato: riscrivere il valore
                            // arrotondato a un decimale sposterebbe i km a ogni salvataggio.
                            manualKm = if (usedKmText != initialUsedKmText && usedKm != null) {
                                usedKm - shoe.runsKm
                            } else {
                                shoe.manualKm
                            },
                            retiredAtEpochMillis = when {
                                retired == shoe.isRetired -> shoe.retiredAtEpochMillis
                                retired -> System.currentTimeMillis()
                                else -> null
                            },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.fitness_edit_save), fontWeight = FontWeight.Bold) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable { showPhotoMenu = true },
                ) {
                    ShoeThumbnail(photo, 110)
                    Text(
                        stringResource(if (photo.isEmpty()) R.string.fitness_shoe_photo_add else R.string.fitness_shoe_photo_change),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FITNESS_TINT,
                    )
                }
                DropdownMenu(expanded = showPhotoMenu, onDismissRequest = { showPhotoMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fitness_shoe_photo_take)) },
                        onClick = {
                            showPhotoMenu = false
                            cameraPermission.launch(android.Manifest.permission.CAMERA)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fitness_shoe_photo_pick)) },
                        onClick = {
                            showPhotoMenu = false
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    if (photo.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fitness_shoe_photo_remove), color = Color(0xFFE53935)) },
                            onClick = { showPhotoMenu = false; photo = "" },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text(stringResource(R.string.fitness_shoe_brand)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.fitness_shoe_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showDatePicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.fitness_shoe_date), color = kb.title, modifier = Modifier.weight(1f))
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(purchaseMillis)),
                    fontWeight = FontWeight.SemiBold,
                    color = FITNESS_TINT,
                )
            }

            OutlinedTextField(
                value = maxKmText,
                onValueChange = { maxKmText = it },
                label = { Text(stringResource(R.string.fitness_shoe_maxkm)) },
                placeholder = { Text(stringResource(R.string.fitness_shoe_maxkm_hint)) },
                suffix = { Text("km") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.fitness_shoe_maxkm_footer), fontSize = 12.sp, color = kb.subtitle)

            OutlinedTextField(
                value = usedKmText,
                onValueChange = { usedKmText = it },
                label = { Text(stringResource(R.string.fitness_shoe_usedkm)) },
                placeholder = { Text("0") },
                suffix = { Text("km") },
                singleLine = true,
                isError = usedKm == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.fitness_shoe_usedkm_footer), fontSize = 12.sp, color = kb.subtitle)

            if (!isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fitness_shoe_retired), color = kb.title, modifier = Modifier.weight(1f))
                    Switch(checked = retired, onCheckedChange = { retired = it })
                }
                Text(stringResource(R.string.fitness_shoe_retired_footer), fontSize = 12.sp, color = kb.subtitle)
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.fitness_shoe_delete), color = Color(0xFFE53935))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMidnightOf(purchaseMillis))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { purchaseMillis = localMidnightOfUtcDate(it) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.fitness_edit_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.fitness_undo)) }
            },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.fitness_shoe_delete_title)) },
            text = { Text(stringResource(R.string.fitness_shoe_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.fitness_copilot_delete_confirm), color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.fitness_undo))
                }
            },
        )
    }
}

/** Il DatePicker lavora in UTC: la data locale va riportata a mezzanotte UTC dello stesso giorno. */
private fun utcMidnightOf(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun localMidnightOfUtcDate(utcMillis: Long): Long {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
