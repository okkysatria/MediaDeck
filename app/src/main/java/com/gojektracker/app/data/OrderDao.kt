package com.gojektracker.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY id DESC")
    fun getAllOrders(): Flow<List<OrderRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderRecord): Long

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrder(id: Long)

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()
}
