package it.vittorioscocca.kidbox.ui.screens.health.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Barra superiore comune (Indietro | … | pill Filtra + Seleziona/Fine | +) per liste Salute:
 * visite, esami, cure, vaccini.
 */
@Composable
fun HealthListTopToolbar(
    tint: Color,
    filterActive: Boolean,
    isSelecting: Boolean,
    onBack: () -> Unit,
    onFilterClick: () -> Unit,
    onToggleSelectClick: () -> Unit,
    onAddClick: (() -> Unit)?,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KidBoxHeaderCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Indietro",
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = kb.card,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KidBoxHeaderCircleButton(
                    icon = Icons.Default.FilterList,
                    contentDescription = "Filtra",
                    onClick = onFilterClick,
                    iconTint = if (filterActive) tint else null,
                )
                TextButton(
                    onClick = onToggleSelectClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = kb.title),
                ) {
                    Text(
                        if (isSelecting) "Fine" else "Seleziona",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        if (!isSelecting && onAddClick != null) {
            KidBoxHeaderCircleButton(
                icon = Icons.Default.Add,
                contentDescription = "Aggiungi",
                onClick = onAddClick,
            )
        }
    }
}

/** Pulsante full-width in basso per «nuova voce» (stesso padding e tipografia ovunque). */
@Composable
fun HealthListAddBottomButton(
    tint: Color,
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = tint),
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** Barra selezione a due azioni: Tutte / Deseleziona + Elimina (visite, esami, vaccini). */
@Composable
fun HealthListDualSelectionBottomBar(
    tint: Color,
    allSelected: Boolean,
    hasSelection: Boolean,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val deleteColor = Color(0xFFD32F2F)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(kb.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onToggleAll, modifier = Modifier.weight(1f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.GridView,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (allSelected) "Deseleziona" else "Tutte",
                    fontSize = 11.sp,
                    color = tint,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(kb.subtitle.copy(alpha = 0.2f)),
        )
        TextButton(
            onClick = onDelete,
            enabled = hasSelection,
            modifier = Modifier.weight(1f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = if (hasSelection) deleteColor else kb.subtitle,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Elimina",
                    fontSize = 11.sp,
                    color = if (hasSelection) deleteColor else kb.subtitle,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
