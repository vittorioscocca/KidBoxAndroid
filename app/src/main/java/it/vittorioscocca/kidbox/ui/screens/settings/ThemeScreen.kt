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
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
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

private data class ThemeRow(
    val title: String,
    val icon: ImageVector,
    val theme: AppTheme,
)

@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val current by viewModel.theme.collectAsStateWithLifecycle()

    val rows = listOf(
        ThemeRow("Chiaro", Icons.Filled.WbSunny, AppTheme.LIGHT),
        ThemeRow("Scuro", Icons.Filled.DarkMode, AppTheme.DARK),
        ThemeRow("Sistema", Icons.Filled.BrightnessAuto, AppTheme.SYSTEM),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
            .statusBarsPadding()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(
            text = "Tema",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1A1A1A),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { viewModel.setTheme(row.theme) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = row.icon,
                            contentDescription = null,
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(row.title, fontSize = 16.sp, color = Color(0xFF1A1A1A))
                    }
                    if (row.theme == current) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFFFF6B00))
                    } else {
                        Box(modifier = Modifier.size(24.dp))
                    }
                }
                if (index != rows.lastIndex) {
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = Color(0xFFE8E8E8),
                    )
                }
            }
        }
    }
}
