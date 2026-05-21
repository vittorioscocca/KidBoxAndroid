package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun PrivacySettingsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val kb = MaterialTheme.kidBoxColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = kb.title,
                )
            }
            Text(
                text = "Privacy",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = kb.title,
            )
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Log tecnici e report errori",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = kb.subtitle,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = kb.card),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "KidBox salva log tecnici sul dispositivo (avvio app, sincronizzazione, crash). " +
                            "Se attivi l’invio automatico, analizziamo questi log e possiamo inviare un report " +
                            "anonimo per correggere bug e migliorare l’app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = kb.subtitle,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CrashReportSettingsRow()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Su Android l’analisi usa Gemini sul dispositivo quando disponibile, altrimenti un servizio cloud dedicato. " +
                    "Non inviamo nomi, chat, documenti o dati sanitari. Puoi disattivare l’invio in qualsiasi momento.",
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
