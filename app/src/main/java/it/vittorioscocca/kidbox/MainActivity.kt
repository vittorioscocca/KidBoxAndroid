package it.vittorioscocca.kidbox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.rememberNavController
import com.facebook.CallbackManager
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.data.local.AppTheme
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.data.local.ThemePreference
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.navigation.AppNavGraph
import it.vittorioscocca.kidbox.notifications.NotificationBadgeStore
import it.vittorioscocca.kidbox.ui.splash.KidBoxSplashScreen
import it.vittorioscocca.kidbox.ui.theme.KidBoxTheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var facebookCallbackManager: CallbackManager

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var themePreference: ThemePreference

    private var pendingPushDeepLink by mutableStateOf<PushDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        clearAppBadgeAndNotifications()
        enableEdgeToEdge()
        pendingPushDeepLink = parsePushDeepLink(intent)
        setContent {
            val theme by themePreference.getThemeFlow().collectAsState(AppTheme.SYSTEM)
            val darkTheme = when (theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view)
                        .isAppearanceLightNavigationBars = !darkTheme
                }
            }
            KidBoxTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.kidBoxColors.background),
                ) {
                    val navController = rememberNavController()
                    var showSplash by remember { mutableStateOf(true) }

                    Crossfade(
                        targetState = showSplash,
                        animationSpec = tween(500),
                        label = "splash_crossfade",
                    ) { splash ->
                        if (splash) {
                            KidBoxSplashScreen(onFinished = { showSplash = false })
                        } else {
                            AppNavGraph(
                                navController = navController,
                                startDestination = AppDestination.Login.route,
                                onboardingPreferences = onboardingPreferences,
                            )
                        }
                    }

                    LaunchedEffect(showSplash, pendingPushDeepLink) {
                        if (showSplash) return@LaunchedEffect
                        val deepLink = pendingPushDeepLink ?: return@LaunchedEffect
                        when (deepLink.type) {
                            "calendar", "open_calendar", "calendar_event" -> {
                                if (deepLink.familyId.isNotBlank()) {
                                    navController.navigate(AppDestination.Calendar.createRoute(deepLink.familyId))
                                }
                                pendingPushDeepLink = null
                            }

                            "new_grocery_item" -> {
                                if (deepLink.familyId.isNotBlank()) {
                                    navController.navigate(AppDestination.ShoppingList.createRoute(deepLink.familyId))
                                }
                                pendingPushDeepLink = null
                            }

                            "todo_assigned", "todo_due_changed" -> {
                                if (
                                    deepLink.familyId.isNotBlank() &&
                                    !deepLink.childId.isNullOrBlank() &&
                                    !deepLink.listId.isNullOrBlank()
                                ) {
                                    navController.navigate(
                                        AppDestination.TodoList.createRoute(
                                            familyId = deepLink.familyId,
                                            childId = deepLink.childId!!,
                                            listId = deepLink.listId!!,
                                            highlightTodoId = deepLink.todoId,
                                        ),
                                    )
                                }
                                pendingPushDeepLink = null
                            }

                            "expense", "expenses", "new_expense", "expense_created" -> {
                                if (deepLink.familyId.isNotBlank()) {
                                    navController.navigate(
                                        AppDestination.ExpensesHome.createRoute(
                                            familyId = deepLink.familyId,
                                            highlightExpenseId = deepLink.expenseId ?: deepLink.itemId,
                                        ),
                                    )
                                }
                                pendingPushDeepLink = null
                            }

                            "document", "documents", "new_document", "document_shared", "shared_document" -> {
                                if (deepLink.familyId.isNotBlank()) {
                                    navController.navigate(
                                        AppDestination.DocumentsHome.createRoute(
                                            familyId = deepLink.familyId,
                                            highlightDocumentId = deepLink.docId ?: deepLink.itemId,
                                        ),
                                    )
                                }
                                pendingPushDeepLink = null
                            }

                            "note", "notes", "new_note" -> {
                                if (deepLink.familyId.isNotBlank()) {
                                    val targetNoteId = deepLink.noteId ?: deepLink.itemId
                                    if (!targetNoteId.isNullOrBlank()) {
                                        navController.navigate(
                                            AppDestination.NoteDetail.createRoute(
                                                familyId = deepLink.familyId,
                                                noteId = targetNoteId,
                                            ),
                                        )
                                    } else {
                                        navController.navigate(AppDestination.NotesHome.createRoute(deepLink.familyId))
                                    }
                                }
                                pendingPushDeepLink = null
                            }

                            "location", "open_location", "location_sharing_started", "location_sharing_stopped" -> {
                                if (deepLink.familyId.isNotBlank()) {
                                    navController.navigate(AppDestination.FamilyLocation.createRoute(deepLink.familyId))
                                }
                                pendingPushDeepLink = null
                            }

                            "location", "family_location", "location_sharing_started", "location_sharing_stopped" -> {
                                if (deepLink.familyId.isNotBlank()) {
                                    navController.navigate(
                                        AppDestination.FamilyLocation.createRoute(deepLink.familyId),
                                    )
                                }
                                pendingPushDeepLink = null
                            }

                            "chat", "new_message", "new_chat_message", "message_received", "open_chat" -> {
                                navController.navigate(AppDestination.Chat.route)
                                pendingPushDeepLink = null
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPushDeepLink = parsePushDeepLink(intent)
        clearAppBadgeAndNotifications()
    }

    override fun onResume() {
        super.onResume()
        clearAppBadgeAndNotifications()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun parsePushDeepLink(intent: Intent?): PushDeepLink? {
        val src = intent?.extras
        val deepLink = src.safeGetString("deep_link")
            ?: src.safeGetString("push_deep_link")
            ?: src.safeGetString("route")
        val type = src.safeGetString("push_type")
            ?: src.safeGetString("type")
            ?: deepLink
            ?: return null
        val familyId = src.safeGetString("push_family_id") ?: src.safeGetString("familyId") ?: ""
        val itemId = src.safeGetString("push_item_id") ?: src.safeGetString("itemId")
        val docId = src.safeGetString("push_doc_id") ?: src.safeGetString("docId")
        val noteId = src.safeGetString("push_note_id") ?: src.safeGetString("noteId")
        val expenseId = src.safeGetString("push_expense_id") ?: src.safeGetString("expenseId")
        val childId = src.safeGetString("push_child_id") ?: src.safeGetString("childId")
        val listId = src.safeGetString("push_list_id") ?: src.safeGetString("listId")
        val todoId = src.safeGetString("push_todo_id") ?: src.safeGetString("todoId")
        return PushDeepLink(
            type = type,
            familyId = familyId,
            itemId = itemId ?: expenseId ?: docId ?: noteId,
            childId = childId,
            listId = listId,
            todoId = todoId,
            expenseId = expenseId,
            docId = docId,
            noteId = noteId,
        )
    }

    private fun clearAppBadgeAndNotifications() {
        NotificationBadgeStore.reset(this)
        NotificationManagerCompat.from(this).cancelAll()
    }
}

private data class PushDeepLink(
    val type: String,
    val familyId: String,
    val itemId: String?,
    val childId: String?,
    val listId: String?,
    val todoId: String?,
    val expenseId: String?,
    val docId: String?,
    val noteId: String?,
)

private fun Bundle?.safeGetString(key: String): String? = this?.getString(key)
