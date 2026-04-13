package it.vittorioscocca.kidbox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.rememberNavController
import com.facebook.CallbackManager
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.data.local.AppTheme
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.data.local.ThemePreference
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.navigation.AppNavGraph
import it.vittorioscocca.kidbox.ui.splash.KidBoxSplashScreen
import it.vittorioscocca.kidbox.ui.theme.KidBoxTheme
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
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPushDeepLink = parsePushDeepLink(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun parsePushDeepLink(intent: Intent?): PushDeepLink? {
        val src = intent?.extras ?: return null
        val type = src.getString("push_type") ?: src.getString("type") ?: return null
        val familyId = src.getString("push_family_id") ?: src.getString("familyId") ?: ""
        val itemId = src.getString("push_item_id") ?: src.getString("itemId")
        val childId = src.getString("push_child_id") ?: src.getString("childId")
        val listId = src.getString("push_list_id") ?: src.getString("listId")
        val todoId = src.getString("push_todo_id") ?: src.getString("todoId")
        return PushDeepLink(
            type = type,
            familyId = familyId,
            itemId = itemId,
            childId = childId,
            listId = listId,
            todoId = todoId,
        )
    }
}

private data class PushDeepLink(
    val type: String,
    val familyId: String,
    val itemId: String?,
    val childId: String?,
    val listId: String?,
    val todoId: String?,
)
