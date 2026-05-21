package it.vittorioscocca.kidbox.notifications

import android.content.Context
import android.content.Intent
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.screens.ai.planning.DailyBriefingDraftStore
import it.vittorioscocca.kidbox.ui.screens.ai.planning.HealthPatternDraftStore
import it.vittorioscocca.kidbox.ui.screens.ai.planning.WeeklySummaryDraftStore
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tap su notifiche locali (briefing, recap settimanale, insight salute)
 * → salva testo in draft store e apre la chat Assistente AI.
 */
object NotificationDeepLinkRouter {

    private const val PREFS_NAME = "kidbox_prefs"
    private const val KEY_ACTIVE_FAMILY_ID = "active_family_id"
    private const val KEY_LAST_BRIEFING = "kb_dailyBriefing_lastText"

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    /** Incrementato a ogni tap notifica AI (anche se la chat è già aperta). */
    private val _recapTick = MutableStateFlow(0)
    val recapTick: StateFlow<Int> = _recapTick.asStateFlow()

    fun handleLaunchIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("push_type")
            ?: intent.getStringExtra("type")
        val isAiChatAction = intent.action == "KB_OPEN_AI_CHAT"
        if (type.isNullOrBlank() && !isAiChatAction) return

        val resolvedType = type ?: return
        val familyId = resolveFamilyId(context, intent)
        val fullText = resolveFullText(context, intent, resolvedType)

        when (resolvedType) {
            "daily_briefing" -> {
                if (fullText.isNotBlank()) DailyBriefingDraftStore.save(context, fullText)
                queueAiChatRoute(familyId, resolvedType)
            }
            "weekly_summary" -> {
                if (fullText.isNotBlank()) WeeklySummaryDraftStore.save(context, fullText)
                queueAiChatRoute(familyId, resolvedType)
            }
            "health_pattern" -> {
                if (fullText.isNotBlank()) HealthPatternDraftStore.save(context, fullText)
                queueAiChatRoute(familyId, resolvedType)
            }
            "geofenceEvent" -> {
                if (familyId.isNotBlank()) {
                    _pendingRoute.value = AppDestination.FamilyLocation.createRoute(familyId)
                    KBLog.app.info(
                        "NotificationDeepLink: geofenceEvent → family_location/$familyId",
                        "NotificationDeepLink",
                    )
                }
            }
            else -> Unit
        }
        _recapTick.value += 1
    }

    private fun resolveFamilyId(context: Context, intent: Intent): String {
        return intent.getStringExtra("push_family_id")
            ?: intent.getStringExtra("familyId")
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_FAMILY_ID, null)
            ?: ""
    }

    private fun resolveFullText(context: Context, intent: Intent, type: String): String {
        val fromIntent = intent.getStringExtra("fullText").orEmpty()
        if (fromIntent.isNotBlank()) return fromIntent
        if (type != "daily_briefing") return ""
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_BRIEFING, null)
            .orEmpty()
    }

    private fun queueAiChatRoute(familyId: String, type: String) {
        if (familyId.isBlank()) {
            KBLog.app.warning(
                "NotificationDeepLink: familyId mancante per type=$type",
                "NotificationDeepLink",
            )
            return
        }
        _pendingRoute.value = AppDestination.AiChat.createRoute(familyId)
        KBLog.app.info("NotificationDeepLink: coda navigazione $type → $familyId", "NotificationDeepLink")
    }

    fun clear() {
        _pendingRoute.value = null
    }
}
