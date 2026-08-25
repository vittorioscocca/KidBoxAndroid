package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.AppLanguage
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.ui.components.KBBackButton

private data class LanguageRow(
    val title: String,
    val language: AppLanguage,
)

@Composable
fun LanguageScreen(
    onBack: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val current by viewModel.language.collectAsStateWithLifecycle()

    // Gli autonimi (Italiano/English/…) restano non tradotti, come da prassi per i
    // selettori di lingua; solo la voce "sistema" segue la lingua corrente.
    val rows = listOf(
        LanguageRow(stringResource(R.string.settings_language_system), AppLanguage.SYSTEM),
        LanguageRow("Italiano", AppLanguage.IT),
        LanguageRow("English", AppLanguage.EN),
        LanguageRow("Français", AppLanguage.FR),
        LanguageRow("Español", AppLanguage.ES),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        KBBackButton(onClick = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_language_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.kidBoxColors.title,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        ) {
            rows.forEachIndexed { index, row ->
                LanguageRowItem(
                    title = row.title,
                    isSystem = row.language == AppLanguage.SYSTEM,
                    isSelected = current == row.language,
                    onClick = { viewModel.setLanguage(row.language) },
                )
                if (index != rows.lastIndex) {
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.kidBoxColors.divider,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_language_footer),
            fontSize = 12.sp,
            color = MaterialTheme.kidBoxColors.subtitle,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun LanguageRowItem(
    title: String,
    isSystem: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon: ImageVector =
                if (isSystem) Icons.Filled.PhoneAndroid else Icons.Filled.Language
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.kidBoxColors.subtitle,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(title, fontSize = 16.sp, color = MaterialTheme.kidBoxColors.title)
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFFFF6B00),
            )
        }
    }
}
