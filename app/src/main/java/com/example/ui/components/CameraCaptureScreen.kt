package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executor

/**
 * Écran de capture de photo optimisé avec CameraX pour photographier un ticket de caisse.
 */
@Composable
fun CameraCaptureScreen(
    onImageCaptured: (tempImagePath: String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialisation simplifiée du contrôleur de caméra CameraX qui gère automatiquement le cycle de vie
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    var isFlashEnabled by remember { mutableStateOf(false) }

    // Liaison du cycle de vie
    LaunchedEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_capture_screen")
    ) {
        // Aperçu de la Caméra en mode Plein écran
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    controller = cameraController
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay d'assistance visuelle pour cadrer le ticket de caisse
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Zone de cadrage à bords arrondis en pointillé ou en ligne continue
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f) // Format vertical classique type ticket
                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), MaterialTheme.shapes.medium)
                    .background(Color.Black.copy(alpha = 0.15f))
            )
        }

        // Instructions à l'écran
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
                .padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
            shape = CircleShape
        ) {
            Text(
                text = "Cadrez votre ticket de caisse à l'intérieur",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        // Bouton de fermeture à gauche
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 64.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .testTag("camera_close_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fermer l'appareil photo",
                tint = Color.White
            )
        }

        // Bouton Flash en haut à droite
        IconButton(
            onClick = {
                isFlashEnabled = !isFlashEnabled
                cameraController.enableTorch(isFlashEnabled)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 64.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .testTag("camera_flash_button")
        ) {
            Icon(
                imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = if (isFlashEnabled) "Désactiver le flash" else "Activer le flash",
                tint = Color.White
            )
        }

        // Section des contrôles au bas de l'écran
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Shutter Button (Bouton circulaire)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = {
                        takePhoto(
                            context = context,
                            controller = cameraController,
                            onPhotoCaptured = { uri ->
                                uri.path?.let { onImageCaptured(it) }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White)
                        .testTag("shutter_button")
                ) {
                    // Cercle interne épuré
                }
            }
        }
    }
}

/**
 * Fonction helper d'exécution de la capture d'image avec CameraX
 */
private fun takePhoto(
    context: Context,
    controller: LifecycleCameraController,
    onPhotoCaptured: (Uri) -> Unit
) {
    val tempFile = File(context.cacheDir, "temp_receipt_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    controller.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(tempFile)
                onPhotoCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCaptureScreen", "Erreur capture d'image: ${exception.message}", exception)
            }
        }
    )
}
