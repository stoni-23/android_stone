package com.stoni.androidstone.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stoni.androidstone.ui.screens.PlayScreen
import com.stoni.androidstone.ui.screens.StartScreen

object Routes {
    const val START = "start"
    const val PLAY = "play"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.START
    ) {
        composable(Routes.START) {
            StartScreen(
                onPlayClick = { navController.navigate(Routes.PLAY) }
            )
        }
        composable(Routes.PLAY) {
            PlayScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
