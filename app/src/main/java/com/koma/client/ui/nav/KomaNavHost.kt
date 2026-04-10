package com.koma.client.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koma.client.domain.model.MediaType
import com.koma.client.ui.auth.AddServerScreen
import com.koma.client.ui.home.HomeScreen
import com.koma.client.ui.reader.epub.EpubReaderScreen
import com.koma.client.ui.reader.image.ImageReaderScreen

@Composable
fun KomaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddServer = { navController.navigate(Routes.ADD_SERVER) },
                onGoToLibraries = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }
        composable(Routes.ADD_SERVER) {
            AddServerScreen(
                onServerAdded = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            KomaAppShell(
                onOpenReader = { bookId, mediaType ->
                    when (mediaType) {
                        MediaType.EPUB -> navController.navigate(Routes.epubReader(bookId))
                        else -> navController.navigate(Routes.imageReader(bookId))
                    }
                },
                onAddServer = {
                    navController.navigate(Routes.ADD_SERVER)
                },
            )
        }
        composable(Routes.IMAGE_READER) {
            ImageReaderScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EPUB_READER) {
            EpubReaderScreen(onBack = { navController.popBackStack() })
        }
    }
}
