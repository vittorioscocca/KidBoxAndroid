package it.vittorioscocca.kidbox.ui.screens.settings

import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.pm.PackageInfoCompat
import it.vittorioscocca.kidbox.data.local.AppTheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.screens.settings.family.SettingsFamilyCard

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
    onLanguage: () -> Unit,
    onUsageGuide: () -> Unit,
    onFamilySettings: () -> Unit,
    onFamilyLeft: () -> Unit,
    onMessageSettings: () -> Unit,
    onNotifications: () -> Unit,
    onAiSettings: () -> Unit,
    onStorageUsage: () -> Unit,
    onAutoFillSettings: () -> Unit,
    onPrivacySettings: () -> Unit,
    onSupportChat: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val (appVersionName, appVersionCode) = remember(context.packageName) {
        runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
            val name = info.versionName ?: "—"
            val code = PackageInfoCompat.getLongVersionCode(info).toString()
            name to code
        }.getOrDefault("—" to "—")
    }

    val rows = listOf(
        SettingRowItem(
            title = stringResource(R.string.settings_row_theme),
            subtitle = theme.toSubtitle(),
            icon = Icons.Filled.Contrast,
            showChevron = true,
            onClick = onTheme,
        ),
        SettingRowItem(
            title = stringResource(R.string.settings_row_language),
            icon = Icons.Filled.Language,
            showChevron = true,
            onClick = onLanguage,
        ),
        SettingRowItem(
            title = stringResource(R.string.settings_row_messages),
            icon = Icons.AutoMirrored.Filled.Chat,
            showChevron = true,
            onClick = onMessageSettings,
        ),
        SettingRowItem(
            title = stringResource(R.string.settings_row_ai),
            icon = Icons.Filled.AutoAwesome,
            showChevron = true,
            onClick = onAiSettings,
        ),
        SettingRowItem(
            title = stringResource(R.string.settings_row_notifications),
            icon = Icons.Filled.Notifications,
            showChevron = true,
            onClick = onNotifications,
        ),
        SettingRowItem(
            title = stringResource(R.string.settings_row_privacy),
            subtitle = stringResource(R.string.settings_row_privacy_sub),
            icon = Icons.Filled.Shield,
            showChevron = true,
            onClick = onPrivacySettings,
        ),
        // Subito dopo Privacy, come su iOS: le due voci trattano entrambe la
        // protezione dei dati e vanno lette una di seguito all'altra.
        SettingRowItem(
            title = stringResource(R.string.settings_row_autofill),
            subtitle = stringResource(R.string.settings_row_autofill_sub),
            icon = Icons.Filled.Key,
            showChevron = true,
            onClick = onAutoFillSettings,
        ),
        SettingRowItem(
            title = stringResource(R.string.settings_row_storage),
            icon = Icons.Filled.Storage,
            showChevron = true,
            onClick = onStorageUsage,
        ),
        SettingRowItem(
            title = stringResource(R.string.settings_row_support),
            subtitle = stringResource(R.string.settings_row_support_sub),
            icon = Icons.Filled.SupportAgent,
            showChevron = true,
            onClick = onSupportChat,
        ),
        SettingRowItem(
            title = stringResource(R.string.settings_row_guide),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            showChevron = true,
            onClick = onUsageGuide,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        KBBackButton(onClick = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.kidBoxColors.title,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // La famiglia sta in una card sua, sopra l'elenco: è la prima cosa che
        // si viene a cercare qui, e una riga fra le altre non lo diceva.
        SettingsFamilyCard(
            onOpenFamilySettings = onFamilySettings,
            onLeaveDone = onFamilyLeft,
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

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Versione $appVersionName",
                fontSize = 13.sp,
                color = MaterialTheme.kidBoxColors.subtitle,
                fontWeight = FontWeight.Normal,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Build $appVersionCode",
                fontSize = 13.sp,
                color = MaterialTheme.kidBoxColors.subtitle,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun AppTheme.toSubtitle(): String = when (this) {
    AppTheme.LIGHT -> stringResource(R.string.settings_theme_light)
    AppTheme.DARK -> stringResource(R.string.settings_theme_dark)
    AppTheme.SYSTEM -> stringResource(R.string.settings_theme_system)
}

@Composable
private fun SettingRow(item: SettingRowItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (item.subtitle != null) 64.dp else 56.dp)
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = if (item.subtitle != null) 10.dp else 0.dp),
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
                    Text(
                        item.subtitle,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.kidBoxColors.subtitle,
                        maxLines = 2,
                    )
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
