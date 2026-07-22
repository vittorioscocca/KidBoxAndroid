package it.vittorioscocca.kidbox.ui.screens.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val suggestionOrder = listOf(
    "notes", "todo", "shopping", "calendar", "health", "documents", "expenses",
    "wallet", "passwords", "location", "photos", "family", "pets", "home_items",
    "vehicles", "travel", "ai",
)

private val suggestionTexts = mapOf(
    "notes" to R.string.home_suggestions_notes_body,
    "todo" to R.string.home_suggestions_todo_body,
    "shopping" to R.string.home_suggestions_shopping_body,
    "calendar" to R.string.home_suggestions_calendar_body,
    "health" to R.string.home_suggestions_health_body,
    "documents" to R.string.home_suggestions_documents_body,
    "expenses" to R.string.home_suggestions_expenses_body,
    "wallet" to R.string.home_suggestions_wallet_body,
    "passwords" to R.string.home_suggestions_passwords_body,
    "location" to R.string.home_suggestions_location_body,
    "photos" to R.string.home_suggestions_photos_body,
    "family" to R.string.home_suggestions_family_body,
    "pets" to R.string.home_suggestions_pets_body,
    "home_items" to R.string.home_suggestions_home_items_body,
    "vehicles" to R.string.home_suggestions_vehicles_body,
    "travel" to R.string.home_suggestions_travel_body,
    "ai" to R.string.home_suggestions_ai_body,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val items = remember(context) {
        val allFeatures = featureItems(context = context, familyId = "", state = HomeUiState())
        val byId = allFeatures.associateBy { it.id }
        suggestionOrder.mapNotNull { id -> byId[id] }
    }

    var selected by remember { mutableStateOf<FeatureItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_suggestions_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.kidBoxColors.title,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.kidBoxColors.card,
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    SuggestionRow(item = item, onClick = { selected = item })
                    if (index != items.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.kidBoxColors.divider,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding())
    }

    selected?.let { item ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(item.iconColor.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = item.iconColor)
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.kidBoxColors.title,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = suggestionTexts[item.id]?.let { stringResource(it) }.orEmpty(),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.kidBoxColors.subtitle,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SuggestionRow(item: FeatureItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(item.iconColor.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = item.iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = item.title,
            fontSize = 16.sp,
            color = MaterialTheme.kidBoxColors.title,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
