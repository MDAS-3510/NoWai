package com.cursoandroid.nowai.data

import kotlinx.coroutines.flow.Flow

class NoWaiRepository(private val db: AppDatabase) {

    private val itemDao = db.itemDao()
    private val categoryDao = db.categoryDao()

    fun observeItems(search: String = ""): Flow<List<Item>> =
        itemDao.observeByRelevance(search)

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    suspend fun addItem(item: Item) = itemDao.insert(item)
    suspend fun updateItem(item: Item) = itemDao.update(item)
    suspend fun deleteItem(item: Item) = itemDao.delete(item)

    suspend fun addCategory(category: Category) = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)
}