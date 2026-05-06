@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun AiConsentDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    /** Se false, non chiama [AiSettings.recordConsent] qui: usalo quando [onAccept] gestisce il salvataggio (es. ViewModel). */
    recordConsentOnAccept: Boolean = true,
) {
    val context = LocalContext.current
    val aiSettings = remember(context) { context.getAiSettingsFromApp() }
    var acceptedTerms by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Assistente AI Medico",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Prima di continuare, leggi come funziona.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            ConsentInfoBlock(
                icon = Icons.Default.UploadFile,
                color = Color(0xFFF59E0B),
                title = "Cosa viene inviato",
                body = "Le tue domande e il contesto sanitario vengono inviati ad Anthropic per generare la risposta.",
            )
            Spacer(Modifier.height(10.dp))
            ConsentInfoBlock(
                icon = Icons.Default.Business,
                color = Color(0xFF3B82F6),
                title = "Fornitore AI: Anthropic",
                body = "I dati sono trattati secondo la Privacy Policy di Anthropic.",
            )
            Spacer(Modifier.height(10.dp))
            ConsentInfoBlock(
                icon = Icons.Default.Warning,
                color = Color(0xFFEF4444),
                title = "Non è un parere medico",
                body = "L'AI fornisce informazioni generali e non sostituisce il medico.",
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = { acceptedTerms = it },
                )
                Text(
                    text = "Ho letto e accetto le condizioni d'uso dell'AI",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (recordConsentOnAccept) {
                        aiSettings.recordConsent()
                    }
                    onAccept()
                },
                enabled = acceptedTerms,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text("Accetta e continua")
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Annulla")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConsentInfoBlock(
    icon: ImageVector,
    color: Color,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = color.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 1.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

