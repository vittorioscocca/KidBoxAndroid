package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.delay

@Composable
fun TravelDestinationSearchField(
    familyId: String,
    destinationName: String,
    destinationRegion: String,
    onDestinationNameChange: (String) -> Unit,
    onDestinationRegionChange: (String) -> Unit,
    onSelectionChanged: () -> Unit,
    modifier: Modifier = Modifier,
    searchViewModel: TravelDestinationSearchViewModel = hiltViewModel(),
) {
    TravelDestinationSearchFieldContent(
        familyId = familyId,
        destinationName = destinationName,
        destinationRegion = destinationRegion,
        onDestinationNameChange = onDestinationNameChange,
        onDestinationRegionChange = onDestinationRegionChange,
        onSelectionChanged = onSelectionChanged,
        searchService = searchViewModel.searchService,
        modifier = modifier,
    )
}

@Composable
private fun TravelDestinationSearchFieldContent(
    familyId: String,
    destinationName: String,
    destinationRegion: String,
    onDestinationNameChange: (String) -> Unit,
    onDestinationRegionChange: (String) -> Unit,
    onSelectionChanged: () -> Unit,
    searchService: TravelPlaceSearchService,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    var query by remember(destinationName) { mutableStateOf(destinationName) }
    var suggestions by remember { mutableStateOf<List<TravelPlaceSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val trimmedQuery = query.trim()
    val showSuggestions = isFocused && trimmedQuery.isNotEmpty()

    LaunchedEffect(trimmedQuery, showSuggestions, familyId) {
        if (!showSuggestions || trimmedQuery.length < 2) {
            suggestions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(280)
        suggestions = searchService.search(trimmedQuery, familyId)
        isSearching = false
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                onDestinationNameChange(value)
                onDestinationRegionChange("")
                onSelectionChanged()
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            placeholder = { Text("Es. Procida, Barcellona…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    isSearching -> CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                    query.isNotEmpty() -> IconButton(onClick = {
                        query = ""
                        onDestinationNameChange("")
                        onDestinationRegionChange("")
                        suggestions = emptyList()
                        searchService.resetSession()
                        onSelectionChanged()
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { isFocused = false }),
            shape = RoundedCornerShape(14.dp),
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
            ) {
                Column {
                    suggestions.take(6).forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = item.title
                                    onDestinationNameChange(item.title)
                                    onDestinationRegionChange(item.subtitle)
                                    suggestions = emptyList()
                                    isFocused = false
                                    searchService.resetSession()
                                    onSelectionChanged()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = kb.subtitle)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, color = kb.title)
                                if (item.subtitle.isNotBlank()) {
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                                }
                            }
                        }
                        if (index < minOf(suggestions.size, 6) - 1) {
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                        }
                    }
                }
            }
        } else if (showSuggestions && isSearching) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Ricerca in corso…", color = kb.subtitle, modifier = Modifier.padding(start = 12.dp))
                }
            }
        } else if (destinationName.isNotBlank() && destinationRegion.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = kb.subtitle)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(destinationName, style = MaterialTheme.typography.titleSmall, color = kb.title)
                        Text(destinationRegion, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                    }
                }
            }
        }
    }
}
