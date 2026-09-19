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
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.START) {
        composable(Routes.START) {
            StartScreen(onStart = { nav.navigate(Routes.PLAY) { popUpTo(Routes.START) { inclusive = true } } })
        }
        composable(Routes.PLAY) {
            PlayScreen(onBackToMenu = {
                nav.navigate(Routes.START) {
                    popUpTo(Routes.PLAY) { inclusive = true }
                }
            })
        }
    }
}
