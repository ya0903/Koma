package com.koma.client.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.koma.client.data.auth.CoilAuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @BaseOkHttpClient baseOkHttpClient: OkHttpClient,
        authInterceptor: CoilAuthInterceptor,
    ): ImageLoader {
        val coilClient = baseOkHttpClient.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { coilClient }))
            }
            .crossfade(true)
            .build()
    }
}
