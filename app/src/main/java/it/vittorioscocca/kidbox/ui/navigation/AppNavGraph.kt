package it.vittorioscocca.kidbox.ui.navigation

import it.vittorioscocca.kidbox.util.KBLog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.notifications.NotificationDeepLinkRouter
import it.vittorioscocca.kidbox.notifications.nudge.NudgeDestination
import it.vittorioscocca.kidbox.ui.BroadcastMessageDialog
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import it.vittorioscocca.kidbox.ui.family.FamilySwitcherViewModel
import it.vittorioscocca.kidbox.ui.screens.auth.LoginScreen
import it.vittorioscocca.kidbox.ui.screens.grocery.GroceryListScreen
import it.vittorioscocca.kidbox.ui.screens.homeitems.HomeItemDetailScreen
import it.vittorioscocca.kidbox.ui.screens.homeitems.HomeItemsScreen
import it.vittorioscocca.kidbox.ui.screens.homeitems.HousePaymentDetailScreen
import it.vittorioscocca.kidbox.ui.screens.pets.PetDetailScreen
import it.vittorioscocca.kidbox.ui.screens.pets.PetsScreen
import it.vittorioscocca.kidbox.ui.screens.vehicles.VehicleDetailScreen
import it.vittorioscocca.kidbox.ui.screens.vehicles.VehicleInterventionsListScreen
import it.vittorioscocca.kidbox.ui.screens.vehicles.VehiclesScreen
import it.vittorioscocca.kidbox.ui.screens.home.HomeScreen
import it.vittorioscocca.kidbox.ui.screens.home.ProfileScreen
import it.vittorioscocca.kidbox.ui.screens.home.SuggestionsScreen
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
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AIChatScreen
import it.vittorioscocca.kidbox.ui.screens.ai.planning.PlanningAIChatScreen
import it.vittorioscocca.kidbox.ui.screens.settings.AiSettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.PrivacySettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.SettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.support.SupportChatScreen
import it.vittorioscocca.kidbox.ui.screens.settings.AutoFillSettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.StorageUsageScreen
import it.vittorioscocca.kidbox.ui.screens.settings.ThemeScreen
import it.vittorioscocca.kidbox.ui.screens.settings.LanguageScreen
import it.vittorioscocca.kidbox.ui.screens.settings.GuideWebViewScreen
import it.vittorioscocca.kidbox.ui.subscription.PlansScreen
import it.vittorioscocca.kidbox.ui.screens.calendar.CalendarScreen
import it.vittorioscocca.kidbox.ui.screens.expenses.ExpensesHomeScreen
import it.vittorioscocca.kidbox.ui.screens.location.FamilyLocationScreen
import it.vittorioscocca.kidbox.ui.screens.location.geofence.GeofenceEditScreen
import it.vittorioscocca.kidbox.ui.screens.location.geofence.GeofenceListScreen
import it.vittorioscocca.kidbox.ui.screens.wallet.WalletHomeScreen
import it.vittorioscocca.kidbox.ui.screens.wallet.WalletTicketDetailScreen
import it.vittorioscocca.kidbox.ui.screens.passwords.AddPasswordScreen
import it.vittorioscocca.kidbox.ui.screens.passwords.PasswordDetailScreen
import it.vittorioscocca.kidbox.ui.screens.passwords.PasswordGroupDetailScreen
import it.vittorioscocca.kidbox.ui.screens.passwords.PasswordsGroupsScreen
import it.vittorioscocca.kidbox.ui.screens.passwords.PasswordsHomeScreen
import it.vittorioscocca.kidbox.ui.screens.passwords.PasswordsImportExportScreen
import it.vittorioscocca.kidbox.ui.screens.passwords.PasswordsSettingsScreen
import it.vittorioscocca.kidbox.ui.screens.passwords.PasswordsSecurityScreen
import it.vittorioscocca.kidbox.ui.screens.notes.NoteDetailScreen
import it.vittorioscocca.kidbox.ui.screens.notes.NotesHomeScreen
import it.vittorioscocca.kidbox.ui.screens.photos.FamilyPhotosScreen
import it.vittorioscocca.kidbox.ui.screens.photos.PhotoAlbumDetailScreen
import it.vittorioscocca.kidbox.ui.screens.chat.ChatMediaGalleryScreen
import it.vittorioscocca.kidbox.ui.screens.chat.ChatScreen
import it.vittorioscocca.kidbox.ui.screens.chat.ChatViewModel
import it.vittorioscocca.kidbox.ui.screens.todo.TodoHomeScreen
import it.vittorioscocca.kidbox.ui.screens.todo.TodoListScreen
import it.vittorioscocca.kidbox.ui.screens.health.HealthSubjectSelectorScreen
import it.vittorioscocca.kidbox.ui.screens.health.HealthHomeScreen
import it.vittorioscocca.kidbox.ui.screens.health.HealthConnectAppScreen
import it.vittorioscocca.kidbox.ui.screens.health.ClinicalRecordScreen
import it.vittorioscocca.kidbox.ui.screens.health.MedicalRecordScreen
import it.vittorioscocca.kidbox.ui.screens.health.visits.MedicalVisitsScreen
import it.vittorioscocca.kidbox.ui.screens.health.visits.MedicalVisitFormScreen
import it.vittorioscocca.kidbox.health.visits.ai.VisitAiChatScreen
import it.vittorioscocca.kidbox.ui.screens.health.visits.MedicalVisitDetailScreen
import it.vittorioscocca.kidbox.ui.screens.health.exams.MedicalExamsScreen
import it.vittorioscocca.kidbox.ui.screens.health.exams.MedicalExamFormScreen
import it.vittorioscocca.kidbox.health.exams.ai.ExamAiChatScreen
import it.vittorioscocca.kidbox.health.exams.ai.ExamsListAiChatScreen
import it.vittorioscocca.kidbox.ui.screens.health.exams.MedicalExamDetailScreen
import it.vittorioscocca.kidbox.ui.screens.health.treatments.MedicalTreatmentsScreen
import it.vittorioscocca.kidbox.ui.screens.health.treatments.MedicalTreatmentFormScreen
import it.vittorioscocca.kidbox.ui.screens.health.treatments.MedicalTreatmentDetailScreen
import it.vittorioscocca.kidbox.ui.screens.health.vaccines.MedicalVaccinesScreen
import it.vittorioscocca.kidbox.ui.screens.health.vaccines.MedicalVaccineFormScreen
import it.vittorioscocca.kidbox.ui.screens.health.timeline.HealthTimelineScreen
import it.vittorioscocca.kidbox.ui.screens.health.ai.HealthAIChatScreen
import it.vittorioscocca.kidbox.ui.screens.travel.TravelCategoryResultsScreen
import it.vittorioscocca.kidbox.ui.screens.travel.TravelDetailScreen
import it.vittorioscocca.kidbox.ui.screens.travel.TravelItineraryStopContext
import it.vittorioscocca.kidbox.ui.screens.travel.TravelPlaceDetailScreen
import it.vittorioscocca.kidbox.ui.screens.travel.decodeNavArg
import it.vittorioscocca.kidbox.ui.screens.travel.TravelDestinationDetailScreen
import it.vittorioscocca.kidbox.ui.screens.travel.TravelDiscoverScreen
import it.vittorioscocca.kidbox.ui.screens.travel.TravelDiscoverViewModel
import it.vittorioscocca.kidbox.ui.screens.travel.TravelAllTripsScreen
import it.vittorioscocca.kidbox.ui.screens.travel.TravelListScreen
import it.vittorioscocca.kidbox.ui.screens.travel.TravelProposalRoute
import it.vittorioscocca.kidbox.ui.screens.travel.TravelWizardScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    onboardingPreferences: OnboardingPreferences,
) {
    val pendingAiRoute by NotificationDeepLinkRouter.pendingRoute.collectAsStateWithLifecycle()
    val pendingFamilyId by NotificationDeepLinkRouter.pendingFamilyId.collectAsStateWithLifecycle()
    val familySwitcherVm: FamilySwitcherViewModel = hiltViewModel()
    val activeFamilyId by familySwitcherVm.activeFamilyId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAiRoute, pendingFamilyId, activeFamilyId, navController.currentBackStackEntry) {
        val route = pendingAiRoute ?: return@LaunchedEffect
        val current = navController.currentDestination?.route ?: return@LaunchedEffect
        if (current == AppDestination.Login.route || current == AppDestination.Onboarding.route) {
            return@LaunchedEffect
        }
        val targetFamilyId = pendingFamilyId
        if (!targetFamilyId.isNullOrBlank() && targetFamilyId != activeFamilyId) {
            KBLog.navigation.info(
                "DeepLink: family switch required ${activeFamilyId ?: "nil"} → $targetFamilyId, deferring navigate",
                "NotificationDeepLink",
            )
            familySwitcherVm.switchToFamily(targetFamilyId)
            return@LaunchedEffect
        }
        navController.navigate(route) { launchSingleTop = true }
        NotificationDeepLinkRouter.clear()
    }

    // Annuncio dalla console admin: non è una destinazione, quindi vive sopra il
    // NavHost e non tocca il back stack.
    val pendingBroadcast by NotificationDeepLinkRouter.pendingBroadcast.collectAsStateWithLifecycle()
    BroadcastMessageDialog(
        message = pendingBroadcast,
        onDismiss = {
            pendingBroadcast?.campaignId?.let { KBAnalytics.logNudge("nudge_dismissed", it) }
            NotificationDeepLinkRouter.clearBroadcast()
        },
        onAction = { msg ->
            msg.campaignId?.let { KBAnalytics.logNudge("nudge_opened", it) }
            // Si chiude PRIMA di navigare: lasciare il dialog aperto sopra la
            // destinazione la coprirebbe.
            NotificationDeepLinkRouter.clearBroadcast()
            val fid = activeFamilyId
            val route = when (msg.destination) {
                NudgeDestination.INVITE -> AppDestination.InviteCode.route
                // Le sezioni di famiglia hanno bisogno della famiglia attiva.
                // Se manca (utente senza famiglia) l'unica destinazione sensata
                // resta l'invito, che di famiglia non ha bisogno.
                null -> null
                else -> if (fid.isNullOrBlank()) {
                    AppDestination.InviteCode.route
                } else {
                    when (msg.destination) {
                        NudgeDestination.DOCUMENTS -> AppDestination.DocumentsHome.createRoute(fid)
                        NudgeDestination.WALLET -> AppDestination.WalletHome.createRoute(fid)
                        NudgeDestination.HEALTH ->
                            AppDestination.PediatricChildSelector.createRoute(fid)
                        NudgeDestination.AI -> AppDestination.AiChat.createRoute(fid)
                        NudgeDestination.CHAT -> AppDestination.Chat.route
                        NudgeDestination.CALENDAR -> AppDestination.Calendar.createRoute(fid)
                        else -> null
                    }
                }
            }
            route?.let { navController.navigate(it) { launchSingleTop = true } }
        },
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(AppDestination.Login.route) {
            LoginScreen(
                onLoginSuccess = { hasFamily ->
                    val hasSeenOnboarding = onboardingPreferences.hasSeenOnboarding()
                    KBLog.navigation.debug(
                        "onLoginSuccess hasFamily=$hasFamily hasSeenOnboarding=$hasSeenOnboarding",
                        "KidBoxDebug",
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
                onNavigate = { route ->
                    navController.navigate(route) {
                        when (route) {
                            AppDestination.Profile.route,
                            AppDestination.Settings.route,
                            -> launchSingleTop = true
                            AppDestination.Plans.route -> launchSingleTop = true
                        }
                    }
                },
                onReloadHome = {
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo(AppDestination.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(AppDestination.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackToHome() },
                onLoggedOut = {
                    navController.navigate(AppDestination.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onStorageUsage = { navController.navigate(AppDestination.StorageUsage.route) },
                onOpenPlans = {
                    navController.navigate(AppDestination.Plans.route) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackToHome() },
                onTheme = { navController.navigate(AppDestination.Theme.route) },
                onLanguage = { navController.navigate(AppDestination.Language.route) },
                onUsageGuide = { navController.navigate(AppDestination.UsageGuide.route) },
                onFamilySettings = { navController.navigate(AppDestination.FamilySettings.route) },
                onMessageSettings = { navController.navigate(AppDestination.MessageSettings.route) },
                onNotifications = { navController.navigate(AppDestination.NotificationSettings.route) },
                onAiSettings = { navController.navigate(AppDestination.AiSettings.route) },
                onStorageUsage = { navController.navigate(AppDestination.StorageUsage.route) },
                onAutoFillSettings = { navController.navigate(AppDestination.AutoFillSettings.route) },
                onPrivacySettings = { navController.navigate(AppDestination.PrivacySettings.route) },
                onSupportChat = {
                    navController.navigate(AppDestination.SupportChat.route) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(AppDestination.SupportChat.route) {
            SupportChatScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.PrivacySettings.route) {
            PrivacySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(AppDestination.AutoFillSettings.route) {
            AutoFillSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(AppDestination.Plans.route) {
            PlansScreen(onDismiss = { navController.popBackStack() })
        }

        composable(AppDestination.AiSettings.route) {
            AiSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPlans = {
                    navController.navigate(AppDestination.Plans.route) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(AppDestination.StorageUsage.route) {
            StorageUsageScreen(
                onBack = { navController.popBackStack() },
                onOpenPlans = {
                    navController.navigate(AppDestination.Plans.route) {
                        launchSingleTop = true
                    }
                },
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

        composable(AppDestination.Language.route) {
            LanguageScreen(onBack = { navController.popBackStack() })
        }

        composable(AppDestination.UsageGuide.route) {
            GuideWebViewScreen(onBack = { navController.popBackStack() })
        }

        composable(AppDestination.Suggestions.route) {
            SuggestionsScreen(onBack = { navController.popBackStack() })
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
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("initialAlbumId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            FamilyPhotosScreen(
                onBack = { navController.popBackStack() },
                onOpenAlbumDetail = { albumId, albumTitle ->
                    navController.navigate(
                        AppDestination.PhotoAlbumDetail.createRoute(
                            familyId = familyId,
                            albumId = albumId,
                            albumTitle = albumTitle,
                        ),
                    )
                },
            )
        }

        composable(
            route = AppDestination.PhotoAlbumDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("albumId") { type = NavType.StringType },
                navArgument("albumTitle") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isTripAlbum") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            PhotoAlbumDetailScreen(onBack = { navController.popBackStack() })
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
                navArgument("isNewNote") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val noteId = backStackEntry.arguments?.getString("noteId").orEmpty()
            val isNewNote = backStackEntry.arguments?.getBoolean("isNewNote") ?: false
            NoteDetailScreen(
                familyId = familyId,
                noteId = noteId,
                isNewNote = isNewNote,
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
            route = AppDestination.Pets.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            PetsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenPet = { petId ->
                    navController.navigate(AppDestination.PetDetail.createRoute(familyId, petId))
                },
            )
        }

        composable(
            route = AppDestination.PetTreatmentForm.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("petId") { type = NavType.StringType },
                navArgument("treatmentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val petId = backStackEntry.arguments?.getString("petId").orEmpty()
            val treatmentId = backStackEntry.arguments?.getString("treatmentId")
            MedicalTreatmentFormScreen(
                familyId = familyId,
                childId = "",
                petId = petId,
                treatmentId = treatmentId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.PetTreatmentDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("petId") { type = NavType.StringType },
                navArgument("treatmentId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val petId = backStackEntry.arguments?.getString("petId").orEmpty()
            val treatmentId = backStackEntry.arguments?.getString("treatmentId").orEmpty()
            MedicalTreatmentDetailScreen(
                familyId = familyId,
                childId = "",
                petId = petId,
                treatmentId = treatmentId,
                onBack = { navController.popBackStack() },
                onEdit = {
                    navController.navigate(AppDestination.PetTreatmentForm.routeEdit(familyId, petId, treatmentId))
                },
                onOpenVisit = { },
            )
        }

        composable(
            route = AppDestination.PetDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("petId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val petId = backStackEntry.arguments?.getString("petId").orEmpty()
            PetDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddTreatment = {
                    navController.navigate(AppDestination.PetTreatmentForm.routeNew(familyId, petId))
                },
                onOpenTreatment = { tid ->
                    navController.navigate(AppDestination.PetTreatmentDetail.route(familyId, petId, tid))
                },
            )
        }

        composable(
            route = AppDestination.HomeItems.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            HomeItemsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenItem = { itemId ->
                    navController.navigate(AppDestination.HomeItemDetail.createRoute(familyId, itemId))
                },
                onOpenHousePayment = { paymentId ->
                    navController.navigate(AppDestination.HousePaymentDetail.createRoute(familyId, paymentId))
                },
            )
        }

        composable(
            route = AppDestination.HousePaymentDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("paymentId") { type = NavType.StringType },
            ),
        ) {
            HousePaymentDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = AppDestination.HomeItemDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("itemId") { type = NavType.StringType },
            ),
        ) {
            HomeItemDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = AppDestination.Vehicles.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            VehiclesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenVehicle = { vehicleId ->
                    navController.navigate(AppDestination.VehicleDetail.createRoute(familyId, vehicleId))
                },
            )
        }

        composable(
            route = AppDestination.VehicleDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("vehicleId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val vehicleId = backStackEntry.arguments?.getString("vehicleId").orEmpty()
            VehicleDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onSeeAllInterventions = {
                    navController.navigate(AppDestination.VehicleInterventions.createRoute(familyId, vehicleId))
                },
            )
        }

        composable(
            route = AppDestination.VehicleInterventions.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("vehicleId") { type = NavType.StringType },
            ),
        ) {
            VehicleInterventionsListScreen(onNavigateBack = { navController.popBackStack() })
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
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            HealthSubjectSelectorScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onSelect = { childId ->
                    navController.navigate(AppDestination.HealthHome.route(familyId, childId))
                },
            )
        }

        composable(
            route = AppDestination.HealthHome.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            HealthHomeScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(
            route = AppDestination.MedicalRecord.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            MedicalRecordScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.ClinicalRecord.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            ClinicalRecordScreen(
                familyId = familyId,
                childId = childId,
                subjectName = "",
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.HealthConnectApp.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            HealthConnectAppScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.MedicalVisits.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            MedicalVisitsScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
                onAdd = {
                    navController.navigate(AppDestination.MedicalVisitForm.route(familyId, childId))
                },
                onOpen = { visitId ->
                    navController.navigate(AppDestination.MedicalVisitDetail.route(familyId, childId, visitId))
                },
                onOpenVisitsListAiChat = { subjectName, visitIdsJson ->
                    navController.navigate(
                        AppDestination.VisitsListAiChat.route(
                            familyId = familyId,
                            childId = childId,
                            subjectName = subjectName,
                            visitIdsJson = visitIdsJson,
                            isListMode = true,
                        ),
                    )
                },
            )
        }

        // MedicalVisitForm must be registered BEFORE MedicalVisitDetail so that the literal
        // "form" path segment takes precedence over the parameterized {visitId} segment.
        composable(
            route = AppDestination.MedicalVisitForm.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("visitId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val visitId = backStackEntry.arguments?.getString("visitId")
            MedicalVisitFormScreen(
                familyId = familyId,
                childId = childId,
                visitId = visitId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.VisitAiChat.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("visitId") { type = NavType.StringType },
                navArgument("subjectName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("visitTitle") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("visitDate") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("diagnosis") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("notes") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val visitIdArg = backStackEntry.arguments?.getString("visitId").orEmpty()
            val subjectNameArg = backStackEntry.arguments?.getString("subjectName").orEmpty()
            VisitAiChatScreen(
                visitId = visitIdArg,
                subjectName = subjectNameArg,
                onBack = { navController.popBackStack() },
                onOpenAiSettings = { navController.navigate(AppDestination.AiSettings.route) },
            )
        }

        composable(
            route = AppDestination.VisitsListAiChat.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("subjectName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("visitIdsJson") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isListMode") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val subjectNameArg = backStackEntry.arguments?.getString("subjectName").orEmpty()
            VisitAiChatScreen(
                visitId = "",
                subjectName = subjectNameArg,
                onBack = { navController.popBackStack() },
                onOpenAiSettings = { navController.navigate(AppDestination.AiSettings.route) },
            )
        }

        composable(
            route = AppDestination.MedicalVisitDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("visitId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val visitId = backStackEntry.arguments?.getString("visitId").orEmpty()
            MedicalVisitDetailScreen(
                familyId = familyId,
                childId = childId,
                visitId = visitId,
                onBack = { navController.popBackStack() },
                onEdit = {
                    navController.navigate(
                        AppDestination.MedicalVisitForm.route(familyId, childId, visitId)
                    )
                },
                onOpenTreatment = { treatmentId ->
                    navController.navigate(AppDestination.TreatmentDetail.route(familyId, childId, treatmentId))
                },
                onOpenExam = { examId ->
                    navController.navigate(AppDestination.MedicalExamDetail.route(familyId, childId, examId))
                },
                onOpenVisitAiChat = { subjectName, visitTitle, visitDate, diagnosis, notes ->
                    navController.navigate(
                        AppDestination.VisitAiChat.route(
                            familyId = familyId,
                            childId = childId,
                            visitId = visitId,
                            subjectName = subjectName,
                            visitTitle = visitTitle,
                            visitDate = visitDate,
                            diagnosis = diagnosis,
                            notes = notes,
                        ),
                    )
                },
            )
        }

        composable(
            route = AppDestination.Vaccines.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            MedicalVaccinesScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
                onAdd = {
                    navController.navigate(AppDestination.VaccineForm.routeNew(familyId, childId))
                },
                onOpen = { vaccineId ->
                    navController.navigate(AppDestination.VaccineForm.routeEdit(familyId, childId, vaccineId))
                },
            )
        }

        composable(
            route = AppDestination.VaccineForm.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("vaccineId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val vaccineId = backStackEntry.arguments?.getString("vaccineId")
            MedicalVaccineFormScreen(
                familyId = familyId,
                childId = childId,
                vaccineId = vaccineId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.MedicalExams.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            MedicalExamsScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
                onAdd = {
                    navController.navigate(AppDestination.MedicalExamForm.routeNew(familyId, childId))
                },
                onOpen = { examId ->
                    navController.navigate(AppDestination.MedicalExamDetail.route(familyId, childId, examId))
                },
                onOpenExamsListAiChat = { subjectName, examIdsJson ->
                    navController.navigate(
                        AppDestination.ExamsListAiChat.route(
                            familyId = familyId,
                            childId = childId,
                            subjectName = subjectName,
                            examIdsJson = examIdsJson,
                            isListMode = true,
                        ),
                    )
                },
            )
        }

        // MedicalExamForm must be registered BEFORE MedicalExamDetail to avoid route conflicts.
        composable(
            route = AppDestination.MedicalExamForm.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("examId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val examId = backStackEntry.arguments?.getString("examId")
            MedicalExamFormScreen(
                familyId = familyId,
                childId = childId,
                examId = examId,
                onBack = { navController.popBackStack() },
                onSaved = { _, _, _ -> navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.ExamsListAiChat.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("subjectName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("examIdsJson") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isListMode") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val subjectNameArg = backStackEntry.arguments?.getString("subjectName").orEmpty()
            ExamsListAiChatScreen(
                subjectName = subjectNameArg,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.ExamAiChat.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("examId") { type = NavType.StringType },
                navArgument("subjectName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("examName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("examStatus") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("deadline") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("preparation") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("resultText") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("notes") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("attachmentsSummary") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val examIdArg = backStackEntry.arguments?.getString("examId").orEmpty()
            val examNameArg = backStackEntry.arguments?.getString("examName").orEmpty()
            val subjectNameArg = backStackEntry.arguments?.getString("subjectName").orEmpty()
            ExamAiChatScreen(
                examId = examIdArg,
                examName = examNameArg,
                subjectName = subjectNameArg,
                onBack = { navController.popBackStack() },
                onOpenAiSettings = { navController.navigate(AppDestination.AiSettings.route) },
            )
        }

        composable(
            route = AppDestination.MedicalExamDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("examId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val examId = backStackEntry.arguments?.getString("examId").orEmpty()
            MedicalExamDetailScreen(
                familyId = familyId,
                childId = childId,
                examId = examId,
                onBack = { navController.popBackStack() },
                onEdit = {
                    navController.navigate(AppDestination.MedicalExamForm.routeEdit(familyId, childId, examId))
                },
                onOpenVisit = { visitId ->
                    navController.navigate(AppDestination.MedicalVisitDetail.route(familyId, childId, visitId))
                },
                onOpenExamAiChat = { subjectName, examName, examStatus, deadline, preparation, resultText, notes, attachmentsSummary ->
                    navController.navigate(
                        AppDestination.ExamAiChat.route(
                            familyId = familyId,
                            childId = childId,
                            examId = examId,
                            subjectName = subjectName,
                            examName = examName,
                            examStatus = examStatus,
                            deadline = deadline,
                            preparation = preparation,
                            resultText = resultText,
                            notes = notes,
                            attachmentsSummary = attachmentsSummary,
                        ),
                    )
                },
            )
        }

        composable(
            route = AppDestination.Treatments.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            MedicalTreatmentsScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
                onAdd = {
                    navController.navigate(AppDestination.TreatmentForm.routeNew(familyId, childId))
                },
                onOpen = { treatmentId ->
                    navController.navigate(AppDestination.TreatmentDetail.route(familyId, childId, treatmentId))
                },
            )
        }

        // TreatmentForm must be registered BEFORE TreatmentDetail to avoid route conflicts.
        composable(
            route = AppDestination.TreatmentForm.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("treatmentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val treatmentId = backStackEntry.arguments?.getString("treatmentId")
            MedicalTreatmentFormScreen(
                familyId = familyId,
                childId = childId,
                petId = "",
                treatmentId = treatmentId,
                onBack = { navController.popBackStack() },
                onSaved = { _: String -> navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.TreatmentDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("treatmentId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            val treatmentId = backStackEntry.arguments?.getString("treatmentId").orEmpty()
            MedicalTreatmentDetailScreen(
                familyId = familyId,
                childId = childId,
                petId = "",
                treatmentId = treatmentId,
                onBack = { navController.popBackStack() },
                onEdit = {
                    navController.navigate(AppDestination.TreatmentForm.routeEdit(familyId, childId, treatmentId))
                },
                onOpenVisit = { visitId ->
                    navController.navigate(AppDestination.MedicalVisitDetail.route(familyId, childId, visitId))
                },
            )
        }

        composable(
            route = AppDestination.HealthTimeline.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            HealthTimelineScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
                onOpenVisit = { visitId ->
                    navController.navigate(AppDestination.MedicalVisitDetail.route(familyId, childId, visitId))
                },
                onOpenExam = { examId ->
                    navController.navigate(AppDestination.MedicalExamDetail.route(familyId, childId, examId))
                },
                onOpenTreatment = { treatmentId ->
                    navController.navigate(AppDestination.TreatmentDetail.route(familyId, childId, treatmentId))
                },
                onOpenVaccine = { vaccineId ->
                    navController.navigate(AppDestination.VaccineForm.routeEdit(familyId, childId, vaccineId))
                },
            )
        }

        composable(
            route = AppDestination.HealthAIChat.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("childId") { type = NavType.StringType },
                navArgument("subjectName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("visitIdsJson") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("examIdsJson") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("treatmentIdsJson") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("vaccineIdsJson") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            HealthAIChatScreen(
                familyId = familyId,
                childId = childId,
                onBack = { navController.popBackStack() },
                onOpenAiSettings = { navController.navigate(AppDestination.AiSettings.route) },
            )
        }

        composable(AppDestination.Chat.route) { backStackEntry ->
            val chatViewModel: ChatViewModel = hiltViewModel(backStackEntry)
            val pendingMsgId by NotificationDeepLinkRouter.pendingChatMessageId.collectAsStateWithLifecycle()
            LaunchedEffect(pendingMsgId) {
                val id = pendingMsgId ?: return@LaunchedEffect
                chatViewModel.highlightMessage(id)
                NotificationDeepLinkRouter.clearChatMessageId()
            }
            ChatScreen(
                onBack = { navController.popBackStack() },
                onNavigateToGallery = { familyId ->
                    navController.navigate(AppDestination.ChatMediaGallery.createRoute(familyId))
                },
                viewModel = chatViewModel,
            )
        }

        composable(
            route = AppDestination.ChatMediaGallery.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            // Share the ChatViewModel instance that is already alive for the Chat destination
            // so highlightMessage() takes effect as soon as we pop back.
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(AppDestination.Chat.route)
            }
            val viewModel: ChatViewModel = hiltViewModel(parentEntry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ChatMediaGalleryScreen(
                messages = state.messages,
                onDismiss = { navController.popBackStack() },
                onGoToMessage = { msgId ->
                    viewModel.highlightMessage(msgId)
                    navController.popBackStack()
                },
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
                navArgument("initialCategoryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val highlightExpenseId = backStackEntry.arguments?.getString("highlightExpenseId")
            val initialCategoryId = decodeNavArg(backStackEntry.arguments?.getString("initialCategoryId"))
                .takeIf { it.isNotBlank() }
            ExpensesHomeScreen(
                familyId = familyId,
                highlightExpenseId = highlightExpenseId,
                initialCategoryId = initialCategoryId,
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
                onGeofences = {
                    navController.navigate(AppDestination.GeofenceList.createRoute(familyId))
                },
            )
        }

        composable(
            route = AppDestination.GeofenceList.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            GeofenceListScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onAdd = {
                    navController.navigate(AppDestination.GeofenceEdit.createRoute(familyId))
                },
                onEdit = { geofenceId ->
                    navController.navigate(AppDestination.GeofenceEdit.createRoute(familyId, geofenceId))
                },
            )
        }

        composable(
            route = AppDestination.GeofenceEdit.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("geofenceId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            GeofenceEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.WalletHome.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            WalletHomeScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onTicketClick = { ticketId ->
                    navController.navigate(AppDestination.WalletDetail.createRoute(familyId, ticketId))
                },
                onDocumentClick = { documentId ->
                    navController.navigate(AppDestination.WalletDocumentDetail.createRoute(familyId, documentId))
                },
                onUpgrade = { navController.navigate(AppDestination.Plans.route) },
            )
        }

        composable(
            route = AppDestination.WalletDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("ticketId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val ticketId = backStackEntry.arguments?.getString("ticketId").orEmpty()
            WalletTicketDetailScreen(
                familyId = familyId,
                ticketId = ticketId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.WalletDocumentDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("documentId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val documentId = backStackEntry.arguments?.getString("documentId").orEmpty()
            it.vittorioscocca.kidbox.ui.screens.wallet.documents.WalletDocumentDetailScreen(
                familyId = familyId,
                documentId = documentId,
                onBack = { navController.popBackStack() },
                onUpgrade = { navController.navigate(AppDestination.Plans.route) },
            )
        }

        composable(
            route = AppDestination.PasswordsHome.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            PasswordsHomeScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onOpenImportExport = {
                    navController.navigate(AppDestination.PasswordsImportExport.createRoute(familyId, ""))
                },
                onOpenSecurity = {
                    navController.navigate(AppDestination.PasswordsSecurity.createRoute(familyId))
                },
                onOpenSettings = {
                    navController.navigate(AppDestination.PasswordsSettings.createRoute(familyId))
                },
                onManageGroups = {
                    navController.navigate(AppDestination.PasswordsGroups.createRoute(familyId))
                },
                onAddPassword = {
                    navController.navigate(AppDestination.PasswordsAdd.createRoute(familyId))
                },
                onOpenPassword = { passwordId ->
                    navController.navigate(AppDestination.PasswordDetail.createRoute(familyId, passwordId))
                },
            )
        }

        composable(
            route = AppDestination.PasswordsSecurity.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            PasswordsSecurityScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onOpenPassword = { passwordId ->
                    navController.navigate(AppDestination.PasswordDetail.createRoute(familyId, passwordId))
                },
            )
        }

        composable(
            route = AppDestination.PasswordsSettings.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) {
            PasswordsSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAutoFillSettings = { navController.navigate(AppDestination.AutoFillSettings.route) },
            )
        }

        composable(
            route = AppDestination.PasswordsGroups.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            PasswordsGroupsScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onOpenCreateGroup = {
                    navController.navigate(AppDestination.PasswordGroupDetail.createRouteForNew(familyId))
                },
                onOpenGroup = { groupId ->
                    navController.navigate(AppDestination.PasswordGroupDetail.createRouteForEdit(familyId, groupId))
                },
            )
        }

        composable(
            route = AppDestination.PasswordGroupDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("groupId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val groupId = backStackEntry.arguments?.getString("groupId")
            PasswordGroupDetailScreen(
                familyId = familyId,
                groupId = groupId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.PasswordsAdd.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            AddPasswordScreen(
                familyId = familyId,
                editingEntryId = null,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.PasswordsEdit.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("passwordId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val passwordId = backStackEntry.arguments?.getString("passwordId").orEmpty()
            AddPasswordScreen(
                familyId = familyId,
                editingEntryId = passwordId.takeIf { it.isNotBlank() },
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.PasswordsImportExport.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("familyName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val familyName = backStackEntry.arguments?.getString("familyName")
            PasswordsImportExportScreen(
                familyId = familyId,
                familyName = familyName,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.PasswordDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("passwordId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val passwordId = backStackEntry.arguments?.getString("passwordId").orEmpty()
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            PasswordDetailScreen(
                familyId = familyId,
                passwordId = passwordId,
                onBack = { navController.popBackStack() },
                onChangePassword = {
                    navController.navigate(AppDestination.PasswordsEdit.createRoute(familyId, passwordId))
                },
                onOpenSecurityReport = { fid ->
                    navController.navigate(AppDestination.PasswordsSecurity.createRoute(fid))
                },
            )
        }

        composable(
            route = AppDestination.TravelList.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            TravelListScreen(
                familyId = familyId,
                onNavigateBack = { navController.popBackStack() },
                onOpenWizard = { navController.navigate(AppDestination.TravelWizard.createRoute(familyId)) },
                onOpenDiscover = { navController.navigate(AppDestination.TravelDiscover.createRoute(familyId)) },
                onOpenTrip = { tripId ->
                    navController.navigate(AppDestination.TravelDetail.createRoute(familyId, tripId))
                },
                onOpenAllTrips = {
                    navController.navigate(AppDestination.TravelAllTrips.createRoute(familyId))
                },
            )
        }

        composable(
            route = AppDestination.TravelAllTrips.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            TravelAllTripsScreen(
                familyId = familyId,
                onNavigateBack = { navController.popBackStack() },
                onOpenTrip = { tripId ->
                    navController.navigate(AppDestination.TravelDetail.createRoute(familyId, tripId))
                },
            )
        }

        composable(
            route = AppDestination.TravelDiscover.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            TravelDiscoverScreen(
                familyId = familyId,
                onNavigateBack = { navController.popBackStack() },
                onOpenDestination = { destinationId ->
                    navController.navigate(AppDestination.TravelDestinationDetail.createRoute(familyId, destinationId))
                },
            )
        }

        composable(
            route = AppDestination.TravelDestinationDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("destinationId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val destinationId = backStackEntry.arguments?.getString("destinationId").orEmpty()
            val discoverEntry = remember(backStackEntry) {
                navController.getBackStackEntry(AppDestination.TravelDiscover.createRoute(familyId))
            }
            val discoverViewModel: TravelDiscoverViewModel = hiltViewModel(discoverEntry)
            TravelDestinationDetailScreen(
                familyId = familyId,
                destinationId = destinationId,
                onNavigateBack = { navController.popBackStack() },
                onPlanTrip = { encodedDestination ->
                    val decoded = URLDecoder.decode(encodedDestination, StandardCharsets.UTF_8.name())
                    navController.navigate(AppDestination.TravelWizard.createRoute(familyId, decoded))
                },
                onTripAccepted = { tripId ->
                    navController.navigate(AppDestination.TravelDetail.createRoute(familyId, tripId)) {
                        popUpTo(AppDestination.TravelDiscover.createRoute(familyId)) { inclusive = true }
                    }
                },
                viewModel = discoverViewModel,
            )
        }

        composable(
            route = AppDestination.TravelWizard.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("destination") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val destinationRaw = backStackEntry.arguments?.getString("destination").orEmpty()
            val prefill = destinationRaw.takeIf { it.isNotBlank() }?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.name())
            }
            TravelWizardScreen(
                familyId = familyId,
                prefillDestinationName = prefill,
                navController = navController,
                onNavigateBack = { navController.popBackStack() },
                onOpenPlans = { navController.navigate(AppDestination.Plans.route) },
            )
        }

        composable(
            route = AppDestination.TravelProposal.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            TravelProposalRoute(
                familyId = familyId,
                navController = navController,
                backStackEntry = backStackEntry,
                onOpenPlans = { navController.navigate(AppDestination.Plans.route) },
            )
        }

        composable(
            route = AppDestination.TravelDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("tripId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
            TravelDetailScreen(
                tripId = tripId,
                familyId = familyId,
                navController = navController,
            )
        }

        composable(
            route = AppDestination.TravelCategoryResults.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("tripId") { type = NavType.StringType },
                navArgument("kind") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
            val kind = backStackEntry.arguments?.getString("kind").orEmpty()
            TravelCategoryResultsScreen(
                familyId = familyId,
                tripId = tripId,
                kind = kind,
                navController = navController,
                backStackEntry = backStackEntry,
            )
        }

        composable(
            route = AppDestination.TravelPlaceDetail.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("placeName") { type = NavType.StringType },
                navArgument("locationContext") { type = NavType.StringType },
                navArgument("scheduleBadge") { type = NavType.StringType },
                navArgument("time") { type = NavType.StringType; defaultValue = "" },
                navArgument("staySummary") { type = NavType.StringType; defaultValue = "" },
                navArgument("costSummary") { type = NavType.StringType; defaultValue = "" },
                navArgument("nextStopTitle") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val context = TravelItineraryStopContext(
                id = decodeNavArg(backStackEntry.arguments?.getString("placeName")),
                placeName = decodeNavArg(backStackEntry.arguments?.getString("placeName")),
                locationContext = decodeNavArg(backStackEntry.arguments?.getString("locationContext")),
                scheduleBadge = decodeNavArg(backStackEntry.arguments?.getString("scheduleBadge")),
                time = decodeNavArg(backStackEntry.arguments?.getString("time")),
                staySummary = decodeNavArg(backStackEntry.arguments?.getString("staySummary")),
                costSummary = decodeNavArg(backStackEntry.arguments?.getString("costSummary")),
                nextStopTitle = decodeNavArg(backStackEntry.arguments?.getString("nextStopTitle")).takeIf { it.isNotBlank() },
            )
            TravelPlaceDetailScreen(
                context = context,
                familyId = familyId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.AskExpert.route) { PlaceholderScreen("Assistente AI") }

        composable(
            route = AppDestination.AiChat.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            PlanningAIChatScreen(
                onNavigateToCalendar = { navController.navigate(AppDestination.Calendar.createRoute(familyId)) },
                onNavigateToTodo = { navController.navigate(AppDestination.Todo.route) },
                onNavigateToHealth = { navController.navigate(AppDestination.PediatricChildSelector.createRoute(familyId)) },
                onNavigateToUpgrade = { navController.navigate(AppDestination.AiSettings.route) },
            )
        }

        composable(
            route = AppDestination.PlanningAiChat.route,
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("familyName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            val familyName = backStackEntry.arguments?.getString("familyName").orEmpty()
            AIChatScreen(
                familyId = familyId,
                familyName = familyName,
                onBack = { navController.popBackStack() },
                onOpenAiSettings = { navController.navigate(AppDestination.AiSettings.route) },
            )
        }

        composable(AppDestination.FamilySettings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            FamilySettingsScreen(
                onBack = { navController.popBackStack() },
                onInvite = { navController.navigate(AppDestination.InviteCode.route) },
                onJoin = { navController.navigate(AppDestination.JoinFamily.route) },
                onEditFamily = { navController.navigate(AppDestination.EditFamily.route) },
                onLeaveDone = {
                    KBLog.navigation.debug("onLeaveDone -> restart app", "AppNavGraph")
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