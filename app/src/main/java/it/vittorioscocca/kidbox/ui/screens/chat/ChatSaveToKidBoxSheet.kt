@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.domain.save.SaveAction
import it.vittorioscocca.kidbox.domain.save.SaveClassifier
import it.vittorioscocca.kidbox.domain.save.SaveModelAnswer
import it.vittorioscocca.kidbox.ui.share.ShareDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.OnDeviceSaveModel

/**
 * «Salva in KidBox…» dal menu di un messaggio di testo. Gemello di
 * `ChatSaveSheet.swift`: subito le destinazioni scelte dalle regole, poi, se il
 * telefono ha Gemini Nano, lo stesso elenco riordinato dal modello. Senza Nano
 * (o con Nano che non risponde) restano le regole, senza errori a schermo.
 */
@Composable
internal fun ChatSaveToKidBoxSheet(
    text: String,
    onDismiss: () -> Unit,
    onSelect: (SaveAction) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var result by remember(text) { mutableStateOf(SaveClassifier.classify(text)) }
    var thinking by remember(text) { mutableStateOf(true) }

    LaunchedEffect(text) {
        val rules = SaveClassifier.classify(text)
        val outcome = OnDeviceSaveModel.classify(text)
        if (outcome is OnDeviceSaveModel.Outcome.Text) {
            SaveModelAnswer.parseDestinations(outcome.text)?.let {
                result = SaveModelAnswer.reorder(rules, it)
            }
        }
        thinking = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = kb.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = kb.subtitle.copy(alpha = 0.08f),
            ) {
                Text(
                    text = text,
                    color = kb.subtitle,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.chat_save_in_title),
                    color = kb.subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (thinking) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else if (result.isModelClassified) {
                    Icon(Icons.Default.AutoAwesome, null, tint = kb.subtitle, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.chat_save_in_on_device), color = kb.subtitle, fontSize = 11.sp)
                }
            }
            result.actions.forEach { action ->
                val destination = when (action) {
                    is SaveAction.Todo -> ShareDestination.TODO
                    is SaveAction.Event -> ShareDestination.EVENT
                    is SaveAction.Grocery -> ShareDestination.SHOPPING
                    is SaveAction.Note -> ShareDestination.NOTE
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = kb.subtitle.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(action) },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(destination.icon, null, tint = kb.title, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(destination.labelRes),
                            color = kb.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
