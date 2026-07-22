package it.vittorioscocca.kidbox.ui.screens.passwords

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun PasswordsGroupsScreen(
    familyId: String,
    onBack: () -> Unit,
    onOpenCreateGroup: () -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    viewModel: PasswordsGroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors

    LaunchedEffect(familyId) {
        viewModel.bind(familyId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = kb.card, shadowElevation = 1.dp) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.location_back_content_description), tint = kb.title)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = kb.card, shadowElevation = 1.dp) {
                IconButton(onClick = onOpenCreateGroup) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.passwords_new_group_title), tint = kb.title)
                }
            }
        }

        Text(
            stringResource(R.string.passwords_manage_groups_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = kb.title,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (state.rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.passwords_no_groups_visible),
                    style = MaterialTheme.typography.bodyLarge,
                    color = kb.subtitle,
                )
            }
        } else {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = kb.card,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                        GroupRow(row = row, onOpenGroup = onOpenGroup)
                        if (index < state.rows.lastIndex) {
                            HorizontalDivider(
                                color = kb.divider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 68.dp, end = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    row: PasswordGroupRowUi,
    onOpenGroup: (String) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val tint = parseHexColor(row.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenGroup(row.id) }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = groupHeaderIconVector(row.iconRaw),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(10.dp)
                .background(tint, androidx.compose.foundation.shape.CircleShape),
        )
        Text(
            row.label,
            style = MaterialTheme.typography.titleMedium,
            color = kb.title,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${row.count}",
            style = MaterialTheme.typography.titleSmall,
            color = kb.subtitle,
        )
    }
}

private fun groupHeaderIconVector(raw: String): ImageVector {
    val s = raw.lowercase()
    return when {
        "briefcase" in s -> Icons.Outlined.WorkOutline
        "person" in s -> Icons.Outlined.PersonOutline
        "bubble" in s || "message" in s -> Icons.Outlined.ChatBubbleOutline
        "creditcard" in s || "card" in s -> Icons.Outlined.CreditCard
        "house" in s || "home" in s -> Icons.Outlined.Home
        "tray" in s || "inbox" in s -> Icons.Outlined.Inbox
        else -> Icons.Outlined.Folder
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
