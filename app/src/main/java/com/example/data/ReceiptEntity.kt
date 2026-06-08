package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représente un ticket de caisse et sa garantie associée.
 */
@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                  // Nom du produit ou du magasin
    val purchaseDate: Long,             // Date d'achat (timestamp)
    val warrantyEndDate: Long,          // Date de fin de garantie (timestamp)
    val imagePath: String,              // Chemin local de l'image ou liste de chemins séparés par des virgules
    val notes: String?,                 // Notes optionnelles
    val category: String = "Divers",    // Catégorie (ex: Électronique, Électroménager, Équipe, etc.)
    val barcode: String? = null         // Code-barres reconstitué
) {
    // Helper pour récupérer la liste de toutes les photos
    val imagePathsList: List<String>
        get() = if (imagePath.isBlank()) emptyList() else imagePath.split(",")
}
