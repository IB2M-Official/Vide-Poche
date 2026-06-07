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
    val imagePath: String,              // Chemin local de l'image stockée dans context.filesDir
    val notes: String?                  // Notes optionnelles (ex: numéro de série, détails)
)
