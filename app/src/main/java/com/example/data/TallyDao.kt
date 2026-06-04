package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TallyDao {
    @Query("SELECT * FROM tally_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<TallyItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TallyItem)

    @Query("DELETE FROM tally_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    @Query("DELETE FROM tally_items")
    suspend fun clearAllItems()

    // Customer Bill operations
    @Query("SELECT * FROM customer_bills ORDER BY timestamp DESC")
    fun getAllBills(): Flow<List<CustomerBill>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: CustomerBill)

    @Query("DELETE FROM customer_bills WHERE id = :id")
    suspend fun deleteBillById(id: Int)

    @Query("DELETE FROM customer_bills")
    suspend fun clearAllBills()
}
