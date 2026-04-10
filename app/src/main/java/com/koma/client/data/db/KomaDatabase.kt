package com.koma.client.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.koma.client.data.db.dao.BookDao
import com.koma.client.data.db.dao.BookmarkDao
import com.koma.client.data.db.dao.DownloadDao
import com.koma.client.data.db.dao.LibraryDao
import com.koma.client.data.db.dao.ReadProgressDao
import com.koma.client.data.db.dao.SeriesDao
import com.koma.client.data.db.dao.ServerDao
import com.koma.client.data.db.entity.BookmarkEntity
import com.koma.client.data.db.entity.CachedBookEntity
import com.koma.client.data.db.entity.CachedLibraryEntity
import com.koma.client.data.db.entity.CachedSeriesEntity
import com.koma.client.data.db.entity.DownloadEntity
import com.koma.client.data.db.entity.ReadProgressEntity
import com.koma.client.data.db.entity.ServerEntity
import com.koma.client.domain.server.MediaServerType

class MediaServerTypeConverters {
    @TypeConverter
    fun fromType(type: MediaServerType): String = type.name

    @TypeConverter
    fun toType(raw: String): MediaServerType = MediaServerType.valueOf(raw)
}

@Database(
    entities = [
        ServerEntity::class,
        CachedLibraryEntity::class,
        CachedSeriesEntity::class,
        CachedBookEntity::class,
        ReadProgressEntity::class,
        BookmarkEntity::class,
        DownloadEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(MediaServerTypeConverters::class)
abstract class KomaDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun libraryDao(): LibraryDao
    abstract fun seriesDao(): SeriesDao
    abstract fun bookDao(): BookDao
    abstract fun readProgressDao(): ReadProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao
}
