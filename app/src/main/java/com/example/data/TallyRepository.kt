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
}
