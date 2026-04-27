package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.AppTheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private data class SettingRowItem(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val showChevron: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTheme: () -> Unit,
    onFamilySettings: () -> Unit,
    onMessageSettings: () -> Unit,
    onNotifications: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val theme by viewModel.theme.collectAsStateWithLifecycle()

    val rows = listOf(
        SettingRowItem(
            title = "Tema",
            subtitle = theme.toSubtitle(),
            icon = Icons.Filled.Contrast,
            showChevron = true,
            onClick = onTheme,
        ),
        SettingRowItem(
            title = "Family settings",
            icon = Icons.Filled.Groups,
            showChevron = false,
            onClick = onFamilySettings,
        ),
        SettingRowItem(
            title = "Messaggi",
            icon = Icons.AutoMirrored.Filled.Chat,
            showChevron = true,
            onClick = onMessageSettings,
        ),
        SettingRowItem(
            title = "Assistente AI",
            icon = Icons.AutoMirrored.Filled.Help,
            showChevron = true,
            onClick = {},
        ),
        SettingRowItem(
            title = "Notifiche",
            icon = Icons.Filled.Notifications,
            showChevron = true,
            onClick = onNotifications,
        ),
        SettingRowItem(
            title = "Utilizzo spazio",
            icon = Icons.Filled.Storage,
            showChevron = true,
            onClick = {},
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(
            text = "Impostazioni",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.kidBoxColors.title,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        ) {
            rows.forEachIndexed { index, item ->
                SettingRow(item)
                if (index != rows.lastIndex) {
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.kidBoxColors.divider,
                    )
                }
            }
        }
    }
}

private fun AppTheme.toSubtitle(): String = when (this) {
    AppTheme.LIGHT -> "Chiaro"
    AppTheme.DARK -> "Scuro"
    AppTheme.SYSTEM -> "Sistema"
}

@Composable
private fun SettingRow(item: SettingRowItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color(0xFFFF6B00),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(item.title, fontSize = 16.sp, color = MaterialTheme.kidBoxColors.title)
                if (item.subtitle != null) {
                    Text(item.subtitle, fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
                }
            }
        }
        if (item.showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.kidBoxColors.subtitle,
            )
        } else {
            Box(modifier = Modifier.size(24.dp))
        }
    }
}
