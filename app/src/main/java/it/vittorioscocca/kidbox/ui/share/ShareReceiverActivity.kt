package it.vittorioscocca.kidbox.ui.share

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.ui.theme.KidBoxTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var aiService: AIService

    @Inject
    lateinit var shareActionHandler: ShareActionHandler

    @Inject
    lateinit var familyDao: KBFamilyDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = ShareContentClassifier.fromIntent(intent ?: Intent())
        val prefs = getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
        val prefActiveFamilyId = prefs.getString("active_family_id", "").orEmpty()
        val prefLegacyFamilyId = prefs.getString("family_id", "").orEmpty()
        val initialFamilyId = prefActiveFamilyId.ifBlank { prefLegacyFamilyId }
        val authUser = FirebaseAuth.getInstance().currentUser
        val defaultSuggestions = ShareDestinationSuggester.suggest(payload.contentType)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            KidBoxTheme(darkTheme = darkTheme) {
                val snackState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var selected by remember { mutableStateOf(defaultSuggestions.firstOrNull() ?: ShareDestination.CHAT) }
                var aiSuggested by remember { mutableStateOf<ShareDestination?>(null) }
                var title by remember { mutableStateOf(defaultTitle(payload.contentType)) }
                var isSubmitting by remember { mutableStateOf(false) }
                var familyId by remember { mutableStateOf(initialFamilyId) }
                val textContent = when (val c = payload.contentType) {
                    is ShareContentType.TextContent -> c.text
                    is ShareContentType.UrlContent -> c.url
                    else -> ""
                }

                if (authUser == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Accedi a KidBox per condividere contenuti")
                            Button(onClick = {
                                startActivity(Intent(this@ShareReceiverActivity, MainActivity::class.java))
                                finish()
                            }) {
                                Text("Apri KidBox")
                            }
                        }
                    }
                    return@KidBoxTheme
                }

                LaunchedEffect(authUser?.uid) {
                    if (familyId.isNotBlank()) return@LaunchedEffect
                    val fallbackFamilyId = runCatching { familyDao.peekAnyFamilyId().orEmpty() }
                        .getOrDefault("")
                    if (fallbackFamilyId.isNotBlank()) {
                        familyId = fallbackFamilyId
                        prefs.edit()
                            .putString("active_family_id", fallbackFamilyId)
                            .putString("family_id", fallbackFamilyId)
                            .apply()
                    }
                }

                LaunchedEffect(textContent, familyId) {
                    if (textContent.isBlank() || familyId.isBlank()) return@LaunchedEffect
                    val suggested = ShareAIClassifier.classifyText(textContent, familyId, aiService)
                    if (suggested != null) {
                        aiSuggested = suggested
                        if (suggested in defaultSuggestions) selected = suggested
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ShareBottomSheet(
                        contentType = payload.contentType,
                        title = title,
                        onTitleChange = { title = it },
                        suggestions = defaultSuggestions,
                        selectedDestination = selected,
                        aiSuggestedDestination = aiSuggested,
                        isSubmitting = isSubmitting,
                        onSelectDestination = { selected = it },
                        onCancel = { finish() },
                        onConfirm = {
                            if (familyId.isBlank()) {
                                scope.launch { snackState.showSnackbar("Famiglia non trovata. Apri KidBox e riprova.") }
                                return@ShareBottomSheet
                            }
                            isSubmitting = true
                            scope.launch {
                                runCatching {
                                    shareActionHandler.execute(
                                        ShareActionInput(
                                            familyId = familyId,
                                            contentType = payload.contentType,
                                            allUris = payload.allUris,
                                            selectedDestination = selected,
                                            title = title.trim(),
                                            text = textContent.trim(),
                                        ),
                                        contentResolver,
                                    )
                                }.onSuccess {
                                    snackState.showSnackbar("Contenuto condiviso")
                                    finish()
                                }.onFailure { err ->
                                    snackState.showSnackbar(err.message ?: "Errore durante la condivisione")
                                    isSubmitting = false
                                }
                            }
                        },
                    )
                    SnackbarHost(
                        hostState = snackState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                    )
                }
            }
        }
    }

    private fun defaultTitle(contentType: ShareContentType): String = when (contentType) {
        is ShareContentType.TextContent -> contentType.text.lineSequence().firstOrNull().orEmpty().take(80)
        is ShareContentType.UrlContent -> contentType.url
        is ShareContentType.ImageContent -> contentType.uri.lastPathSegment.orEmpty()
        is ShareContentType.VideoContent -> contentType.uri.lastPathSegment.orEmpty()
        is ShareContentType.PdfContent -> contentType.uri.lastPathSegment.orEmpty()
        is ShareContentType.FileContent -> contentType.uri.lastPathSegment.orEmpty()
        ShareContentType.Unknown -> ""
    }
}

