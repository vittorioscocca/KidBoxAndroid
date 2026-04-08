package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun JoinFamilyScreen(
    onBack: () -> Unit,
    onJoined: () -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    var code by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Text("Entra con codice", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Codice invito", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase(Locale.ROOT) },
            placeholder = { Text("Es. K7P4D2") },
            keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(Icons.Filled.QrCodeScanner, null, tint = Color(0xFF2E86FF), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.size(10.dp))
                Text("Scansiona QR code", color = Color(0xFF2E86FF), fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFFEAEAEA)))
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.joinWithCode(code, onJoined) }) {
                    Text("Entra")
                }
            }
        }
        if (!state.error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.error.orEmpty(), color = Color(0xFFE53E3E), fontSize = 12.sp)
        }
    }
}
