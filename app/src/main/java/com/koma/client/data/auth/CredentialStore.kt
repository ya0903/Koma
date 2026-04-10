package com.koma.client.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class CredentialStore @Inject constructor(
    @ApplicationContext context: Context?,
) {
    private val prefs by lazy {
        requireNotNull(context) { "Context is required for CredentialStore" }
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "koma_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    open fun getUsername(serverId: String): String? = prefs.getString("${serverId}_user", null)
    open fun getPassword(serverId: String): String? = prefs.getString("${serverId}_pass", null)

    open fun store(serverId: String, username: String, password: String) {
        prefs.edit()
            .putString("${serverId}_user", username)
            .putString("${serverId}_pass", password)
            .apply()
    }

    open fun delete(serverId: String) {
        prefs.edit()
            .remove("${serverId}_user")
            .remove("${serverId}_pass")
            .remove("${serverId}_jwt")
            .apply()
    }

    open fun getJwtToken(serverId: String): String? = prefs.getString("${serverId}_jwt", null)

    open fun storeJwtToken(serverId: String, token: String) {
        prefs.edit()
            .putString("${serverId}_jwt", token)
            .apply()
    }
}
