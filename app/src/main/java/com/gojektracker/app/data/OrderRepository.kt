package com.gojektracker.app.data

import kotlinx.coroutines.flow.Flow

class OrderRepository(private val orderDao: OrderDao) {
    val allOrders: Flow<List<OrderRecord>> = orderDao.getAllOrders()

    suspend fun insert(order: OrderRecord): Long {
        return orderDao.insertOrder(order)
    }

    suspend fun delete(id: Long) {
        orderDao.deleteOrder(id)
    }

    suspend fun deleteAll() {
        orderDao.deleteAllOrders()
    }
}
