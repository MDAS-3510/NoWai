package com.cursoandroid.nowai

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


enum class ItemType { TASK, STUDY }
enum class RepeatUnit { DAYS, WEEKS, MONTHS, YEARS }

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ItemType
)

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL // apagar categoria não apaga os itens
    )],
    indices = [Index("categoryId")]
)

data class Entities(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: ItemType,
    val categoryId: Long? = null,
    val notes: String = "",
    val repeatEvery: Int? = null,        // ex.: 2
    val repeatUnit: RepeatUnit? = null,  // ex.: WEEKS → "a cada 2 semanas"
    val lastDoneAt: Long? = null,        // epoch millis
    val nextDueAt: Long? = null,         // epoch millis
    val createdAt: Long = System.currentTimeMillis()

)
