package com.cursoandroid.nowai


import androidx.room.TypeConverter
class Converters {

    @TypeConverter fun fromItemType(v: ItemType): String = v.name
    @TypeConverter fun toItemType(v: String): ItemType = ItemType.valueOf(v)

    @TypeConverter fun fromUnit(v: RepeatUnit?): String? = v?.name
    @TypeConverter fun toUnit(v: String?): RepeatUnit? = v?.let { RepeatUnit.valueOf(it) }
}