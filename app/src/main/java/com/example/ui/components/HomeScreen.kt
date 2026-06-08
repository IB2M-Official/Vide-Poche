package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.Receipt
import com.example.viewmodel.FilterType
import com.example.viewmodel.ReceiptViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Écran principal présentant la liste des garanties, le filtrage par statut et catégorie, et la recherche par texte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ReceiptViewModel,
    onNavigateToCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val receipts by viewModel.uiReceipts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var receiptToDelete by remember { mutableStateOf<Receipt?>(null) }
    var receiptDetailToShow by remember { mutableStateOf<Receipt?>(null) }
    var showTutorial by remember { mutableStateOf(false) }

    val categoriesList = listOf("Tous", "Divers", "Électronique", "Électroménager", "Mode", "Alimentation")

    // Détection automatique du tout premier lancement pour afficher le tutoriel interactif
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("vide_poche_prefs", android.content.Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run2", true)
        if (isFirstRun) {
            showTutorial = true
            prefs.edit().putBoolean("is_first_run2", false).apply()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Vide-Poche",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Garanties locales & sécurisées",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTutorial = true },
                        modifier = Modifier.testTag("help_tutorial_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Ouvrir le guide d'utilisation",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCamera,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_receipt_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Prendre un ticket en photo"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Zone de recherche textuelle
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Rechercher par titre, note, code-barres...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchQuery("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Effacer la recherche"
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_bar"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors()
            )

            // Puces de filtrage (Filter Chips) - Par statut temporel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == FilterType.ALL,
                    onClick = { viewModel.setFilter(FilterType.ALL) },
                    label = { Text("Tous les statuts") },
                    modifier = Modifier.testTag("filter_chip_all")
                )
                FilterChip(
                    selected = selectedFilter == FilterType.ACTIVE,
                    onClick = { viewModel.setFilter(FilterType.ACTIVE) },
                    label = { Text("Actifs") },
                    modifier = Modifier.testTag("filter_chip_active")
                )
                FilterChip(
                    selected = selectedFilter == FilterType.EXPIRED,
                    onClick = { viewModel.setFilter(FilterType.EXPIRED) },
                    label = { Text("Expirés") },
                    modifier = Modifier.testTag("filter_chip_expired")
                )
            }

            // Puces de filtrage (LazyRow Scrollable) - Par catégorie rattachée (Nouveauté)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categoriesList) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.setCategoryFilter(cat) },
                        label = { Text(cat) },
                        modifier = Modifier.testTag("filter_chip_category_$cat")
                    )
                }
            }

            // Liste de tickets de caisse rattachés
            if (receipts.isEmpty()) {
                EmptyStateView(
                    searchQuery = searchQuery,
                    selectedFilter = selectedFilter,
                    onNavigateToCamera = onNavigateToCamera
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("receipts_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(receipts, key = { it.id }) { receipt ->
                        ReceiptItemCard(
                            receipt = receipt,
                            onClick = { receiptDetailToShow = receipt },
                            onDeleteClick = { receiptToDelete = receipt }
                        )
                    }
                }
            }
        }
    }

    // Boîte de dialogue de confirmation de suppression
    if (receiptToDelete != null) {
        AlertDialog(
            onDismissRequest = { receiptToDelete = null },
            title = { Text("Supprimer le ticket ?") },
            text = { Text("Voulez-vous vraiment supprimer définitivement le ticket \"${receiptToDelete?.title}\" ? Cette opération effacera également ses images.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        receiptToDelete?.let { viewModel.deleteReceipt(it) }
                        receiptToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { receiptToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("Annuler")
                }
            },
            modifier = Modifier.testTag("delete_dialog")
        )
    }

    // Modal de détail complet du ticket de caisse (multi-photos + code barres reconstitué)
    if (receiptDetailToShow != null) {
        ReceiptDetailDialog(
            receipt = receiptDetailToShow!!,
            onDismiss = { receiptDetailToShow = null }
        )
    }

    // Modal du Guide / Tutoriel interactif de l'application
    if (showTutorial) {
        TutorialDialog(
            onDismiss = { showTutorial = false },
            onLaunchCamera = {
                showTutorial = false
                onNavigateToCamera()
            }
        )
    }
}

/**
 * Carte de reçu individuelle stylisée avec indicateurs de statut et catégories.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiptItemCard(
    receipt: Receipt,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }

    // Calcul de l'état restant de la garantie
    val now = System.currentTimeMillis()
    val timeLeftMillis = receipt.warrantyEndDate - now
    val daysLeft = TimeUnit.MILLISECONDS.toDays(timeLeftMillis)

    // Détermination de la couleur d'alerte :
    val (statusColor, statusText) = when {
        timeLeftMillis <= 0 -> {
            Pair(Color(0xFFD32F2F), "EXPIRÉ") // Rouge
        }
        daysLeft < 30 -> {
            Pair(Color(0xFFE65100), "$daysLeft JOURS") // Orange
        }
        daysLeft > 180 -> {
            Pair(Color(0xFF2E7D32), "$daysLeft JOURS") // Vert
        }
        else -> {
            Pair(Color(0xFF1976D2), "$daysLeft JOURS") // Bleu
        }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDeleteClick,
                onClickLabel = "Voir le détail du ticket"
            )
            .testTag("receipt_card_${receipt.id}"),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniature du ticket de caisse local (Première photo s'il y en a plusieurs)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val paths = receipt.imagePathsList
                val firstPath = paths.firstOrNull() ?: ""
                val imageFile = remember(firstPath) { File(firstPath) }
                if (imageFile.exists()) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = "Vignette du ticket pour ${receipt.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Textes d'informations
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = receipt.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Affichage de la catégorie sous forme de badge discret
                        SuggestionChip(
                            onClick = {},
                            label = { Text(receipt.category, fontSize = 10.sp) },
                            modifier = Modifier
                                .height(22.dp)
                                .padding(vertical = 0.dp)
                        )
                    }

                    // Badge indicateur de statut restant
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val subtext = if (!receipt.notes.isNullOrBlank()) {
                    receipt.notes
                } else {
                    "Achat le ${dateFormat.format(Date(receipt.purchaseDate))}"
                }

                Text(
                    text = "$subtext • Expire le ${dateFormat.format(Date(receipt.warrantyEndDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Bouton supprimer direct (48dp pour accessibilité)
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_receipt_button_${receipt.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer ce ticket",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Vue d'état vide affichée lorsqu'aucun ticket n'est disponible.
 */
@Composable
fun EmptyStateView(
    searchQuery: String,
    selectedFilter: FilterType,
    onNavigateToCamera: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.CloudQueue,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (searchQuery.isNotEmpty()) {
                    "Aucun résultat trouvé"
                } else when (selectedFilter) {
                    FilterType.ACTIVE -> "Aucune garantie active en cours"
                    FilterType.EXPIRED -> "Aucune garantie expirée"
                    FilterType.ALL -> "Votre vide-poche est vide !"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (searchQuery.isNotEmpty()) {
                    "Essayez de modifier votre recherche."
                } else {
                    "Prenez vos tickets en photo pour sécuriser et catégoriser toutes vos garanties !"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (searchQuery.isEmpty() && selectedFilter == FilterType.ALL) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onNavigateToCamera,
                    modifier = Modifier.testTag("empty_state_action_button")
                ) {
                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanner mon premier ticket")
                }
            }
        }
    }
}

/**
 * Boîte de dialogue détaillée affichant la photo plein format (ou visionneuse multi-photos) et toutes les informations du ticket.
 */
@Composable
fun ReceiptDetailDialog(
    receipt: Receipt,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE) }

    // Liste des images rattachées
    val paths = receipt.imagePathsList
    var activeImageIndex by remember { mutableStateOf(0) }
    var showFullscreenPath by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("receipt_detail_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // En-tête du détail
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = receipt.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Catégorie : ${receipt.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_detail_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer le détail"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Contenu scrollable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    // Visionneuse de photos du ticket (multi-photos) rattachées
                    if (paths.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = File(paths[activeImageIndex]),
                                    contentDescription = "Photo ${activeImageIndex + 1} du ticket de caisse",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.DarkGray)
                                        .clickable {
                                            showFullscreenPath = paths[activeImageIndex]
                                        },
                                    contentScale = ContentScale.Fit
                                )

                                // Petits points de navigation si > 1 photos
                                if (paths.size > 1) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(12.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        paths.forEachIndexed { index, _ ->
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (index == activeImageIndex) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f))
                                                    .clickable { activeImageIndex = index }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Aucune image rattachée",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date d'achat
                    ListItem(
                        headlineContent = { Text("Date d'achat") },
                        supportingContent = { Text(dateFormat.format(Date(receipt.purchaseDate))) },
                        leadingContent = {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )

                    // Fin de garantie
                    ListItem(
                        headlineContent = { Text("Fin de la garantie") },
                        supportingContent = { Text(dateFormat.format(Date(receipt.warrantyEndDate))) },
                        leadingContent = {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )

                    // Catégorie
                    ListItem(
                        headlineContent = { Text("Groupe / Catégorie") },
                        supportingContent = { Text(receipt.category) },
                        leadingContent = {
                            val catIcon = when (receipt.category) {
                                "Électronique" -> Icons.Default.Tv
                                "Électroménager" -> Icons.Default.Build
                                "Mode" -> Icons.Default.ShoppingBag
                                "Alimentation" -> Icons.Default.ShoppingCart
                                else -> Icons.Default.Category
                            }
                            Icon(catIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )

                    // Code-barres reconstitué
                    if (!receipt.barcode.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        BarcodeView(barcode = receipt.barcode)
                    }

                    // Notes rattachées
                    if (!receipt.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Notes & Détails",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = receipt.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bouton OK de fermeture
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("detail_ok_button")
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Dialogue pour afficher l'image sélectionnée en grand écran
    if (showFullscreenPath != null) {
        FullscreenImageDialog(
            imagePath = showFullscreenPath!!,
            onDismiss = { showFullscreenPath = null }
        )
    }
}
