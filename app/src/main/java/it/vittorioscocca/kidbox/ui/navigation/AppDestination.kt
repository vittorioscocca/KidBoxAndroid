package it.vittorioscocca.kidbox.ui.navigation

sealed class AppDestination(val route: String) {
    data object Login : AppDestination("login")
    data object Onboarding : AppDestination("onboarding")
    data object WikiOnboarding : AppDestination("wiki_onboarding/{familyId}") {
        fun createRoute(familyId: String): String = "wiki_onboarding/$familyId"
    }

    data object Home : AppDestination("home")
}
