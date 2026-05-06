package it.vittorioscocca.kidbox.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class KBPlan(
    val raw: String,
    val displayName: String,
    val includesAI: Boolean,
    val aiDailyLimit: Int,
    val planColorValue: Long,
    val planIconName: String,
) {
    FREE("free", "Free", false, 0, 0xFF9E9E9E, "star"),
    PRO("pro", "Pro", true, 20, 0xFF2563EB, "auto_awesome"),
    MAX("max", "Max", true, Int.MAX_VALUE, 0xFF7C3AED, "workspace_premium");

    val planColor: Color get() = Color(planColorValue)

    val planIcon: ImageVector
        get() = when (planIconName) {
            "auto_awesome" -> Icons.Filled.AutoAwesome
            "workspace_premium" -> Icons.Filled.WorkspacePremium
            else -> Icons.Filled.Star
        }

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: FREE
    }
}
