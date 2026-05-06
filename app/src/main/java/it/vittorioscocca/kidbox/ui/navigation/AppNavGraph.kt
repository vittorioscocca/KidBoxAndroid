package it.vittorioscocca.kidbox.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
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
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AIChatScreen
import it.vittorioscocca.kidbox.ui.screens.settings.AiSettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.SettingsScreen
import it.vittorioscocca.kidbox.ui.screens.settings.ThemeScreen
import it.vittorioscocca.kidbox.ui.screens.calendar.CalendarScreen
import it.vittorioscocca.kidbox.ui.screens.expenses.ExpensesHomeScreen
import it.vittorioscocca.kidbox.ui.screens.location.FamilyLocationScreen
import it.vittorioscocca.kidbox.ui.screens.wallet.WalletHomeScreen
import it.vittorioscocca.kidbox.ui.screens.wallet.WalletTicketDetailScreen
import it.vittorioscocca.kidbox.ui.screens.notes.NoteDetailScreen
import it.vittorioscocca.kidbox.ui.screens.notes.NotesHomeScreen
import it.vittorioscocca.kidbox.ui.screens.photos.FamilyPhotosScreen
import it.vittorioscocca.kidbox.ui.screens.chat.ChatMediaGalleryScreen
import it.vittorioscocca.kidbox.ui.screens.chat.ChatScreen
import it.vittorioscocca.kidbox.ui.screens.chat.ChatViewModel
import it.vittorioscocca.kidbox.ui.screens.todo.TodoHomeScreen
import it.vittorioscocca.kidbox.ui.screens.todo.TodoListScreen
import it.vittorioscocca.kidbox.ui.screens.health.HealthSubjectSelectorScreen
import it.vittorioscocca.kidbox.ui.screens.health.HealthHomeScreen
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
                onAiSettings = { navController.navigate(AppDestination.AiSettings.route) },
            )
        }

        composable(AppDestination.AiSettings.route) {
            AiSettingsScreen(onBack = { navController.popBackStack() })
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
                onSaved = { _: String -> navController.popBackStack() },
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
            )
        }

        composable(AppDestination.Chat.route) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onNavigateToGallery = { familyId ->
                    navController.navigate(AppDestination.ChatMediaGallery.createRoute(familyId))
                },
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

        composable(AppDestination.AskExpert.route) { PlaceholderScreen("Assistente AI") }

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