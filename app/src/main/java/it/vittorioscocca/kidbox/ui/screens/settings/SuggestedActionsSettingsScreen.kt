package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.screens.chat.SuggestedActionKind
import it.vittorioscocca.kidbox.ui.screens.chat.SuggestedActionsPrefs
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/** Impostazioni > Messaggi > Azioni suggerite: un interruttore per tipo di banner. */
@Composable
fun SuggestedActionsSettingsScreen(onBack: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val enabled by remember { SuggestedActionsPrefs.enabled(context) }.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        KBBackButton(onClick = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_suggested_actions_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
        )
        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = kb.card),
        ) {
            SuggestedActionKind.entries.forEachIndexed { index, kind ->
                if (index > 0) HorizontalDivider(color = kb.subtitle.copy(alpha = 0.12f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(kind.icon, contentDescription = null, tint = kind.tint)
                    Text(
                        text = stringResource(kind.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = kb.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    Switch(
                        checked = kind in enabled.orEmpty(),
                        onCheckedChange = { SuggestedActionsPrefs.setEnabled(context, kind, it) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_suggested_actions_expiry),
            style = MaterialTheme.typography.bodySmall,
            color = kb.subtitle,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_suggested_actions_note),
            style = MaterialTheme.typography.bodySmall,
            color = kb.subtitle,
        )
    }
}
