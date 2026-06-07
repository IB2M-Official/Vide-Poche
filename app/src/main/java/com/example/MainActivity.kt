package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.LocalReceiptRepository
import com.example.ui.components.AddReceiptDialog
import com.example.ui.components.CameraCaptureScreen
import com.example.ui.components.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ReceiptViewModel
import com.example.viewmodel.ReceiptViewModelFactory

enum class Screen {
    Home,
    Camera
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialisation de la base de données Room et du Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = LocalReceiptRepository(database.receiptDao())
        
        // Initialisation de notre ViewModel unique via sa Factory personnalisée
        val viewModel = ViewModelProvider(
            this,
            ReceiptViewModelFactory(application, repository)
        )[ReceiptViewModel::class.java]

        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(Screen.Home) }
                var tempImagePathForDialog by remember { mutableStateOf<String?>(null) }
                val context = LocalContext.current

                // Demande de permissions dynamique et moderne de CameraX et de Notifications Push (Android 13+)
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
                    
                    if (cameraGranted) {
                        currentScreen = Screen.Camera
                    } else {
                        Toast.makeText(
                            context,
                            "La permission Caméra est requise pour pouvoir photographier les tickets.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        Screen.Home -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToCamera = {
                                    val cameraPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED

                                    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                    } else {
                                        true
                                    }

                                    if (cameraPermission && notificationPermission) {
                                        currentScreen = Screen.Camera
                                    } else {
                                        val permissions = mutableListOf(android.Manifest.permission.CAMERA)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        permissionLauncher.launch(permissions.toTypedArray())
                                    }
                                }
                            )

                            // Fenêtre de dialogue d'ajout : S'affiche immédiatement après la capture
                            tempImagePathForDialog?.let { tempPath ->
                                AddReceiptDialog(
                                    tempImagePath = tempPath,
                                    onDismiss = { tempImagePathForDialog = null },
                                    onSave = { title, purchaseDate, warrantyMonths, notes ->
                                        viewModel.addReceipt(
                                            title = title,
                                            purchaseDate = purchaseDate,
                                            warrantyMonths = warrantyMonths,
                                            notes = notes,
                                            tempImagePath = tempPath
                                        )
                                        tempImagePathForDialog = null
                                    }
                                )
                            }
                        }
                        Screen.Camera -> {
                            CameraCaptureScreen(
                                onImageCaptured = { capturedPath ->
                                    tempImagePathForDialog = capturedPath
                                    currentScreen = Screen.Home
                                },
                                onClose = {
                                    currentScreen = Screen.Home
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
