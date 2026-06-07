package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Interface du dépôt pour la gestion des tickets de caisse.
 */
interface ReceiptRepository {
    fun getAllReceipts(): Flow<List<Receipt>>
    suspend fun insertReceipt(receipt: Receipt): Long
    suspend fun deleteReceipt(receipt: Receipt)
    suspend fun getReceiptById(id: Long): Receipt?
}

/**
 * Implémentation concrète locale du dépôt.
 */
class LocalReceiptRepository(private val receiptDao: ReceiptDao) : ReceiptRepository {

    override fun getAllReceipts(): Flow<List<Receipt>> {
        return receiptDao.getAllReceiptsOrderByExpiry()
    }

    override suspend fun insertReceipt(receipt: Receipt): Long {
        return receiptDao.insertReceipt(receipt)
    }

    override suspend fun deleteReceipt(receipt: Receipt) {
        return receiptDao.deleteReceipt(receipt)
    }

    override suspend fun getReceiptById(id: Long): Receipt? {
        return receiptDao.getReceiptById(id)
    }
}
