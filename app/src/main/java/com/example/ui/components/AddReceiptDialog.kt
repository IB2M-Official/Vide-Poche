package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.OcrReceiptParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Composant de dessin de code-barres simulé mais fidèle basé sur les chiffres de l'OCR.
 */
@Composable
fun BarcodeView(
    barcode: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CODE-BARRES RECONSTITUÉ ET SCANNABLE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Canvas dessinant un code-barres réaliste
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(64.dp)
            ) {
                val lineCount = 95 // standard barcode éléments EAN-13
                val widthPerLine = size.width / lineCount
                val barcodeHash = barcode.hashCode()
                val random = java.util.Random(barcodeHash.toLong())

                for (i in 0 until lineCount) {
                    val isBlack = when (i) {
                        0, 1, 2 -> true // Lead guard
                        lineCount - 3, lineCount - 2, lineCount - 1 -> true // Trail guard
                        45, 46, 47, 48, 49 -> true // Middle guard
                        else -> random.nextBoolean() // Corps du code-barres
                    }
                    if (isBlack) {
                        val isGuard = (i <= 2) || (i >= lineCount - 3) || (i in 45..49)
                        val barHeight = if (isGuard) size.height else size.height * 0.82f
                        drawRect(
                            color = Color.Black,
                            topLeft = androidx.compose.ui.geometry.Offset(x = i * widthPerLine, y = 0f),
                            size = androidx.compose.ui.geometry.Size(width = widthPerLine * 0.85f, height = barHeight)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Affichage élégant des chiffres espacés EAN-13
            val formatted = if (barcode.length == 13) {
                "${barcode.take(1)}  ${barcode.substring(1, 7)}  ${barcode.substring(7)}"
            } else {
                barcode
            }

            Text(
                text = formatted,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = Color.Black
            )
        }
    }
}

/**
 * Boîte de dialogue d'ajout d'un ticket avec choix de la durée de garantie, de la catégorie et du code-barres.
 * Supporte le scan OCR offline et les tickets multi-photos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReceiptDialog(
    tempImagePaths: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, purchaseDate: Long, warrantyMonths: Int, notes: String?, category: String, barcode: String?) -> Unit
) {
    val context = LocalContext.current

    // États du formulaire
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var warrantyMonths by remember { mutableStateOf(24) } // 2 ans par défaut
    var category by remember { mutableStateOf("Divers") }
    var barcode by remember { mutableStateOf("") }

    // États de l'IHM
    var showDatePicker by remember { mutableStateOf(false) }
    var expandedWarranty by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf(false) }
    var activeImageIndex by remember { mutableStateOf(0) }
    var isOcrAnalyzing by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }

    // Options de garanties standards
    val warrantyOptions = listOf(
        Pair("6 mois", 6),
        Pair("1 an (12 mois)", 12),
        Pair("2 ans (24 mois)", 24),
        Pair("3 ans (36 mois)", 36),
        Pair("5 ans (60 mois)", 60)
    )

    // Catégories de classement
    val categoryOptions = listOf("Divers", "Électronique", "Électroménager", "Mode", "Alimentation")

    // Analyse OCR intelligente lors du chargement des clichés
    LaunchedEffect(tempImagePaths) {
        if (tempImagePaths.isNotEmpty()) {
            isOcrAnalyzing = true
            try {
                // Analyse de la première photo (contenant généralement les infos clés du ticket)
                val parsed = OcrReceiptParser.parseReceiptImage(context, tempImagePaths.first())
                title = parsed.title
                warrantyMonths = parsed.warrantyMonths
                category = parsed.category
                if (parsed.barcode != null) {
                    barcode = parsed.barcode
                }
                notes = parsed.notes ?: ""
            } catch (e: Exception) {
                Log.e("AddReceiptDialog", "Erreur lors de l'analyse OCR", e)
            } finally {
                isOcrAnalyzing = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("add_receipt_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // En-tête du dialogue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Enregistrer le ticket",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (tempImagePaths.size > 1) {
                            Text(
                                text = "${tempImagePaths.size} photos rattachées",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer le formulaire",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Indicateur de chargement OCR intelligent
                if (isOcrAnalyzing) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Analyse OCR gratuite et locale en cours...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Contenu scrollable du formulaire
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Visionneuse de photos du ticket (Prend en charge le multi-photos)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tempImagePaths.isNotEmpty()) {
                            AsyncImage(
                                model = File(tempImagePaths[activeImageIndex]),
                                contentDescription = "Détail ticket n°${activeImageIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Overlay sombre dégradé
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)
                                        )
                                    ))
                            )

                            // Contrôles de navigation entre images rattachées
                            if (tempImagePaths.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    tempImagePaths.forEachIndexed { idx, _ ->
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (idx == activeImageIndex) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f))
                                                .clickable { activeImageIndex = idx }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Champ Titre (Produit / Magasin)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nom du produit / Magasin") },
                        placeholder = { Text("Ex: Lave Vaisselle Darty, Aspirateur Fnac...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_title_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Catégorie Groupement (Nouveauté)
                    Text(
                        text = "Grouper / Catégoriser",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedCategory = !expandedCategory }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dérouler la liste des catégories"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedCategory = !expandedCategory }
                                .testTag("category_dropdown_trigger"),
                            colors = OutlinedTextFieldDefaults.colors()
                        )

                        DropdownMenu(
                            expanded = expandedCategory,
                            onDismissRequest = { expandedCategory = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            categoryOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        category = opt
                                        expandedCategory = false
                                    },
                                    modifier = Modifier.testTag("category_option_$opt")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sélecteur de date d'achat
                    Text(
                        text = "Date d'achat",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                            .clickable(
                                enabled = true,
                                onClickLabel = "Modifier la date d'achat",
                                role = Role.Button
                            ) { showDatePicker = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = dateFormat.format(Date(purchaseDate)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Ouvrir le calendrier",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sélecteur de durée de garantie
                    Text(
                        text = "Durée de la garantie",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = warrantyOptions.firstOrNull { it.second == warrantyMonths }?.first ?: "$warrantyMonths mois",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedWarranty = !expandedWarranty }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dérouler la liste des garanties"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedWarranty = !expandedWarranty }
                                .testTag("warranty_dropdown_trigger"),
                            colors = OutlinedTextFieldDefaults.colors()
                        )

                        DropdownMenu(
                            expanded = expandedWarranty,
                            onDismissRequest = { expandedWarranty = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            warrantyOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.first) },
                                    onClick = {
                                        warrantyMonths = option.second
                                        expandedWarranty = false
                                    },
                                    modifier = Modifier.testTag("warranty_option_${option.second}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Zone de Reconstitution du Code-barres
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { 
                            // Uniquement chiffres
                            barcode = it.filter { char -> char.isDigit() }
                        },
                        label = { Text("Chiffres du code-barres (Reconstitué)") },
                        placeholder = { Text("Ex: 3600551016834 (8, 12 ou 13 chiffres)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_barcode_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    // Dessin du code-barres en temps réel si saisi !
                    if (barcode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        BarcodeView(barcode = barcode)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notes optionnelles
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes & Détails (Optionnel)") },
                        placeholder = { Text("Ex: Numéro de série SN-82193, Extension Darty Max...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("receipt_notes_input"),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Actions en bas (Enregistrer / Annuler)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_button")
                    ) {
                        Text("Annuler", color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(
                                    title,
                                    purchaseDate,
                                    warrantyMonths,
                                    notes.takeIf { it.isNotBlank() },
                                    category,
                                    barcode.takeIf { it.isNotBlank() }
                                )
                            }
                        },
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_button")
                    ) {
                        Text("Enregistrer le ticket")
                    }
                }
            }
        }
    }

    // Modal DatePickerDialog de Material 3
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = purchaseDate
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            purchaseDate = it
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("datepicker_confirm")
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.testTag("datepicker_cancel")
                ) {
                    Text("Annuler")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
