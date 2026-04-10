# Retrofit — keep service interfaces and their annotations
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions, *Annotation*
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.koma.client.**$$serializer { *; }
-keepclassmembers class com.koma.client.** {
    *** Companion;
}
-keepclasseswithmembers class com.koma.client.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }

# Room — keep entity and DAO classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Hilt / Dagger
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}

# WorkManager workers (must be instantiable by name)
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# Coil — keep image fetcher / decoder implementations found via ServiceLoader
-keep class coil3.** { *; }
-dontwarn coil3.**

# Keep app domain models used via reflection or serialization
-keep class com.koma.client.domain.** { *; }
-keep class com.koma.client.data.db.entity.** { *; }
-keep class com.koma.client.data.server.**.dto.** { *; }
-keep class com.koma.client.work.GithubRelease { *; }
