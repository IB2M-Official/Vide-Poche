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
     * Planifie plusieurs rappels d'expiration de la garantie (3 mois, 30 jours, 7 jours, 1 jour avant le terme).
     */
    private fun scheduleWarrantyReminder(receiptId: Long, title: String, warrantyEndDate: Long) {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        // Liste des rappels (subId unique de 1 à 4, offset en millisecondes, et message personnalisé)
        val reminders = listOf(
            Triple(1, 90L * 24 * 60 * 60 * 1000, "La garantie de \"$title\" expire dans 3 mois ! Pensez à vérifier s'il fonctionne toujours parfaitement."),
            Triple(2, 30L * 24 * 60 * 60 * 1000, "Attention : la garantie pour \"$title\" expire dans 30 jours (1 mois) !"),
            Triple(3, 7L * 24 * 60 * 60 * 1000, "Dernière semaine ! La garantie de \"$title\" prend fin dans 7 jours."),
            Triple(4, 1L * 24 * 60 * 60 * 1000, "Urgent : la garantie de votre produit \"$title\" expire DEMAIN !")
        )

        for ((subId, offset, message) in reminders) {
            val reminderTime = warrantyEndDate - offset

            // On ne planifie l'alarme que si l'échéance est future ou si on est proche mais pas encore expiré
            if (warrantyEndDate > now && reminderTime > now) {
                val intent = Intent(app, WarrantyReminderReceiver::class.java).apply {
                    putExtra(WarrantyReminderReceiver.EXTRA_RECEIPT_TITLE, title)
                    putExtra(WarrantyReminderReceiver.EXTRA_RECEIPT_ID, receiptId)
                    putExtra(WarrantyReminderReceiver.EXTRA_REMINDER_MESSAGE, message)
                }

                // Génère un code de requête unique combinant le receiptId hashcode et le subId
                val uniqueRequestCode = (receiptId.hashCode() * 31) + subId

                val pendingIntent = PendingIntent.getBroadcast(
                    app,
                    uniqueRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                    Log.d("ReceiptViewModel", "Rappel #$subId configuré avec succès pour $title au timestamp: $reminderTime ($message)")
                } catch (e: SecurityException) {
                    Log.e("ReceiptViewModel", "Erreur lors de la planification de l'exact alarm #$subId", e)
                }
            }
        }

        // Compléter par un rappel immédiat ou rapide si la garantie expire très bientôt et qu'aucune alarme n'a pu se programmer
        val oneDayInMs = 24L * 60 * 60 * 1000
        if (warrantyEndDate > now && (warrantyEndDate - now) < (30 * oneDayInMs)) {
            // Expire dans moins de 30 jours, on programme un rappel rapide dans 5 secondes pour avertir que la date est très proche
            val intent = Intent(app, WarrantyReminderReceiver::class.java).apply {
                putExtra(WarrantyReminderReceiver.EXTRA_RECEIPT_TITLE, title)
                putExtra(WarrantyReminderReceiver.EXTRA_RECEIPT_ID, receiptId)
                putExtra(WarrantyReminderReceiver.EXTRA_REMINDER_MESSAGE, "Alerte de proximité : la garantie pour \"$title\" expire très bientôt, dans moins d'un mois !")
            }
            val quickPendingIntent = PendingIntent.getBroadcast(
                app,
                (receiptId.hashCode() * 31) + 99,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + 5000, quickPendingIntent)
            } catch (e: SecurityException) {
                Log.e("ReceiptViewModel", "Erreur rappel de proximité", e)
            }
        }
    }

    /**
     * Annule toutes les alarmes configurées pour un ticket spécifique.
     */
    private fun cancelWarrantyReminder(receiptId: Long) {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(app, WarrantyReminderReceiver::class.java)

        // Annuler les 4 rappels par défaut
        for (subId in listOf(1, 2, 3, 4, 99)) {
            val uniqueRequestCode = (receiptId.hashCode() * 31) + subId
            val pendingIntent = PendingIntent.getBroadcast(
                app,
                uniqueRequestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
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
