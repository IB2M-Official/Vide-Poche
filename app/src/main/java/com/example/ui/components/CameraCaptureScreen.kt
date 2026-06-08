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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import java.io.File

/**
 * Écran de capture de photo optimisé avec CameraX pour photographier un ou plusieurs tickets de caisse.
 */
@Composable
fun CameraCaptureScreen(
    onImagesCaptured: (tempImagePaths: List<String>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Liste locale réactive des clichés capturés pour ce ticket
    val capturedPaths = remember { mutableStateListOf<String>() }

    // Initialisation du contrôleur de caméra CameraX
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
                .padding(bottom = 120.dp, top = 60.dp)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.65f) // Format vertical classique
                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), RoundedCornerShape(16.dp))
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
            val textInstr = if (capturedPaths.isEmpty()) {
                "Cadrez votre ticket de caisse"
            } else {
                "${capturedPaths.size} photo(s) prise(s)"
            }
            Text(
                text = textInstr,
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

        // Galerie horizontale des miniatures capturées en direct au-dessus des réglages
        if (capturedPaths.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 144.dp)
                    .fillMaxWidth()
                    .height(84.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(capturedPaths) { path ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Aperçu miniature",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { capturedPaths.remove(path) },
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Supprimer cette photo",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section des contrôles au bas de l'écran (Obturateur + Validation)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 44.dp)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Espaceur à gauche pour aligner l'obturateur
            Spacer(modifier = Modifier.width(60.dp))

            // Shutter Button (Prise de photo)
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
                                uri.path?.let { capturedPaths.add(it) }
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

            // Bouton de Validation / Enregistrement final des clichés
            if (capturedPaths.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onImagesCaptured(capturedPaths.toList())
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .testTag("confirm_capture_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirmer les clichés",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(60.dp))
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
