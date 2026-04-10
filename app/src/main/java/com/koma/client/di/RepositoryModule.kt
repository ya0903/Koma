package com.koma.client.di

import com.koma.client.data.repo.BookmarkRepositoryImpl
import com.koma.client.data.repo.DownloadRepositoryImpl
import com.koma.client.data.repo.ReadProgressRepositoryImpl
import com.koma.client.data.repo.ServerRepositoryImpl
import com.koma.client.domain.repo.BookmarkRepository
import com.koma.client.domain.repo.DownloadRepository
import com.koma.client.domain.repo.ReadProgressRepository
import com.koma.client.domain.repo.ServerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindServerRepository(impl: ServerRepositoryImpl): ServerRepository

    @Binds
    @Singleton
    abstract fun bindReadProgressRepository(impl: ReadProgressRepositoryImpl): ReadProgressRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository
}
