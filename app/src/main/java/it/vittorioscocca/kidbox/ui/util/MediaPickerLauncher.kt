package it.vittorioscocca.kidbox.ui.util

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Single image picker (no permission required).
 * Mirrors iOS PhotosPicker(selection:matching:.images)
 */
@Composable
fun rememberSingleImagePicker(onResult: (Uri?) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = onResult,
    )

/**
 * Multi-image+video picker (no permission required).
 * Mirrors iOS PhotosPicker(selection:maxSelectionCount:matching:.any(of:[.images,.videos]))
 */
@Composable
fun rememberMultiMediaPicker(
    maxItems: Int = 30,
    onResult: (List<Uri>) -> Unit,
) = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems),
    onResult = onResult,
)

fun singleImageRequest() =
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

fun imageAndVideoRequest() =
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)

fun videoOnlyRequest() =
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
