package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.screens.onboarding.InviteCodeViewModel
import it.vittorioscocca.kidbox.ui.screens.onboarding.QRCodeView

@Composable
fun InviteCodeScreen(
    onBack: () -> Unit,
    viewModel: InviteCodeViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val qrPayload by viewModel.qrPayload.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.generateInviteCode()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Invita", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.size(16.dp))
        when {
            isBusy -> CircularProgressIndicator()
            qrPayload != null -> {
                QRCodeView(payload = qrPayload.orEmpty(), modifier = Modifier.size(220.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Codice: ${code.orEmpty()}")
            }
            errorMessage != null -> {
                Text(errorMessage.orEmpty(), color = Color(0xFFE53E3E))
                Spacer(modifier = Modifier.size(8.dp))
                Button(onClick = viewModel::generateInviteCode) { Text("Riprova") }
            }
        }
    }
}
