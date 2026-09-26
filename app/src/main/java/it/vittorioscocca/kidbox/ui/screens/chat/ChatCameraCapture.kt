package it.vittorioscocca.kidbox.ui.screens.chat

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.permissions.RuntimePermissions
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Stesso tetto di iOS (`videoMaximumDuration = 60`). */
private const val MAX_VIDEO_MILLIS = 60_000L

private val CameraYellow = Color(0xFFFFD60A)
private val RecordRed = Color(0xFFFF3B30)

private enum class CaptureMode { PHOTO, VIDEO }

/**
 * Fotocamera della chat con il selettore Foto | Video, come il picker di sistema
 * che usa iOS. Android non ha un intent «foto o video»: TakePicture e
 * CaptureVideo sono due modalità separate, per questo è una schermata nostra
 * (CameraX). Dopo lo scatto c'è l'anteprima con «Riprendi» e «Usa».
 *
 * [onCaptured] riceve il file in `cacheDir/chat_camera` e ne diventa
 * responsabile (va cancellato dopo l'invio). Il permesso CAMERA va chiesto
 * prima di mostrarla; quello del microfono lo chiede lei al passaggio su Video,
 * e senza si registra muti.
 */
@Composable
internal fun ChatCameraCapture(
    onCaptured: (file: File, isVideo: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var torchOn by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var isTakingPhoto by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMillis by remember { mutableLongStateOf(0L) }
    // Chiusura durante la registrazione: il file che arriva al Finalize va buttato.
    var discardRecording by remember { mutableStateOf(false) }
    // Scatto in revisione: (file, isVideo). Finché c'è, la fotocamera è sganciata.
    var review by remember { mutableStateOf<Pair<File, Boolean>?>(null) }
    var audioGranted by remember {
        mutableStateOf(RuntimePermissions.isGranted(context, Manifest.permission.RECORD_AUDIO))
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        audioGranted = it
    }

    val previewView = remember {
        PreviewView(context).apply {
            // Come il picker di iOS: si vede esattamente quello che verrà salvato.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val imageCapture = remember(flashMode) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .setResolutionSelector(aspectSelector(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY))
            .build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
            )
            .build()
        VideoCapture.withOutput(recorder)
    }

    LaunchedEffect(Unit) {
        val p = runCatching { ProcessCameraProvider.getInstance(context).await() }.getOrNull()
        if (p == null) {
            cameraError = true
            return@LaunchedEffect
        }
        hasFrontCamera = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        provider = p
    }

    // Aggancio per modalità: Preview+ImageCapture oppure Preview+VideoCapture.
    // Tenerli tutti e tre insieme non è garantito sui dispositivi LEGACY.
    LaunchedEffect(provider, mode, lensFacing, imageCapture, review == null) {
        val p = provider ?: return@LaunchedEffect
        p.unbindAll()
        camera = null
        torchOn = false
        if (review != null) return@LaunchedEffect
        val preview = Preview.Builder()
            .setResolutionSelector(
                aspectSelector(
                    if (mode == CaptureMode.PHOTO) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                    else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY,
                ),
            )
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        camera = runCatching {
            p.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                if (mode == CaptureMode.PHOTO) imageCapture else videoCapture,
            )
        }.onFailure { cameraError = true }.getOrNull()
        if (camera != null) cameraError = false
    }

    val currentProvider by rememberUpdatedState(provider)
    val currentRecording by rememberUpdatedState(recording)
    DisposableEffect(Unit) {
        onDispose {
            currentRecording?.let {
                discardRecording = true
                it.stop()
            }
            // Senza unbind la fotocamera resta agganciata alla schermata della chat.
            currentProvider?.unbindAll()
        }
    }

    fun newFile(ext: String): File {
        val dir = File(context.cacheDir, "chat_camera").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.$ext")
    }

    fun takePhoto() {
        if (isTakingPhoto || camera == null) return
        isTakingPhoto = true
        val file = newFile("jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    isTakingPhoto = false
                    review = file to false
                }

                override fun onError(exception: ImageCaptureException) {
                    isTakingPhoto = false
                    file.delete()
                }
            },
        )
    }

    @SuppressLint("MissingPermission") // withAudioEnabled solo dopo il controllo del permesso
    fun startRecording() {
        if (recording != null || camera == null) return
        val file = newFile("mp4")
        discardRecording = false
        recordedMillis = 0L
        val options = FileOutputOptions.Builder(file).setDurationLimitMillis(MAX_VIDEO_MILLIS).build()
        val pending = videoCapture.output.prepareRecording(context, options)
            .let { if (audioGranted) it.withAudioEnabled() else it }
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Status ->
                    recordedMillis = TimeUnit.NANOSECONDS.toMillis(event.recordingStats.recordedDurationNanos)
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    // Il limite dei 60 s e l'app mandata in background chiudono il file
                    // regolarmente: il video è valido anche con quell'«errore».
                    val usable = event.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                        event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                        event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
                    if (!discardRecording && usable && file.length() > 0) {
                        review = file to true
                    } else {
                        file.delete()
                    }
                }
                else -> Unit
            }
        }
    }

    fun close() {
        review?.first?.delete()
        review = null
        onClose()
    }

    BackHandler {
        when {
            recording != null -> {
                discardRecording = true
                recording?.stop()
            }
            review != null -> {
                review?.first?.delete()
                review = null
            }
            else -> close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val shot = review
        if (shot != null) {
            CaptureReview(
                file = shot.first,
                isVideo = shot.second,
                onRetake = {
                    shot.first.delete()
                    review = null
                },
                onUse = {
                    review = null
                    onCaptured(shot.first, shot.second)
                },
            )
            return@Box
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (cameraError) {
            Text(
                text = stringResource(R.string.chat_camera_unavailable),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        // ── In alto: chiudi, cronometro, flash ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { close() }, enabled = recording == null) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_close), tint = Color.White)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (mode == CaptureMode.VIDEO) {
                    val isRec = recording != null
                    Text(
                        text = formatElapsed(recordedMillis),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRec) RecordRed else Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            if (hasFlash) {
                if (mode == CaptureMode.PHOTO) {
                    IconButton(onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    }) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                else -> Icons.Default.FlashOff
                            },
                            contentDescription = stringResource(R.string.chat_camera_flash),
                            tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.White else CameraYellow,
                        )
                    }
                } else {
                    // In video il flash è la torcia, accesa finché serve.
                    IconButton(onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    }) {
                        Icon(
                            imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = stringResource(R.string.chat_camera_flash),
                            tint = if (torchOn) CameraYellow else Color.White,
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        // ── In basso: selettore Foto | Video, scatto, cambio fotocamera ──────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeLabel(
                    text = stringResource(R.string.chat_camera_mode_photo),
                    selected = mode == CaptureMode.PHOTO,
                    enabled = recording == null,
                ) { mode = CaptureMode.PHOTO }
                ModeLabel(
                    text = stringResource(R.string.chat_camera_mode_video),
                    selected = mode == CaptureMode.VIDEO,
                    enabled = recording == null,
                ) {
                    mode = CaptureMode.VIDEO
                    if (!audioGranted) audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                ShutterButton(
                    isVideo = mode == CaptureMode.VIDEO,
                    isRecording = recording != null,
                    enabled = camera != null && !isTakingPhoto,
                    contentDescription = stringResource(
                        when {
                            mode == CaptureMode.PHOTO -> R.string.chat_camera_shoot
                            recording != null -> R.string.chat_camera_stop
                            else -> R.string.chat_camera_record
                        },
                    ),
                ) {
                    when {
                        mode == CaptureMode.PHOTO -> takePhoto()
                        recording != null -> recording?.stop()
                        else -> startRecording()
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (hasFrontCamera && recording == null) {
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.18f), CircleShape),
                        ) {
                            Icon(
                                Icons.Default.Cameraswitch,
                                contentDescription = stringResource(R.string.chat_camera_switch),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun aspectSelector(strategy: AspectRatioStrategy): ResolutionSelector =
    ResolutionSelector.Builder().setAspectRatioStrategy(strategy).build()

private fun formatElapsed(millis: Long): String {
    val s = (millis / 1000).coerceAtMost(MAX_VIDEO_MILLIS / 1000)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun ModeLabel(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text.uppercase(),
        color = if (selected) CameraYellow else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.Black.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun ShutterButton(
    isVideo: Boolean,
    isRecording: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    // Come iOS: cerchio bianco per le foto, rosso per il video, quadrato rosso
    // arrotondato mentre registra.
    val innerSize by animateDpAsState(if (isRecording) 30.dp else 60.dp, label = "shutterInner")
    val corner by animateDpAsState(if (isRecording) 7.dp else 30.dp, label = "shutterCorner")
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(corner))
                .background(if (isVideo) RecordRed else Color.White),
        )
    }
}

@Composable
private fun CaptureReview(
    file: File,
    isVideo: Boolean,
    onRetake: () -> Unit,
    onUse: () -> Unit,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(file))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_camera_retake),
                color = Color.White,
                fontSize = 17.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetake)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(if (isVideo) R.string.chat_camera_use_video else R.string.chat_camera_use_photo),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onUse)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
