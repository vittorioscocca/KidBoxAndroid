@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun iconFor(kind: DocumentKind): ImageVector = when (kind) {
    DocumentKind.TESSERA_SANITARIA -> Icons.Filled.MedicalServices
    DocumentKind.CARTA_IDENTITA -> Icons.Filled.Badge
    DocumentKind.CIE -> Icons.Filled.CreditCard
    DocumentKind.PASSAPORTO -> Icons.Filled.MenuBook
    DocumentKind.CODICE_FISCALE -> Icons.Filled.Pin
    DocumentKind.PATENTE -> Icons.Filled.DirectionsCar
    DocumentKind.ALTRO -> Icons.Filled.Description
}

/** Card documento, stessa lingua visiva di `WalletTicketCard` (gradiente per kind). */
@Composable
fun WalletDocumentCard(
    item: WalletDocumentItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val kind = item.metadata?.kind ?: DocumentKind.ALTRO
    val gradient = Brush.linearGradient(listOf(Color(kind.gradientStartHex), Color(kind.gradientEndHex)))
    val expiry = item.metadata?.effectiveExpiryDate

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(iconFor(kind), contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.padding(start = 10.dp))
                Column {
                    Text(
                        kind.displayName.uppercase(Locale.ITALIAN),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                    Text(
                        item.document.title.ifBlank { kind.displayName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    item.ownerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
                if (expiry != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (expiry.isBefore(LocalDate.now())) "SCADUTO" else "SCADE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            expiry.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.ITALIAN)),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        if (isSelectionMode) {
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
            )
        }
    }
}
