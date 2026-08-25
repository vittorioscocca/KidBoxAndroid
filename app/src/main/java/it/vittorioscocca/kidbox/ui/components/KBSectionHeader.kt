package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Pulsante tondo dell'header, identico a quelli di Calendario e Lista della spesa.
 */
@Composable
fun KBHeaderCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.kidBoxColors.card,
        shadowElevation = 6.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Header di sezione condiviso: «indietro» tondo a sinistra, «+» tondo a destra e
 * titolo grande sotto. Stessa forma usata da Calendario e Lista della spesa; le
 * sezioni Garage, Animali e Casa ci si sono allineate.
 */
@Composable
fun KBSectionHeader(
    title: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    addContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KBHeaderCircleButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.grocery_back),
                    tint = kb.title,
                )
            }
            Spacer(Modifier.weight(1f))
            KBHeaderCircleButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = addContentDescription,
                    tint = kb.title,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
            ),
            color = kb.title,
        )
    }
}

/**
 * Tasto «indietro» standard dell'app: stesso tondo dell'header di sezione.
 * Usato dalle schermate che hanno solo il ritorno indietro (es. Impostazioni).
 */
@Composable
fun KBBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = kb.card,
        shadowElevation = 6.dp,
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = contentDescription ?: stringResource(R.string.settings_common_back),
                tint = kb.title,
            )
        }
    }
}
