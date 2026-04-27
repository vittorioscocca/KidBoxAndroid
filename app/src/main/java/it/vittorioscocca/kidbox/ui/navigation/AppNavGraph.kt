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
import it.vittorioscocca.kidbox.ui.screens.grocery.GroceryListScreen
import it.vittorioscocca.kidbox.ui.screens.home.HomeScreen
import it.vittorioscocca.kidbox.ui.screens.home.ProfileScreen
import it.vittorioscocca.kidbox.ui.screens.onboarding.OnboardingScreen
import it.vittorioscocca.kidbox.ui.screens.onboarding.WikiOnboardingScreen
import it.vittorioscocca.kidbox.ui.screens.documents.DocumentBrowserScreen
import it.vittorioscocca.kidbox.ui.screens.settings.family.EditChildScreen
import it.vittorioscocca.kidbox.ui.screens.settings.family.EditFamilyScreen
import it.vittorioscocca.kidbox.ui.screens.settings.family.FamilySettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.InviteCodeScreen
import it.vittorioscocca.kidbox.ui.screens.settings.JoinFamilyScreen
import it.vittorioscocca.kidbox.ui.screens.settings.MessageSettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.NotificationSettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.SettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.ThemeScreen
import it.vittorioscocca.kidbox.ui.screens.calendar.CalendarScreen
import it.vittorioscocca.kidbox.ui.screens.expenses.ExpensesHomeScreen
import it.vittorioscocca.kidbox.ui.screens.location.FamilyLocationScreen
import it.vittorioscocca.kidbox.ui.screens.notes.NoteDetailScreen
import it.vittorioscocca.kidbox.ui.screens.notes.NotesHomeScreen
import it.vittorioscocca.kidbox.ui.screens.photos.FamilyPhotosScreen
import it.vittorioscocca.kidbox.ui.screens.chat.ChatScreen
import it.vittorioscocca.kidbox.ui.screens.todo.TodoHomeScreen
import it.vittorioscocca.kidbox.ui.screens.todo.TodoListScreen

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
                        // Se non ho più una famiglia (es. revoca), devo rientrare nel wizard.
                        // hasSeenOnboarding non deve bypassare questo stato.
                        !hasFamily -> {
                            navController.navigate(AppDestination.Onboarding.route) {
                                popUpTo(AppDestination.Login.route) { inclusive = true }
                            }
                        }
                        hasSeenOnboarding -> {
                            navController.navigate(AppDestination.Home.route) {
                                popUpTo(AppDestination.Login.route) { inclusive = true }
                            }
                        }
                        else -> {
                            onboardingPreferences.completeOnboarding()
                            navController.navigate(AppDestination.Home.route) {
                                popUpTo(navController.graph.id) { inclusive = false }
                            }
                        }
                    }
                },
            )
        }

        composable(AppDestination.Onboarding.route) {
            OnboardingScreen(
                onFamilyCreated = {
                    onboardingPreferences.completeOnboarding()
                    navController.navigate(AppDestination.Home.route) {
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
                onMessageSettings = { navController.navigate(AppDestination.MessageSettings.route) },
                onNotifications = { navController.navigate(AppDestination.NotificationSettings.route) },
            )
        }

        composable(AppDestination.MessageSettings.route) {
            MessageSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(AppDestination.NotificationSettings.route) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
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
        ) {
            FamilyPhotosScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.NotesHome.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            NotesHomeScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(
            route = AppDestination.NoteDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("noteId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val noteId = backStackEntry.arguments?.getString("noteId").orEmpty()
            NoteDetailScreen(
                familyId = familyId,
                noteId = noteId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.Todo.route) {
            TodoHomeScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(
            route = AppDestination.TodoList.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("listId") { type = NavType.StringType },
                navArgument("highlightTodoId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            TodoListScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = AppDestination.TodoSmart.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("kind") { type = NavType.StringType },
            ),
        ) {
            TodoListScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = AppDestination.ShoppingList.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) {
            GroceryListScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = AppDestination.Calendar.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            CalendarScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.PediatricChildSelector.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { PlaceholderScreen("Salute") }

        composable(AppDestination.Chat.route) {
            ChatScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.ExpensesHome.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("highlightExpenseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val highlightExpenseId = backStackEntry.arguments?.getString("highlightExpenseId")
            ExpensesHomeScreen(
                familyId = familyId,
                highlightExpenseId = highlightExpenseId,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(
            route = AppDestination.DocumentsHome.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("highlightDocumentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = false
                    defaultValue = "root"
                },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val familyId = args?.getString("familyId").orEmpty()
            val highlightDocumentId = args?.getString("highlightDocumentId")
            val initialFolderId = args?.getString("folderId") ?: "root"
            DocumentBrowserScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
                initialHighlightDocumentId = highlightDocumentId,
                initialFolderId = initialFolderId,
            )
        }

        composable(
            route = AppDestination.FamilyLocation.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            FamilyLocationScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.AskExpert.route) { PlaceholderScreen("Assistente AI") }

        composable(AppDestination.FamilySettings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            FamilySettingsScreen(
                onBack = { navController.popBackStack() },
                onInvite = { navController.navigate(AppDestination.InviteCode.route) },
                onJoin = { navController.navigate(AppDestination.JoinFamily.route) },
                onEditFamily = { navController.navigate(AppDestination.EditFamily.route) },
                onLeaveDone = {
                    Log.d("AppNavGraph", "onLeaveDone -> restart app")
                    val intent = (context as android.app.Activity).packageManager
                        .getLaunchIntentForPackage(context.packageName)!!
                        .apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    context.startActivity(intent)
                    (context as android.app.Activity).finish()
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