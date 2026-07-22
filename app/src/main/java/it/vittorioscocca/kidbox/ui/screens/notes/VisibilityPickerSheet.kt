package it.vittorioscocca.kidbox.ui.screens.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

data class VisibilityPickerMember(
    val uid: String,
    val displayName: String,
)

/**
 * Usare da un [ModalBottomSheet] già aperto (es. note / to-do). Non annidare dentro un altro bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisibilityPickerBottomSheet(
    currentUid: String?,
    scopeSectionTitle: String,
    membersExcludingSelf: List<VisibilityPickerMember>,
    initialScope: String,
    initialMemberIds: List<String>,
    onDismiss: () -> Unit,
    onConfirmed: (scope: String, memberIds: List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        VisibilityPickerPanel(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            currentUid = currentUid,
            scopeSectionTitle = scopeSectionTitle,
            membersExcludingSelf = membersExcludingSelf,
            initialScope = initialScope,
            initialMemberIds = initialMemberIds,
            onDismiss = onDismiss,
            onConfirmed = onConfirmed,
        )
    }
}

/**
 * Dialog a schermo intero: per form già mostrati come bottom sheet (es. calendario), dove un secondo
 * [ModalBottomSheet] non viene presentato correttamente.
 */
@Composable
fun VisibilityPickerFullscreenDialog(
    currentUid: String?,
    scopeSectionTitle: String,
    membersExcludingSelf: List<VisibilityPickerMember>,
    initialScope: String,
    initialMemberIds: List<String>,
    onDismiss: () -> Unit,
    onConfirmed: (scope: String, memberIds: List<String>) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            val scroll = rememberScrollState()
            VisibilityPickerPanel(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 24.dp),
                currentUid = currentUid,
                scopeSectionTitle = scopeSectionTitle,
                membersExcludingSelf = membersExcludingSelf,
                initialScope = initialScope,
                initialMemberIds = initialMemberIds,
                onDismiss = onDismiss,
                onConfirmed = onConfirmed,
            )
        }
    }
}

@Composable
private fun VisibilityPickerPanel(
    modifier: Modifier = Modifier,
    currentUid: String?,
    scopeSectionTitle: String,
    membersExcludingSelf: List<VisibilityPickerMember>,
    initialScope: String,
    initialMemberIds: List<String>,
    onDismiss: () -> Unit,
    onConfirmed: (scope: String, memberIds: List<String>) -> Unit,
) {
    var selScope by remember(initialScope) {
        mutableStateOf(KBVisibilityScope.normalized(initialScope))
    }
    var selMembers by remember(initialMemberIds) {
        mutableStateOf(initialMemberIds.toSet())
    }
    val kb = MaterialTheme.kidBoxColors

    Column(modifier) {
        Text(scopeSectionTitle, style = MaterialTheme.typography.titleSmall, color = kb.title)
        Spacer(Modifier.height(8.dp))
        VisibilityOptionRow(label = stringResource(R.string.notes_visibility_option_family), selected = selScope == KBVisibilityScope.FAMILY) {
            selScope = KBVisibilityScope.FAMILY
            selMembers = emptySet()
        }
        VisibilityOptionRow(label = stringResource(R.string.notes_visibility_option_members), selected = selScope == KBVisibilityScope.MEMBERS) {
            selScope = KBVisibilityScope.MEMBERS
        }
        VisibilityOptionRow(label = stringResource(R.string.notes_visibility_option_only_me), selected = selScope == KBVisibilityScope.ONLY_CREATOR) {
            selScope = KBVisibilityScope.ONLY_CREATOR
            selMembers = emptySet()
        }
        if (selScope == KBVisibilityScope.MEMBERS) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.notes_visibility_select_members), style = MaterialTheme.typography.titleSmall, color = kb.title)
            Spacer(Modifier.height(6.dp))
            membersExcludingSelf.forEach { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selMembers = if (m.uid in selMembers) selMembers - m.uid else selMembers + m.uid
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.displayName, color = kb.title)
                    Icon(
                        imageVector = if (m.uid in selMembers) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (m.uid in selMembers) kb.title else kb.subtitle,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_visibility_cancel)) }
            TextButton(
                onClick = {
                    val me = currentUid?.takeIf { it.isNotBlank() }
                    var finalMembers = selMembers.toList()
                    if (selScope != KBVisibilityScope.MEMBERS) finalMembers = emptyList()
                    if (me != null) finalMembers = finalMembers.filter { it != me }
                    onConfirmed(selScope, finalMembers.sorted())
                    onDismiss()
                },
            ) { Text(stringResource(R.string.notes_visibility_confirm)) }
        }
    }
}

@Composable
private fun VisibilityOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = kb.title)
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) kb.title else kb.subtitle,
        )
    }
}
