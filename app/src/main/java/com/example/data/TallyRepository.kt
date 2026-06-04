package com.example.data

import kotlinx.coroutines.flow.Flow

class TallyRepository(private val tallyDao: TallyDao) {
    val allItems: Flow<List<TallyItem>> = tallyDao.getAllItems()

    suspend fun insert(item: TallyItem) {
        tallyDao.insertItem(item)
    }

    suspend fun delete(id: Int) {
        tallyDao.deleteItemById(id)
    }

    suspend fun clearAll() {
        tallyDao.clearAllItems()
    }

    // Customer Bill operations
    val allBills: Flow<List<CustomerBill>> = tallyDao.getAllBills()

    suspend fun insertBill(bill: CustomerBill) {
        tallyDao.insertBill(bill)
    }

    suspend fun deleteBill(id: Int) {
        tallyDao.deleteItemById(id)
    }

    suspend fun clearAllBills() {
        tallyDao.clearAllBills()
    }

    suspend fun restoreLiveTally(items: List<TallyItem>) {
        tallyDao.clearAllItems()
        items.forEach { item ->
            // Insert the item with an auto-generated fresh ID to avoid conflicts
            tallyDao.insertItem(
                TallyItem(
                    type = item.type,
                    timestamp = item.timestamp,
                    length = item.length,
                    width = item.width,
                    thickness = item.thickness,
                    girth = item.girth,
                    useHoppusRule = item.useHoppusRule,
                    units = item.units,
                    calculatedCft = item.calculatedCft
                )
            )
        }
    }
}
