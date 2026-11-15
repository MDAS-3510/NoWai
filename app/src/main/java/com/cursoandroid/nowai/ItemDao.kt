package com.cursoandroid.nowai

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): Item?

    // Relevância: o que vence primeiro aparece no topo.
    // Itens sem vencimento (nextDueAt nulo) vão para o final.
    @Query("""
        SELECT * FROM items
        WHERE title LIKE '%' || :search || '%'
        ORDER BY nextDueAt IS NULL, nextDueAt ASC
    """)
    fun observeByRelevance(search: String): Flow<List<Item>>
}