package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Interface d'accès aux données pour la table des reçus (Receipt).
 */
@Dao
interface ReceiptDao {

    @Query("SELECT * FROM receipts ORDER BY warrantyEndDate ASC")
    fun getAllReceiptsOrderByExpiry(): Flow<List<Receipt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: Receipt): Long

    @Delete
    suspend fun deleteReceipt(receipt: Receipt)

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun getReceiptById(id: Long): Receipt?
}
