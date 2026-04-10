package com.koma.client

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KomaApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun newImageLoader(context: Context): ImageLoader = imageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
