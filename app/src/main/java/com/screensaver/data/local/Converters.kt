package com.screensaver.data.local

import androidx.room.TypeConverter
import com.screensaver.data.local.entities.WallpaperDomain

/** Room 类型转换：实体字段 WallpaperDomain <-> 数据库 TEXT 列。 */
class Converters {

    @TypeConverter
    fun fromDomain(value: WallpaperDomain?): String? = value?.name

    @TypeConverter
    fun toDomain(value: String?): WallpaperDomain = WallpaperDomain.fromStorage(value)
}