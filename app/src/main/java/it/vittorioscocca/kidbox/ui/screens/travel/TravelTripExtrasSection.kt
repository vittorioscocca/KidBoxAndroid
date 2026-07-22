package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private val ExtrasAccent = Color(0xFFF2611A)

@Composable
fun TravelTripExtrasSection(
    photoCount: Int,
    noteTitle: String,
    noteHasContent: Boolean,
    todoListName: String,
    openTodoCount: Int,
    onPhotosClick: () -> Unit,
    onNotesClick: () -> Unit,
    onTodosClick: () -> Unit,
    onExpensesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        TravelTripExtraCard(
            icon = Icons.Default.PhotoLibrary,
            title = stringResource(R.string.travel_photos),
            subtitle = if (photoCount == 0) {
                stringResource(R.string.travel_photos_hint)
            } else {
                "$photoCount foto nell'album del viaggio"
            },
            onClick = onPhotosClick,
        )
        TravelTripExtraCard(
            icon = Icons.Default.EditNote,
            title = stringResource(R.string.travel_notes),
            subtitle = if (noteHasContent) noteTitle else stringResource(R.string.travel_notes_hint),
            onClick = onNotesClick,
        )
        TravelTripExtraCard(
            icon = Icons.Default.Checklist,
            title = stringResource(R.string.travel_todo),
            subtitle = if (openTodoCount == 0) {
                "Lista «$todoListName»"
            } else {
                "$openTodoCount attività aperte · $todoListName"
            },
            onClick = onTodosClick,
        )
        TravelTripExtraCard(
            icon = Icons.Default.AttachMoney,
            title = stringResource(R.string.travel_expenses),
            subtitle = stringResource(R.string.travel_expenses_hint),
            onClick = onExpensesClick,
        )
    }
}

@Composable
private fun TravelTripExtraCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ExtrasAccent.copy(alpha = 0.12f),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ExtrasAccent,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = kb.title)
                Text(subtitle, fontSize = 14.sp, color = kb.subtitle, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ExtrasAccent)
        }
    }
}
