package com.example.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Receipt
import com.example.data.ReceiptRepository
import com.example.notification.WarrantyReminderReceiver
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

/**
 * Filter state representing active filters.
 */
enum class FilterType {
    ALL,      // Tous les tickets
    ACTIVE,   // Garanties en cours (> 0 jours restant)
    EXPIRED   // Garanties expirées
}

/**
 * ReceiptViewModel gère l'état de l'interface graphique du gestionnaire "Vide-Poche".
 */
class ReceiptViewModel(
    private val app: Application,
    private val repository: ReceiptRepository
) : AndroidViewModel(app) {

    // Recherche de ticket par titre, note, code-barres, catégorie, etc.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filtre sélectionné (Tous, Actifs, Expirés)
    private val _selectedFilter = MutableStateFlow(FilterType.ALL)
    val selectedFilter: StateFlow<FilterType> = _selectedFilter.asStateFlow()

    // Filtre de catégorie sélectionnée
    private val _selectedCategory = MutableStateFlow("Tous")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Liste complète triée récupérée de Room
    val allReceipts: Flow<List<Receipt>> = repository.getAllReceipts()

    // Flux combiné pour appliquer recherche et filtres de manière réactive
    val uiReceipts: StateFlow<List<Receipt>> = combine(
        allReceipts,
        searchQuery,
        selectedFilter,
        selectedCategory
    ) { list, query, filter, catFilter ->
        val filteredBySearch = if (query.isBlank()) {
            list
        } else {
            list.filter { 
                it.title.contains(query, ignoreCase = true) ||
                (it.notes != null && it.notes.contains(query, ignoreCase = true)) ||
                (it.barcode != null && it.barcode.contains(query, ignoreCase = true)) ||
                it.category.contains(query, ignoreCase = true)
            }
        }

        val filteredByCategory = if (catFilter == "Tous") {
            filteredBySearch
        } else {
            filteredBySearch.filter { it.category.equals(catFilter, ignoreCase = true) }
        }

        val now = System.currentTimeMillis()
        when (filter) {
            FilterType.ALL -> filteredByCategory
            FilterType.ACTIVE -> filteredByCategory.filter { it.warrantyEndDate > now }
            FilterType.EXPIRED -> filteredByCategory.filter { it.warrantyEndDate <= now }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: FilterType) {
        _selectedFilter.value = filter
    }

    fun setCategoryFilter(category: String) {
        _selectedCategory.value = category
    }

    /**
     * Ajoute un reçu avec calcul automatique de la date de fin de garantie
     * et planification d'un rappel d'alarme. Supporte plusieurs photos, la catégorie et le code-barres.
     */
    fun addReceipt(
        title: String,
        purchaseDate: Long,
        warrantyMonths: Int,
        notes: String?,
        tempImagePaths: List<String>,
        category: String,
        barcode: String?
    ) {
        viewModelScope.launch {
            val finalPaths = mutableListOf<String>()

            tempImagePaths.forEachIndexed { index, tempPath ->
                val finalImageName = "receipt_${System.currentTimeMillis()}_$index.jpg"
                val finalFile = File(app.filesDir, finalImageName)
                val tempFile = File(tempPath)
                if (tempFile.exists()) {
                    try {
                        tempFile.copyTo(finalFile, overwrite = true)
                        finalPaths.add(finalFile.absolutePath)
                        tempFile.delete() // Nettoyage de l'image temp
                    } catch (e: Exception) {
                        Log.e("ReceiptViewModel", "Erreur lors de la copie de l'image temp", e)
                    }
                }
            }

            // Calcul de la date de fin de garantie
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = purchaseDate
            calendar.add(Calendar.MONTH, warrantyMonths)
            val warrantyEndDate = calendar.timeInMillis

            val newReceipt = Receipt(
                title = title.trim(),
                purchaseDate = purchaseDate,
                warrantyEndDate = warrantyEndDate,
                imagePath = finalPaths.joinToString(","),
                notes = notes?.trim(),
                category = category,
                barcode = barcode
            )

            // Insertion dans la base de données Room
            val insertedId = repository.insertReceipt(newReceipt)

            // Planifier l'alarme de rappel de fin de garantie
            scheduleWarrantyReminder(insertedId, title.trim(), warrantyEndDate)
        }
    }

    /**
     * Supprime un reçu, efface ses fichiers d'images associés pour optimiser l'espace,
     * et annule l'alarme correspondante.
     */
    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            // 1. Supprimer tous les fichiers images localement
            receipt.imagePathsList.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        Log.e("ReceiptViewModel", "Erreur lors de la suppression de l'image $path", e)
                    }
                }
            }

            // 2. Annuler l'alarme système
            cancelWarrantyReminder(receipt.id)

            // 3. Supprimer de Room
            repository.deleteReceipt(receipt)
        }
    }

    /**
     * Planifie le rappel d'expiration de la garantie (30 jours avant le terme).
     */
    private fun scheduleWarrantyReminder(receiptId: Long, title: String, warrantyEndDate: Long) {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 30 jours en millisecondes = 30 * 24 * 60 * 60 * 1000
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val reminderTime = warrantyEndDate - thirtyDaysInMillis

        val now = System.currentTimeMillis()

        // Si la garantie expire dans moins de 30 jours mais n'est pas encore passée,
        // on planifie l'alarme pour s'exécuter dans quelques secondes, ou simplement au moment actuel
        // pour avertir immédiatement l'utilisateur. Sinon, on planifie à la date voulue.
        val targetTriggerTime = when {
            reminderTime > now -> reminderTime
            warrantyEndDate > now -> now + 5000 // Alerte rapide dans 5 secondes si on est déjà dans la bande des 30 jours restants
            else -> return // Déjà expirée, aucune alarme nécessaire
        }

        val intent = Intent(app, WarrantyReminderReceiver::class.java).apply {
            putExtra(WarrantyReminderReceiver.EXTRA_RECEIPT_TITLE, title)
            putExtra(WarrantyReminderReceiver.EXTRA_RECEIPT_ID, receiptId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            app,
            receiptId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Utilise setAndAllowWhileIdle pour s'assurer que l'appareil réveille l'app en mode Doze
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTriggerTime,
                pendingIntent
            )
            Log.d("ReceiptViewModel", "Alarme configurée avec succès pour $title au timestamp: $targetTriggerTime")
        } catch (e: SecurityException) {
            Log.e("ReceiptViewModel", "Erreur lors de la planification de l'exact alarm", e)
        }
    }

    /**
     * Annule une alarme configurée pour un ticket spécifique.
     */
    private fun cancelWarrantyReminder(receiptId: Long) {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(app, WarrantyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            receiptId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

/**
 * Factory ViewModel simple pour inclure le constructeur personnalisé.
 */
class ReceiptViewModelFactory(
    private val app: Application,
    private val repository: ReceiptRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReceiptViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReceiptViewModel(app, repository) as T
        }
        throw IllegalArgumentException("ViewModel inconnu ou incompatible")
    }
}
