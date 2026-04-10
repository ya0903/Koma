package com.koma.client.di

import androidx.work.WorkerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    @Singleton
    abstract fun bindWorkerFactory(factory: HiltWorkerFactory): WorkerFactory
}
