package com.vedant.admin.feature.catalogue.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * The two ways to hand the editor an image, as plain lambdas.
 *
 * Both go through **Activity Result contracts** (`PickVisualMedia` for the
 * gallery, `TakePicture` for the camera) — no legacy `startActivityForResult`,
 * no storage permission. The camera writes into a private cache file exposed
 * only through our [FileProvider]; the Uri is never shared beyond the camera app.
 *
 * The callbacks deliver a [Uri] (not bytes) so the caller can do the decode and
 * compression off the main thread.
 */
class ImagePickActions(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit,
)

@Composable
fun rememberImagePickActions(onImageUri: (Uri) -> Unit): ImagePickActions {
    val context = LocalContext.current
    val captureTarget = remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) onImageUri(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { captured: Boolean ->
        val uri = captureTarget.value
        if (captured && uri != null) onImageUri(uri)
    }

    return remember(context) {
        ImagePickActions(
            pickFromGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            takePhoto = {
                val uri = createCaptureUri(context)
                captureTarget.value = uri
                cameraLauncher.launch(uri)
            },
        )
    }
}

/** A fresh, collision-free file for the camera, shared via our [FileProvider]. */
private fun createCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(directory, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
