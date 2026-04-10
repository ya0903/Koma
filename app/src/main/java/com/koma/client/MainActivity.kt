package com.koma.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.koma.client.ui.nav.KomaNavHost
import com.koma.client.ui.settings.AppPrefsKeys
import com.koma.client.ui.settings.ThemeMode
import com.koma.client.ui.settings.appPrefsDataStore
import com.koma.client.ui.theme.KomaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appPrefsDataStore.data.map { prefs ->
                prefs[AppPrefsKeys.THEME]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
            }.collectAsState(initial = ThemeMode.SYSTEM)

            val dynamicColor by appPrefsDataStore.data.map { prefs ->
                prefs[AppPrefsKeys.DYNAMIC_COLOR] ?: true
            }.collectAsState(initial = true)

            KomaTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> null
                } ?: androidx.compose.foundation.isSystemInDarkTheme(),
                dynamicColor = dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KomaNavHost()
                }
            }
        }
    }
}
