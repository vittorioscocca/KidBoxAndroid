package it.vittorioscocca.kidbox.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.ui.screens.auth.LoginScreen
import it.vittorioscocca.kidbox.ui.screens.home.HomeScreen
import it.vittorioscocca.kidbox.ui.screens.home.ProfileScreen
import it.vittorioscocca.kidbox.ui.screens.onboarding.OnboardingScreen
import it.vittorioscocca.kidbox.ui.screens.onboarding.WikiOnboardingScreen
import it.vittorioscocca.kidbox.ui.screens.settings.family.EditChildScreen
import it.vittorioscocca.kidbox.ui.screens.settings.family.EditFamilyScreen
import it.vittorioscocca.kidbox.ui.screens.settings.family.FamilySettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.InviteCodeScreen
import it.vittorioscocca.kidbox.ui.screens.settings.JoinFamilyScreen
import it.vittorioscocca.kidbox.ui.screens.settings.SettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.ThemeScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    onboardingPreferences: OnboardingPreferences,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(AppDestination.Login.route) {
            LoginScreen(
                onLoginSuccess = { hasFamily ->
                    val hasSeenOnboarding = onboardingPreferences.hasSeenOnboarding()
                    Log.d(
                        "KidBoxDebug",
                        "onLoginSuccess hasFamily=$hasFamily hasSeenOnboarding=$hasSeenOnboarding",
                    )

                    when {
                        hasSeenOnboarding -> {
                            navController.navigate(AppDestination.Home.route) {
                                popUpTo(AppDestination.Login.route) { inclusive = true }
                            }
                        }
                        hasFamily -> {
                            onboardingPreferences.completeOnboarding()
                            navController.navigate(AppDestination.Home.route) {
                                popUpTo(AppDestination.Login.route) { inclusive = true }
                            }
                        }
                        else -> {
                            navController.navigate(AppDestination.Onboarding.route) {
                                popUpTo(AppDestination.Login.route) { inclusive = true }
                            }
                        }
                    }
                },
            )
        }

        composable(AppDestination.Onboarding.route) {
            OnboardingScreen(
                onFamilyCreated = { familyId ->
                    navController.navigate(AppDestination.WikiOnboarding.createRoute(familyId)) {
                        popUpTo(AppDestination.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = AppDestination.WikiOnboarding.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            WikiOnboardingScreen(
                familyId = familyId,
                onStart = {
                    onboardingPreferences.completeOnboarding()
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(AppDestination.Home.route) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(AppDestination.Profile.route) {
            ProfileScreen(
                onLoggedOut = {
                    navController.navigate(AppDestination.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onTheme = { navController.navigate(AppDestination.Theme.route) },
                onFamilySettings = { navController.navigate(AppDestination.FamilySettings.route) },
            )
        }
        composable(AppDestination.Theme.route) {
            ThemeScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.InviteCode.route) {
            InviteCodeScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.JoinFamily.route) {
            JoinFamilyScreen(
                onBack = { navController.popBackStack() },
                onJoined = {
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(AppDestination.EditFamily.route) {
            EditFamilyScreen(
                onBack = { navController.popBackStack() },
                onEditChild = { childId ->
                    navController.navigate(AppDestination.EditChild.createRoute(childId))
                },
            )
        }
        composable(
            route = AppDestination.EditChild.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            EditChildScreen(childId = childId, onBack = { navController.popBackStack() })
        }
        composable(
            route = AppDestination.FamilyPhotos.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { PlaceholderScreen("Family Photos") }
        composable(
            route = AppDestination.NotesHome.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { PlaceholderScreen("Notes") }
        composable(AppDestination.Todo.route) { PlaceholderScreen("To-Do") }
        composable(
            route = AppDestination.ShoppingList.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { PlaceholderScreen("Lista Spesa") }
        composable(
            route = AppDestination.Calendar.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { PlaceholderScreen("Calendario") }
        composable(
            route = AppDestination.PediatricChildSelector.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { PlaceholderScreen("Salute") }
        composable(AppDestination.Chat.route) { PlaceholderScreen("Chat") }
        composable(
            route = AppDestination.ExpensesHome.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { PlaceholderScreen("Spese") }
        composable(AppDestination.DocumentsHome.route) { PlaceholderScreen("Documenti") }
        composable(
            route = AppDestination.FamilyLocation.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { PlaceholderScreen("Posizione") }
        composable(AppDestination.AskExpert.route) { PlaceholderScreen("Assistente AI") }
        composable(AppDestination.FamilySettings.route) {
            FamilySettingsScreen(
                onBack = { navController.popBackStack() },
                onInvite = { navController.navigate(AppDestination.InviteCode.route) },
                onJoin = { navController.navigate(AppDestination.JoinFamily.route) },
                onEditFamily = { navController.navigate(AppDestination.EditFamily.route) },
                onLeaveDone = {
                    navController.navigate(AppDestination.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label)
    }
}