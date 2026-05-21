package it.vittorioscocca.kidbox.ui.navigation

import androidx.navigation.NavHostController

/**
 * Torna alla Home senza rimuoverla dallo stack (evita schermata bianca se il back
 * di sistema fa più pop del necessario su alcuni device Samsung).
 */
fun NavHostController.popBackToHome(): Boolean {
    val homeRoute = AppDestination.Home.route
    if (currentDestination?.route == homeRoute) return true
    if (popBackStack(homeRoute, inclusive = false)) return true
    navigate(homeRoute) {
        launchSingleTop = true
        popUpTo(graph.id) { inclusive = false }
    }
    return true
}
