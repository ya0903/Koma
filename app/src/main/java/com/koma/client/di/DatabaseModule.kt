package com.koma.client.di

import android.content.Context
import androidx.room.Room
import com.koma.client.data.db.KomaDatabase
import com.koma.client.data.db.dao.BookDao
import com.koma.client.data.db.dao.BookmarkDao
import com.koma.client.data.db.dao.DownloadDao
import com.koma.client.data.db.dao.LibraryDao
import com.koma.client.data.db.dao.ReadProgressDao
import com.koma.client.data.db.dao.SeriesDao
import com.koma.client.data.db.dao.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KomaDatabase =
        Room.databaseBuilder(context, KomaDatabase::class.java, "koma.db")
            .fallbackToDestructiveMigration() // TODO: Replace with proper migrations before v2
            .build()

    @Provides fun provideServerDao(db: KomaDatabase): ServerDao = db.serverDao()
    // TODO: Wire CachedLibraryEntity/CachedSeriesEntity/CachedBookEntity into repositories for offline cache
    @Provides fun provideLibraryDao(db: KomaDatabase): LibraryDao = db.libraryDao()
    @Provides fun provideSeriesDao(db: KomaDatabase): SeriesDao = db.seriesDao()
    @Provides fun provideBookDao(db: KomaDatabase): BookDao = db.bookDao()
    @Provides fun provideReadProgressDao(db: KomaDatabase): ReadProgressDao = db.readProgressDao()
    @Provides fun provideBookmarkDao(db: KomaDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun provideDownloadDao(db: KomaDatabase): DownloadDao = db.downloadDao()
}
