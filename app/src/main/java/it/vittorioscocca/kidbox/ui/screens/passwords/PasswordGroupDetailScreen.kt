@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package it.vittorioscocca.kidbox.ui.screens.passwords

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val IosBg = Color(0xFFF2F2F7)

private data class GroupIconOption(val raw: String, val icon: ImageVector)

private val iconOptions = listOf(
    GroupIconOption("key", Icons.Filled.Key),
    GroupIconOption("lock", Icons.Filled.Lock),
    GroupIconOption("person", Icons.Filled.Person),
    GroupIconOption("briefcase", Icons.Filled.BusinessCenter),
    GroupIconOption("creditcard", Icons.Filled.CreditCard),
    GroupIconOption("cart", Icons.Filled.ShoppingCart),
    GroupIconOption("house", Icons.Filled.Home),
    GroupIconOption("game", Icons.Filled.SportsEsports),
    GroupIconOption("computer", Icons.Filled.Computer),
    GroupIconOption("chat", Icons.Filled.Chat),
    GroupIconOption("flight", Icons.Filled.Flight),
    GroupIconOption("car", Icons.Filled.LocalTaxi),
    GroupIconOption("heart", Icons.Filled.Favorite),
    GroupIconOption("medical", Icons.Filled.MedicalServices),
    GroupIconOption("map", Icons.Filled.Map),
    GroupIconOption("globe", Icons.Filled.Language),
    GroupIconOption("store", Icons.Filled.Store),
    GroupIconOption("camera", Icons.Filled.CameraAlt),
    GroupIconOption("music", Icons.Filled.MusicNote),
    GroupIconOption("card", Icons.Filled.CreditCard),
    GroupIconOption("article", Icons.Filled.Article),
    GroupIconOption("folder", Icons.Filled.Folder),
    GroupIconOption("inventory", Icons.Filled.Inventory2),
    GroupIconOption("gift", Icons.Filled.CardGiftcard),
    GroupIconOption("wifi", Icons.Filled.Wifi),
    GroupIconOption("cloud", Icons.Filled.Cloud),
    GroupIconOption("paperplane", Icons.Filled.Public),
    GroupIconOption("school", Icons.Filled.School),
    GroupIconOption("tools", Icons.Filled.Build),
    GroupIconOption("star", Icons.Filled.Star),
)

private val colorOptions = listOf(
    "#0A84FF", "#5E5CE6", "#BF5AF2", "#FF2D55", "#FF375F", "#FF9F0A",
    "#FFD60A", "#34C759", "#30D158", "#64D2FF", "#8E8E93", "#7C6FDE",
)

@Composable
fun PasswordGroupDetailScreen(
    familyId: String,
    groupId: String?,
    onBack: () -> Unit,
    viewModel: PasswordGroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    var showVisibilitySheet by remember { mutableStateOf(false) }

    LaunchedEffect(familyId, groupId) {
        viewModel.load(familyId, groupId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderPill(text = "Annulla", onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (state.isEditMode) "Modifica gruppo" else "Nuovo gruppo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = kb.title,
            )
            Spacer(modifier = Modifier.weight(1f))
            HeaderPill(
                text = "Salva",
                onClick = { viewModel.save(onDone = onBack) },
                enabled = state.title.trim().isNotEmpty() && !state.isSaving,
            )
        }

        SectionTitle("Dettagli gruppo")
        RoundedCard {
            BasicTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                textStyle = TextStyle(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Medium,
                    color = kb.title,
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (state.title.isBlank()) {
                            Text("Nome gruppo", style = MaterialTheme.typography.titleLarge, color = kb.subtitle)
                        }
                        inner()
                    }
                },
            )
            HorizontalDivider(color = Color(0xFFEDEDED), thickness = 0.5.dp)
            Text(
                "${state.title.length}/30",
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
                modifier = Modifier.padding(top = 10.dp, start = 2.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle("Icona")
        RoundedCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 6,
            ) {
                iconOptions.forEach { option ->
                    val selected = option.raw == state.selectedIcon
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected) Color(0xFFEAE6FF) else Color(0xFFF5F5F7),
                        modifier = Modifier
                            .size(54.dp)
                            .clickable { viewModel.setIcon(option.raw) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(
                                option.icon,
                                contentDescription = null,
                                tint = Color.Black,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle("Colore")
        RoundedCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 6,
            ) {
                colorOptions.forEach { colorHex ->
                    val selected = colorHex.equals(state.selectedColor, ignoreCase = true)
                    val color = parseHexColor(colorHex)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { viewModel.setColor(colorHex) },
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.35f)),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle("Visibilita")
        RoundedCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showVisibilitySheet = true }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val visLabel = when (state.visibility) {
                    KBVisibilityScope.ONLY_CREATOR -> "Solo creatore"
                    else -> "Tutta la famiglia"
                }
                Text(visLabel, style = MaterialTheme.typography.titleMedium, color = kb.title)
                Spacer(modifier = Modifier.weight(1f))
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = kb.subtitle,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showVisibilitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showVisibilitySheet = false },
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Visibilita gruppo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                VisibilityOptionRow(
                    label = "Tutta la famiglia",
                    selected = state.visibility == KBVisibilityScope.FAMILY,
                    onClick = {
                        viewModel.setVisibility(KBVisibilityScope.FAMILY)
                        showVisibilitySheet = false
                    },
                )
                VisibilityOptionRow(
                    label = "Solo creatore",
                    selected = state.visibility == KBVisibilityScope.ONLY_CREATOR,
                    onClick = {
                        viewModel.setVisibility(KBVisibilityScope.ONLY_CREATOR)
                        showVisibilitySheet = false
                    },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HeaderPill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.kidBoxColors.subtitle,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp),
    )
}

@Composable
private fun RoundedCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

private fun parseHexColor(hex: String): Color {
    val h = hex.trim().removePrefix("#")
    return runCatching {
        when (h.length) {
            6 -> {
                val rgb = h.toLong(16).toInt() and 0xFFFFFF
                Color(0xFF000000.toInt() or rgb)
            }
            8 -> Color(h.toLong(16).toInt())
            else -> Color(0xFF7C6FDE)
        }
    }.getOrElse { Color(0xFF7C6FDE) }
}

@Composable
private fun VisibilityOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
